package br.com.tonus.scheduling.internal;

import br.com.tonus.registration.RegistrationFacade;
import br.com.tonus.registration.StudentSummary;
import br.com.tonus.scheduling.ClassCanceled;
import br.com.tonus.scheduling.ClassRescheduled;
import br.com.tonus.scheduling.HolidayCreated;
import br.com.tonus.scheduling.internal.domain.DefaultSchedule;
import br.com.tonus.scheduling.internal.domain.DefaultScheduleRepository;
import br.com.tonus.scheduling.internal.domain.Holiday;
import br.com.tonus.scheduling.internal.domain.HolidayRepository;
import br.com.tonus.scheduling.internal.domain.ScheduleException;
import br.com.tonus.scheduling.internal.domain.ScheduleExceptionRepository;
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
    private final HolidayRepository holidayRepository;
    private final RegistrationFacade registrationFacade;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SchedulingService(DefaultScheduleRepository defaultScheduleRepository,
                             ScheduleExceptionRepository scheduleExceptionRepository,
                             HolidayRepository holidayRepository,
                             RegistrationFacade registrationFacade,
                             ApplicationEventPublisher events,
                             Clock clock) {
        this.defaultScheduleRepository = defaultScheduleRepository;
        this.scheduleExceptionRepository = scheduleExceptionRepository;
        this.holidayRepository = holidayRepository;
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

    @Transactional
    public void deleteDefaultSchedule(Long id) {
        defaultScheduleRepository.delete(requireSchedule(id));
    }

    @Transactional
    public ScheduleException cancelClass(Long studentId, LocalDate date) {
        requireNewException(studentId, date);
        ScheduleException saved = scheduleExceptionRepository.save(ScheduleException.cancellation(studentId, date));
        events.publishEvent(new ClassCanceled(studentId, date));
        return saved;
    }

    @Transactional
    public ScheduleException rescheduleClass(Long studentId, LocalDate date, LocalDate newDate, LocalTime newTime) {
        if (newDate == null || newTime == null) {
            throw new InvalidExceptionTargetException("Remarcação exige nova data e nova hora");
        }
        requireUsableRescheduleDate(newDate);
        requireNewException(studentId, date);
        ScheduleException saved = scheduleExceptionRepository.save(
                ScheduleException.reschedule(studentId, date, newDate, newTime));
        events.publishEvent(new ClassRescheduled(studentId, date, newDate, newTime));
        return saved;
    }

    @Transactional(readOnly = true)
    public ScheduleException findException(Long id) {
        return requireException(id);
    }

    /**
     * Filtros opcionais e combináveis. Specification em vez de uma consulta com
     * {@code :param IS NULL} para cada filtro: aquilo obriga o Postgres a
     * inferir tipo de parâmetro nulo, e a consulta quebra na borda em vez de na
     * compilação.
     */
    @Transactional(readOnly = true)
    public Page<ScheduleException> findExceptions(Long studentId, LocalDate from, LocalDate to, Pageable pageable) {
        Specification<ScheduleException> filter = Specification.allOf(
                studentId == null ? null : (root, query, cb) -> cb.equal(root.get("studentId"), studentId),
                from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), from),
                to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), to));
        return scheduleExceptionRepository.findAll(filter, withStableSort(pageable));
    }

    /**
     * Apagar a exceção devolve o dia ao template — é como se desfaz um
     * cancelamento marcado por engano. Sem isso, o único caminho de correção
     * seria mexer no banco à mão.
     */
    @Transactional
    public void deleteException(Long id) {
        scheduleExceptionRepository.delete(requireException(id));
    }

    @Transactional
    public Holiday createHoliday(LocalDate date, String description) {
        if (holidayRepository.existsByDate(date)) {
            throw new HolidayAlreadyExistsException(date);
        }
        Holiday saved = holidayRepository.save(new Holiday(date, description));
        events.publishEvent(new HolidayCreated(date));
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Holiday> findHolidays(LocalDate from, LocalDate to, Pageable pageable) {
        Specification<Holiday> filter = Specification.allOf(
                from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), from),
                to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), to));
        return holidayRepository.findAll(filter, withStableSort(pageable));
    }

    @Transactional
    public void deleteHoliday(Long id) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new SchedulingNotFoundException("Feriado não encontrado: " + id));
        holidayRepository.delete(holiday);
    }

    /**
     * A agenda de um dia, expandida:
     *
     * <pre>
     * agenda(D) = template (se D é dia útil)
     *           − feriado em D
     *           − exceções em D
     *           + remarcações cuja nova_data = D
     * </pre>
     *
     * Nada disso é materializado: guardar aula por dia obrigaria a gerar linhas
     * para o futuro inteiro e reconciliá-las a cada troca de horário.
     */
    @Transactional(readOnly = true)
    public List<ScheduleEntry> scheduleOf(LocalDate date) {
        if (holidayRepository.existsByDate(date)) {
            return List.of();
        }

        Map<Long, StudentSummary> students = activeStudentsById();
        List<ScheduleEntry> entries = new ArrayList<>();

        if (isWeekday(date)) {
            Set<Long> excepted = scheduleExceptionRepository.findByDate(date).stream()
                    .map(ScheduleException::getStudentId)
                    .collect(Collectors.toSet());
            for (DefaultSchedule schedule : defaultScheduleRepository.findAll()) {
                if (excepted.contains(schedule.getStudentId())) {
                    continue;
                }
                entryOf(students, schedule.getStudentId(), schedule.getTime(), ScheduleEntry.Origin.TEMPLATE)
                        .ifPresent(entries::add);
            }
        }

        for (ScheduleException reschedule : scheduleExceptionRepository.findByNewDate(date)) {
            entryOf(students, reschedule.getStudentId(), reschedule.getNewTime(), ScheduleEntry.Origin.RESCHEDULED)
                    .ifPresent(entries::add);
        }

        entries.sort(Comparator.comparing(ScheduleEntry::time).thenComparing(ScheduleEntry::studentName));
        return entries;
    }

    @Transactional(readOnly = true)
    public List<ScheduleEntry> scheduleOfStudent(Long studentId, LocalDate date) {
        return scheduleOf(date).stream()
                .filter(entry -> entry.studentId().equals(studentId))
                .toList();
    }

    /**
     * A grade do professor. O grupo emerge de professor + hora: não existe
     * turma como entidade, então não há o que manter em sincronia.
     */
    @Transactional(readOnly = true)
    public List<ScheduleEntry> scheduleOfTeacher(Long teacherId, LocalDate date) {
        return scheduleOf(date).stream()
                .filter(entry -> entry.teacherId().equals(teacherId))
                .toList();
    }

    /**
     * Indexar os alunos ativos resolve duas coisas de uma vez: dá nome e
     * professor de cada aula, e exclui da agenda quem saiu do estúdio — aluno
     * inativo não está no mapa, e o horário padrão dele fica sem efeito.
     */
    private Map<Long, StudentSummary> activeStudentsById() {
        return registrationFacade.findActiveStudents().stream()
                .collect(Collectors.toMap(StudentSummary::studentId, Function.identity()));
    }

    private Optional<ScheduleEntry> entryOf(Map<Long, StudentSummary> students,
                                                      Long studentId,
                                                      LocalTime time,
                                                      ScheduleEntry.Origin origin) {
        StudentSummary student = students.get(studentId);
        if (student == null) {
            return Optional.empty();
        }
        return Optional.of(
                new ScheduleEntry(student.studentId(), student.name(), student.teacherId(), time, origin));
    }

    private boolean isWeekday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /**
     * A aula remarcada precisa poder acontecer.
     *
     * Data passada é praticamente sempre erro de digitação, e a remarcação
     * dispara aviso ao aluno — avisar sobre aula que já passou é pior que
     * recusar o registro.
     *
     * Feriado já cadastrado é conferido aqui, mas a invariante é assumidamente
     * parcial: nada impede cadastrar o feriado *depois* da remarcação, e nesse
     * caso a aula simplesmente some da agenda do dia (a expansão suprime o dia
     * inteiro). A checagem existe para o ADMIN descobrir na hora o que
     * descobriria de qualquer jeito, não para garantir consistência.
     */
    private void requireUsableRescheduleDate(LocalDate newDate) {
        LocalDate today = LocalDate.now(clock);
        if (newDate.isBefore(today)) {
            throw new InvalidRescheduleDateException("Não é possível remarcar para data passada: " + newDate);
        }
        if (holidayRepository.existsByDate(newDate)) {
            throw new InvalidRescheduleDateException("Não é possível remarcar para um feriado: " + newDate);
        }
    }

    private void requireNewException(Long studentId, LocalDate date) {
        requireExistingStudent(studentId);
        if (scheduleExceptionRepository.existsByStudentIdAndDate(studentId, date)) {
            throw new ExceptionAlreadyExistsException(studentId, date);
        }
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
     * Mesma razão do registration: paginação sem ORDER BY total é
     * não-determinística no Postgres, e a mesma consulta pode repetir ou omitir
     * linhas entre páginas.
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
