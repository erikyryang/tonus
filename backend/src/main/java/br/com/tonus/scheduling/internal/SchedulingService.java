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
     * A coluna "Horário" de /students (SPEC-009): mapa aluno → hora, para o
     * front cruzar com a tabela de alunos que já tem em mãos. O grafo não
     * muda — quem cruza os dois é o cliente, não registration.
     */
    @Transactional(readOnly = true)
    public Map<Long, LocalTime> schedulesByStudent() {
        return defaultScheduleRepository.findAll().stream()
                .collect(Collectors.toMap(DefaultSchedule::getStudentId, DefaultSchedule::getTime));
    }

    /**
     * Apaga o horário e, junto, todas as exceções do aluno.
     *
     * Sem a limpeza, uma exceção sobrevive ao horário que a originou: como
     * schedule_exception é indexada por (student_id, date) e não pelo id do
     * horário, um CANCELED antigo continuaria valendo e suprimiria em silêncio
     * a aula de um horário novo criado depois para o mesmo aluno no mesmo dia.
     *
     * A limpeza é de tudo — cancelamento e remarcação — e não seletiva:
     * apagar o horário devolve o aluno a "sem agenda", em vez de decidir caso a
     * caso o que sobrevive. É a leitura mais simples de "apagado", e a única
     * que fecha o bug sem deixar outra combinação por trás.
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
    public Announcement createAnnouncement(LocalDate date, String description, boolean suspendsClasses) {
        if (announcementRepository.existsByDate(date)) {
            throw new AnnouncementAlreadyExistsException(date);
        }
        Announcement saved = announcementRepository.save(new Announcement(date, description, suspendsClasses));
        events.publishEvent(new AnnouncementCreated(date, description, suspendsClasses));
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Announcement> findAnnouncements(LocalDate from, LocalDate to, Pageable pageable) {
        Specification<Announcement> filter = Specification.allOf(
                from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), from),
                to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), to));
        return announcementRepository.findAll(filter, withStableSort(pageable));
    }

    @Transactional
    public void deleteAnnouncement(Long id) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new SchedulingNotFoundException("Comunicado não encontrado: " + id));
        announcementRepository.delete(announcement);
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
        if (announcementRepository.existsByDateAndSuspendsClassesTrue(date)) {
            return List.of();
        }
        return expand(date, activeStudentsById(), defaultScheduleRepository.findAll(),
                scheduleExceptionRepository.findByDate(date),
                scheduleExceptionRepository.findByNewDate(date));
    }

    /**
     * O dia inteiro numa passada só: feriado, agenda e cancelamentos.
     *
     * Existe separado de {@link #scheduleOf} porque o painel do ADMIN precisa
     * das três coisas de uma vez, e compor três chamadas públicas repetiria a
     * consulta de feriado, a de alunos ativos e a de horários padrão a cada
     * abertura de tela — a mais frequente do sistema.
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
     * A expansão propriamente dita, já sem a checagem de feriado — quem chama
     * resolveu isso antes. Uma implementação só: duas fariam a mesma regra
     * divergir entre a agenda do ADMIN e a de aluno e professor.
     *
     * Não consulta nada: recebe tudo pronto. Enquanto ela mesma buscava as
     * remarcações do dia, expandir uma janela custava uma consulta por data —
     * quem tem o intervalo inteiro em mãos passa a fatia de cada dia e paga uma
     * vez só. É o que mantém a regra única sem impor o custo do dia avulso a
     * quem pergunta por vários.
     */
    private List<ScheduleEntry> expand(LocalDate date,
                                       Map<Long, StudentSummary> students,
                                       List<DefaultSchedule> schedules,
                                       List<ScheduleException> exceptionsOfDay,
                                       List<ScheduleException> reschedulesIntoDay) {
        List<ScheduleEntry> entries = new ArrayList<>();

        if (isWeekday(date)) {
            Set<Long> excepted = exceptionsOfDay.stream()
                    .map(ScheduleException::getStudentId)
                    .collect(Collectors.toSet());
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
            entryOf(students, reschedule.getStudentId(), reschedule.getNewTime(),
                    ScheduleEntry.Origin.RESCHEDULED, reschedule.getDate(), reschedule.getId())
                    .ifPresent(entries::add);
        }

        entries.sort(Comparator.comparing(ScheduleEntry::time).thenComparing(ScheduleEntry::studentName));
        return entries;
    }

    /**
     * Os cancelamentos do dia. Andam ao lado da agenda, não dentro dela: a aula
     * cancelada não acontece, então incluí-la entre as demais faria a recepção
     * chamar um aluno que não vem. Fora da agenda e visível, o ADMIN vê o que
     * cancelou e pode desfazer.
     *
     * Fim de semana não tem cancelamento a mostrar: não havia aula para
     * cancelar, e a exceção registrada nesse dia não muda nada.
     */
    private List<CanceledEntry> cancellations(LocalDate date,
                                              Map<Long, StudentSummary> students,
                                              List<DefaultSchedule> schedules,
                                              List<ScheduleException> exceptionsOfDay) {
        if (!isWeekday(date)) {
            return List.of();
        }

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
     * O dia do aluno: a fatia dele do dia do estúdio, com o feriado e os
     * próprios cancelamentos.
     *
     * Carrega as três coisas pela mesma razão do painel do ADMIN, e não por
     * simetria: sem a descrição do feriado o aluno veria um dia vazio sem saber
     * por quê, e sem o cancelamento "não tem aula hoje" ficaria idêntico a "sua
     * aula de hoje foi cancelada". Filtrar {@link #dayOf} em vez de expandir de
     * novo mantém uma implementação só da regra.
     */
    @Transactional(readOnly = true)
    public DayScheduleView dayOfStudent(Long studentId, LocalDate date) {
        DayScheduleView day = dayOf(date);
        return new DayScheduleView(day.date(), day.announcement(),
                day.entries().stream().filter(entry -> entry.studentId().equals(studentId)).toList(),
                day.cancellations().stream().filter(entry -> entry.studentId().equals(studentId)).toList());
    }

    /**
     * A janela do aluno: um dia por data, com o mesmo conteúdo do dia avulso.
     *
     * O custo é por intervalo, não por dia. Cada consulta cobre a janela
     * inteira — o aluno, o horário padrão dele, os feriados do período e as
     * exceções das duas pontas — e a expansão de cada data trabalha sobre
     * fatias em memória. A alternativa óbvia, chamar {@link #dayOfStudent} num
     * laço, refaria tudo isso por data e ainda carregaria o estúdio inteiro de
     * cada vez para descartar todo mundo menos um.
     *
     * O aluno inativo recebe a janela vazia, e não erro: ele ainda consegue
     * entrar enquanto o login não é revogado (a revogação é assíncrona), e um
     * 500 no lugar de "sem aula" seria a pior forma de contar isso.
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
     * A grade do professor. O grupo emerge de professor + hora: não existe
     * turma como entidade, então não há o que manter em sincronia.
     *
     * O telefone vem junto porque o professor atende aquele aluno — desmarcar,
     * avisar de atraso — e é buscado em lote, só para os alunos que ficaram na
     * grade depois do filtro: uma consulta, e nenhum telefone de aluno de outro
     * professor sai do registration.
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
     * Comunicado que não suspende não apaga nada: o dia segue com a agenda de
     * sempre, e o aviso viaja ao lado dela. Dia sem comunicado nenhum também
     * não suspende — daí o nulo entrar aqui em vez de virar checagem espalhada.
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
