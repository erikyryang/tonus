package br.com.tonus.scheduling.internal;

import br.com.tonus.registration.RegistrationFacade;
import br.com.tonus.registration.StudentSummary;
import br.com.tonus.scheduling.ClassCanceled;
import br.com.tonus.scheduling.ClassRescheduled;
import br.com.tonus.scheduling.AnnouncementCreated;
import br.com.tonus.scheduling.internal.domain.DefaultSchedule;
import br.com.tonus.scheduling.internal.domain.DefaultScheduleRepository;
import br.com.tonus.scheduling.internal.domain.ExceptionType;
import br.com.tonus.scheduling.internal.domain.Announcement;
import br.com.tonus.scheduling.internal.domain.AnnouncementRepository;
import br.com.tonus.scheduling.internal.domain.ScheduleException;
import br.com.tonus.scheduling.internal.domain.ScheduleExceptionRepository;
import br.com.tonus.scheduling.BroadcastRequested;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulingService {

    private final DefaultScheduleRepository defaultScheduleRepository;
    private final ScheduleExceptionRepository scheduleExceptionRepository;
    private final AnnouncementRepository announcementRepository;
    private final RegistrationFacade registrationFacade;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SchedulingService(DefaultScheduleRepository defaultScheduleRepository,
                             ScheduleExceptionRepository scheduleExceptionRepository,
                             AnnouncementRepository announcementRepository,
                             RegistrationFacade registrationFacade,
                             ApplicationEventPublisher events,
                             Clock clock) {
        this.defaultScheduleRepository = defaultScheduleRepository;
        this.scheduleExceptionRepository = scheduleExceptionRepository;
        this.announcementRepository = announcementRepository;
        this.registrationFacade = registrationFacade;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public DefaultSchedule createDefaultSchedule(Long studentId, LocalTime time) {
        requireExistingStudent(studentId);
        if (defaultScheduleRepository.existsByStudentId(studentId)) {
            throw new ScheduleAlreadyExistsException(studentId);
        }
        return defaultScheduleRepository.save(new DefaultSchedule(studentId, time));
    }

    @Transactional
    public DefaultSchedule updateDefaultSchedule(Long id, Long studentId, LocalTime time) {
        DefaultSchedule schedule = requireSchedule(id);
        if (!studentId.equals(schedule.getStudentId())) {
            requireExistingStudent(studentId);
            if (defaultScheduleRepository.existsByStudentId(studentId)) {
                throw new ScheduleAlreadyExistsException(studentId);
            }
            // The schedule is leaving the old student, and so do their
            // exceptions — the same cleanup deleteDefaultSchedule performs.
            // Without it, schedule_exception (indexed by student_id + date,
            // not by the schedule) would keep an old CANCELED that silently
            // suppresses the class of a future schedule for that student.
            scheduleExceptionRepository.deleteByStudentId(schedule.getStudentId());
        }
        schedule.update(studentId, time);
        return defaultScheduleRepository.save(schedule);
    }

    @Transactional(readOnly = true)
    public DefaultSchedule findDefaultSchedule(Long id) {
        return requireSchedule(id);
    }

    @Transactional(readOnly = true)
    public Page<DefaultSchedule> findDefaultSchedules(Pageable pageable) {
        return defaultScheduleRepository.findAll(withStableSort(pageable));
    }

    /**
     * The "Schedule" column of /students (SPEC-009): student → time map, for the
     * frontend to cross-reference with the students table it already has at hand. The graph does not
     * change — whoever crosses the two is the client, not registration.
     */
    @Transactional(readOnly = true)
    public Map<Long, LocalTime> schedulesByStudent() {
        return defaultScheduleRepository.findAll().stream()
                .collect(Collectors.toMap(DefaultSchedule::getStudentId, DefaultSchedule::getTime));
    }

    /**
     * Deletes the schedule and, along with it, all of the student's exceptions.
     *
     * Without the cleanup, an exception survives the schedule that originated it: since
     * schedule_exception is indexed by (student_id, date) and not by the schedule's
     * id, an old CANCELED would still apply and would silently suppress
     * the class of a new schedule created later for the same student on the same day.
     *
     * The cleanup is of everything — cancellation and reschedule — and not selective:
     * deleting the schedule returns the student to "no schedule", instead of deciding case by
     * case what survives. It is the simplest reading of "deleted", and the only one
     * that closes the bug without leaving another combination behind.
     */
    @Transactional
    public void deleteDefaultSchedule(Long id) {
        DefaultSchedule schedule = requireSchedule(id);
        scheduleExceptionRepository.deleteByStudentId(schedule.getStudentId());
        defaultScheduleRepository.delete(schedule);
    }

    @Transactional
    public ScheduleException cancelClass(Long studentId, LocalDate date) {
        requireNewException(studentId, date);
        boolean classExisted = studentHasClassOn(studentId, date);
        ScheduleException saved = scheduleExceptionRepository.save(ScheduleException.cancellation(studentId, date));
        // No class that day (no template, no reschedule landing there) means
        // there is nobody to tell: a "your class was canceled" message for a
        // class that never existed would be noise. The exception row still
        // stands — the panel shows what the admin recorded and can undo it.
        if (classExisted) {
            events.publishEvent(new ClassCanceled(studentId, date));
        }
        return saved;
    }

    @Transactional
    public ScheduleException rescheduleClass(Long studentId, LocalDate date, LocalDate newDate, LocalTime newTime) {
        if (newDate == null || newTime == null) {
            throw new InvalidExceptionTargetException("Remarcação exige nova data e nova hora");
        }
        requireUsableRescheduleDate(newDate);
        requireNewException(studentId, date);
        boolean classExisted = studentHasClassOn(studentId, date);
        ScheduleException saved = scheduleExceptionRepository.save(
                ScheduleException.reschedule(studentId, date, newDate, newTime));
        // Same rule as the cancellation: no class on the origin day, no
        // "your class was moved" message — there is no class to move.
        if (classExisted) {
            events.publishEvent(new ClassRescheduled(studentId, date, newDate, newTime));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public ScheduleException findException(Long id) {
        return requireException(id);
    }

    /**
     * Optional and combinable filters. Specification instead of a query with
     * {@code :param IS NULL} for each filter: that forces Postgres to
     * infer the type of a null parameter, and the query breaks at the edge instead of at
     * compile time.
     */
    @Transactional(readOnly = true)
    public Page<ScheduleException> findExceptions(Long studentId, LocalDate from, LocalDate to, Pageable pageable) {
        Specification<ScheduleException> filter = Specification.allOf(
                studentId == null ? Specification.unrestricted() : (root, query, cb) -> cb.equal(root.get("studentId"), studentId),
                from == null ? Specification.unrestricted() : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), from),
                to == null ? Specification.unrestricted() : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), to));
        return scheduleExceptionRepository.findAll(filter, withStableSort(pageable));
    }

    /**
     * Deleting the exception returns the day to the template — it is how a
     * mistakenly marked cancellation gets undone. Without this, the only path to correction
     * would be to touch the database by hand.
     */
    @Transactional
    public void deleteException(Long id) {
        scheduleExceptionRepository.delete(requireException(id));
    }

    @Transactional
    public Announcement createAnnouncement(LocalDate date, String description, boolean suspendsClasses) {
        if (announcementRepository.existsByDate(date)) {
            throw new AnnouncementAlreadyExistsException(date);
        }
        Announcement saved = announcementRepository.save(new Announcement(date, description, suspendsClasses));
        events.publishEvent(new AnnouncementCreated(saved.getId(), date, description, suspendsClasses));
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Announcement> findAnnouncements(LocalDate from, LocalDate to, Pageable pageable) {
        Specification<Announcement> filter = Specification.allOf(
                from == null ? Specification.unrestricted() : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), from),
                to == null ? Specification.unrestricted() : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), to));
        return announcementRepository.findAll(filter, withStableSort(pageable));
    }

    /**
     * The dispatch has no entity nor table of its own: there is no domain fact
     * behind it, it is just a message that needs to go out. The broadcastId (UUID)
     * is born here and becomes reference_key in notification — never the date, which
     * would swallow a second legitimate dispatch on the same day as if it were a
     * redelivery of the first.
     */
    @Transactional
    public String requestBroadcast(String message) {
        String broadcastId = UUID.randomUUID().toString();
        events.publishEvent(new BroadcastRequested(broadcastId, message));
        return broadcastId;
    }

    @Transactional
    public void deleteAnnouncement(Long id) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new SchedulingNotFoundException("Comunicado não encontrado: " + id));
        announcementRepository.delete(announcement);
    }

    /**
     * The schedule of a day, expanded:
     *
     * <pre>
     * schedule(D) = template (if D is a weekday)
     *           − holiday on D
     *           − exceptions on D
     *           + reschedules whose new_date = D
     * </pre>
     *
     * None of this is materialized: storing a class per day would force generating rows
     * for the entire future and reconciling them on every schedule change.
     */
    @Transactional(readOnly = true)
    public List<ScheduleEntry> scheduleOf(LocalDate date) {
        if (announcementRepository.existsByDateAndSuspendsClassesTrue(date)) {
            return List.of();
        }
        return expand(date, activeStudentsById(), defaultScheduleRepository.findAll(),
                scheduleExceptionRepository.findByDate(date),
                scheduleExceptionRepository.findByNewDate(date));
    }

    /**
     * The whole day in a single pass: holiday, schedule and cancellations.
     *
     * Exists separately from {@link #scheduleOf} because the ADMIN panel needs
     * all three things at once, and composing three public calls would repeat the
     * holiday query, the active students query and the default schedules query on every
     * screen opening — the most frequent one in the system.
     */
    @Transactional(readOnly = true)
    public DayScheduleView dayOf(LocalDate date) {
        DayScheduleView.Announcement announcement = announcementRepository.findByDate(date)
                .map(SchedulingService::viewOf)
                .orElse(null);
        if (suspends(announcement)) {
            return new DayScheduleView(date, announcement, List.of(), List.of());
        }

        Map<Long, StudentSummary> students = activeStudentsById();
        List<DefaultSchedule> schedules = defaultScheduleRepository.findAll();
        List<ScheduleException> exceptionsOfDay = scheduleExceptionRepository.findByDate(date);

        return new DayScheduleView(date, announcement,
                expand(date, students, schedules, exceptionsOfDay,
                        scheduleExceptionRepository.findByNewDate(date)),
                cancellations(date, students, schedules, exceptionsOfDay));
    }

    /**
     * The expansion itself, already without the holiday check — the caller
     * resolved that before. One implementation only: two would make the same rule
     * diverge between the ADMIN's schedule and the student's and teacher's.
     *
     * Queries nothing: receives everything ready. While it used to fetch the
     * day's reschedules itself, expanding a window cost one query per date —
     * whoever has the whole interval in hand passes the slice of each day and pays only
     * once. That is what keeps the rule single without imposing the cost of the single day on
     * whoever asks about several.
     */
    private List<ScheduleEntry> expand(LocalDate date,
                                       Map<Long, StudentSummary> students,
                                       List<DefaultSchedule> schedules,
                                       List<ScheduleException> exceptionsOfDay,
                                       List<ScheduleException> reschedulesIntoDay) {
        List<ScheduleEntry> entries = new ArrayList<>();

        // Any exception takes the student out of the template; a CANCELED one
        // also removes a reschedule landing on the day. Both sets are built
        // unconditionally, not only on weekdays: a reschedule can create a
        // weekend class, and canceling that class must be just as effective —
        // otherwise the CANCELED exception changes nothing while the student
        // still gets a "your class was canceled" message.
        Set<Long> excepted = exceptionsOfDay.stream()
                .map(ScheduleException::getStudentId)
                .collect(Collectors.toSet());
        Set<Long> canceled = exceptionsOfDay.stream()
                .filter(exception -> exception.getType() == ExceptionType.CANCELED)
                .map(ScheduleException::getStudentId)
                .collect(Collectors.toSet());

        if (isWeekday(date)) {
            for (DefaultSchedule schedule : schedules) {
                if (excepted.contains(schedule.getStudentId())) {
                    continue;
                }
                entryOf(students, schedule.getStudentId(), schedule.getTime(),
                        ScheduleEntry.Origin.TEMPLATE, null, null)
                        .ifPresent(entries::add);
            }
        }

        for (ScheduleException reschedule : reschedulesIntoDay) {
            if (canceled.contains(reschedule.getStudentId())) {
                continue;
            }
            entryOf(students, reschedule.getStudentId(), reschedule.getNewTime(),
                    ScheduleEntry.Origin.RESCHEDULED, reschedule.getDate(), reschedule.getId())
                    .ifPresent(entries::add);
        }

        entries.sort(Comparator.comparing(ScheduleEntry::time).thenComparing(ScheduleEntry::studentName));
        return entries;
    }

    /**
     * The day's cancellations. They go alongside the schedule, not inside it: the
     * canceled class does not happen, so including it among the others would make the reception
     * call a student who is not coming. Outside the schedule and visible, the ADMIN sees what
     * they canceled and can undo it.
     *
     * A cancellation on a weekend is just as real as on a weekday: reschedules
     * can create classes there, and a CANCELED exception has to be both
     * effective in the expansion and visible here to be undone. The time can
     * be null — there is no template on a weekend, and the reschedule's time
     * is recorded on the reschedule, not on the cancellation.
     */
    private List<CanceledEntry> cancellations(LocalDate date,
                                              Map<Long, StudentSummary> students,
                                              List<DefaultSchedule> schedules,
                                              List<ScheduleException> exceptionsOfDay) {
        Map<Long, LocalTime> defaultTimes = schedules.stream()
                .collect(Collectors.toMap(DefaultSchedule::getStudentId, DefaultSchedule::getTime));

        List<CanceledEntry> canceled = new ArrayList<>();
        for (ScheduleException exception : exceptionsOfDay) {
            if (exception.getType() != ExceptionType.CANCELED) {
                continue;
            }
            StudentSummary student = students.get(exception.getStudentId());
            if (student == null) {
                continue;
            }
            canceled.add(new CanceledEntry(exception.getId(), student.studentId(), student.name(),
                    student.teacherId(), student.teacherName(), defaultTimes.get(student.studentId())));
        }

        canceled.sort(Comparator.comparing(CanceledEntry::studentName));
        return canceled;
    }

    /**
     * The student's day: their slice of the studio's day, with the holiday and their
     * own cancellations.
     *
     * Carries the three things for the same reason as the ADMIN panel, and not for
     * symmetry: without the holiday description the student would see an empty day without knowing
     * why, and without the cancellation "no class today" would look identical to "your
     * class today was canceled". Filtering {@link #dayOf} instead of expanding
     * again keeps a single implementation of the rule.
     */
    @Transactional(readOnly = true)
    public DayScheduleView dayOfStudent(Long studentId, LocalDate date) {
        DayScheduleView day = dayOf(date);
        return new DayScheduleView(day.date(), day.announcement(),
                day.entries().stream().filter(entry -> entry.studentId().equals(studentId)).toList(),
                day.cancellations().stream().filter(entry -> entry.studentId().equals(studentId)).toList());
    }

    /**
     * The student's window: one day per date, with the same content as the single day.
     *
     * The cost is per interval, not per day. Each query covers the whole
     * window — the student, their default schedule, the holidays of the period and the
     * exceptions on both ends — and the expansion of each date works over
     * in-memory slices. The obvious alternative, calling {@link #dayOfStudent} in a
     * loop, would redo all of this per date and would still load the whole studio
     * each time just to discard everyone but one.
     *
     * The inactive student receives an empty window, not an error: they can still
     * log in while the login is not revoked (revocation is asynchronous), and a
     * 500 instead of "no class" would be the worst way to convey that.
     */
    @Transactional(readOnly = true)
    public List<DayScheduleView> weekOfStudent(Long studentId, LocalDate from, int days) {
        LocalDate to = from.plusDays(days - 1L);

        Map<Long, StudentSummary> student = registrationFacade.findStudentSummary(studentId)
                .filter(StudentSummary::active)
                .map(summary -> Map.of(summary.studentId(), summary))
                .orElseGet(Map::of);

        List<DefaultSchedule> schedules = defaultScheduleRepository.findByStudentId(studentId)
                .map(List::of)
                .orElseGet(List::of);

        Map<LocalDate, DayScheduleView.Announcement> announcements =
                announcementRepository.findByDateBetween(from, to).stream()
                        .collect(Collectors.toMap(Announcement::getDate, SchedulingService::viewOf));

        Map<LocalDate, List<ScheduleException>> exceptionsByDate =
                scheduleExceptionRepository.findByStudentIdAndDateBetween(studentId, from, to).stream()
                        .collect(Collectors.groupingBy(ScheduleException::getDate));
        Map<LocalDate, List<ScheduleException>> reschedulesByNewDate =
                scheduleExceptionRepository.findByStudentIdAndNewDateBetween(studentId, from, to).stream()
                        .collect(Collectors.groupingBy(ScheduleException::getNewDate));

        List<DayScheduleView> week = new ArrayList<>(days);
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = from.plusDays(offset);
            DayScheduleView.Announcement announcement = announcements.get(date);
            if (suspends(announcement)) {
                week.add(new DayScheduleView(date, announcement, List.of(), List.of()));
                continue;
            }
            List<ScheduleException> exceptionsOfDay = exceptionsByDate.getOrDefault(date, List.of());
            week.add(new DayScheduleView(date, announcement,
                    expand(date, student, schedules, exceptionsOfDay,
                            reschedulesByNewDate.getOrDefault(date, List.of())),
                    cancellations(date, student, schedules, exceptionsOfDay)));
        }
        return week;
    }

    /**
     * The teacher's grid. The group emerges from teacher + time: there is no
     * class as an entity, so there is nothing to keep in sync.
     *
     * The phone comes along because the teacher attends that student — canceling,
     * warning of a delay — and it is fetched in batch, only for the students that remained in the
     * grid after the filter: one query, and no phone of a student of another
     * teacher leaves registration.
     */
    @Transactional(readOnly = true)
    public List<TeacherClassEntry> gridOfTeacher(Long teacherId, LocalDate date) {
        List<ScheduleEntry> entries = scheduleOf(date).stream()
                .filter(entry -> entry.teacherId().equals(teacherId))
                .toList();

        Map<Long, String> phones = registrationFacade.findStudentPhones(
                entries.stream().map(ScheduleEntry::studentId).collect(Collectors.toSet()));

        return entries.stream()
                .map(entry -> new TeacherClassEntry(entry.studentId(), entry.studentName(),
                        phones.get(entry.studentId()), entry.time()))
                .toList();
    }

    /**
     * Indexing the active students solves two things at once: it gives name and
     * teacher of each class, and excludes from the schedule whoever left the studio — an
     * inactive student is not in the map, and their default schedule has no effect.
     */
    private Map<Long, StudentSummary> activeStudentsById() {
        return registrationFacade.findActiveStudents().stream()
                .collect(Collectors.toMap(StudentSummary::studentId, Function.identity()));
    }

    private Optional<ScheduleEntry> entryOf(Map<Long, StudentSummary> students,
                                            Long studentId,
                                            LocalTime time,
                                            ScheduleEntry.Origin origin,
                                            LocalDate originDate,
                                            Long exceptionId) {
        StudentSummary student = students.get(studentId);
        if (student == null) {
            return Optional.empty();
        }
        return Optional.of(new ScheduleEntry(student.studentId(), student.name(), student.teacherId(),
                student.teacherName(), time, origin, originDate, exceptionId));
    }

    /**
     * An announcement that does not suspend erases nothing: the day continues with its usual
     * schedule, and the notice travels alongside it. A day with no announcement at all also
     * does not suspend — hence the null coming in here instead of turning into a scattered check.
     */
    private static boolean suspends(DayScheduleView.Announcement announcement) {
        return announcement != null && announcement.suspendsClasses();
    }

    private static DayScheduleView.Announcement viewOf(Announcement announcement) {
        return new DayScheduleView.Announcement(announcement.getDescription(), announcement.suspendsClasses());
    }

    private boolean isWeekday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /**
     * The rescheduled class needs to be able to happen.
     *
     * A past date is practically always a typo, and the reschedule
     * triggers a notice to the student — notifying about a class that has already passed is worse than
     * refusing the registration.
     *
     * An already registered holiday is checked here, but the invariant is admittedly
     * partial: nothing prevents registering the holiday *after* the reschedule, and in that
     * case the class simply disappears from the day's schedule (the expansion suppresses the whole
     * day). The check exists so the ADMIN finds out right away what they
     * would find out anyway, not to guarantee consistency.
     */
    private void requireUsableRescheduleDate(LocalDate newDate) {
        LocalDate today = LocalDate.now(clock);
        if (newDate.isBefore(today)) {
            throw new InvalidRescheduleDateException("Não é possível remarcar para data passada: " + newDate);
        }
        if (announcementRepository.existsByDateAndSuspendsClassesTrue(newDate)) {
            throw new InvalidRescheduleDateException(
                    "Não é possível remarcar para um dia com as aulas suspensas: " + newDate);
        }
    }

    private void requireNewException(Long studentId, LocalDate date) {
        requireExistingStudent(studentId);
        if (scheduleExceptionRepository.existsByStudentIdAndDate(studentId, date)) {
            throw new ExceptionAlreadyExistsException(studentId, date);
        }
    }

    /**
     * Whether the student would have a class on {@code date}: the day's
     * template (weekday + default schedule) or a reschedule landing on it.
     * Decides whether canceling/rescheduling that day is notifying someone —
     * a phantom operation has nobody to tell.
     */
    private boolean studentHasClassOn(Long studentId, LocalDate date) {
        if (isWeekday(date) && defaultScheduleRepository.findByStudentId(studentId).isPresent()) {
            return true;
        }
        return scheduleExceptionRepository.existsByNewDateAndStudentId(date, studentId);
    }

    private void requireExistingStudent(Long studentId) {
        if (!registrationFacade.studentExists(studentId)) {
            throw new UnknownStudentException(studentId);
        }
    }

    private DefaultSchedule requireSchedule(Long id) {
        return defaultScheduleRepository.findById(id)
                .orElseThrow(() -> new SchedulingNotFoundException("Horário não encontrado: " + id));
    }

    private ScheduleException requireException(Long id) {
        return scheduleExceptionRepository.findById(id)
                .orElseThrow(() -> new SchedulingNotFoundException("Exceção não encontrada: " + id));
    }

    /**
     * Same reason as registration: pagination without a total ORDER BY is
     * non-deterministic in Postgres, and the same query can repeat or omit
     * rows between pages.
     */
    private Pageable withStableSort(Pageable pageable) {
        if (pageable.getSort().getOrderFor("id") != null) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().and(Sort.by("id")));
    }
}
