package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.SchedulingService;
import br.com.tonus.shared.AuthenticatedUser;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints /me: o id do aluno e do professor vem do token, e não existe
 * caminho para informá-lo — nem path, nem query, nem corpo.
 *
 * A diferença importa. Um endpoint que aceitasse o id e conferisse se bate com
 * o do token dependeria de alguém lembrar de fazer a conferência; é justamente
 * o que se esquece, e vira IDOR. Aqui não há o que conferir.
 */
@RestController
@RequestMapping("/me")
class MyScheduleController {

    private final SchedulingService schedulingService;
    private final Clock clock;

    MyScheduleController(SchedulingService schedulingService, Clock clock) {
        this.schedulingService = schedulingService;
        this.clock = clock;
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasRole('STUDENT')")
    DayScheduleResponse mySchedule(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long studentId = requireLink(user == null ? null : user.studentId(), "aluno");
        LocalDate day = orToday(date);
        return DayScheduleResponse.of(day, schedulingService.scheduleOfStudent(studentId, day));
    }

    @GetMapping("/grid")
    @PreAuthorize("hasRole('TEACHER')")
    TeacherGridResponse myGrid(@AuthenticationPrincipal AuthenticatedUser user,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long teacherId = requireLink(user == null ? null : user.teacherId(), "professor");
        LocalDate day = orToday(date);
        return TeacherGridResponse.of(day, schedulingService.scheduleOfTeacher(teacherId, day));
    }

    /**
     * Sem data, "hoje" — é o que aluno e professor querem em 99% dos acessos.
     * O ADMIN continua obrigado a informar a data em /schedule/day: lá a
     * consulta é deliberada, e um default silencioso esconderia engano.
     *
     * "Hoje" sai do Clock injetado, com o fuso do estúdio (ver ClockConfig).
     */
    private LocalDate orToday(LocalDate date) {
        return date != null ? date : LocalDate.now(clock);
    }

    /**
     * Token com o papel certo mas sem vínculo é erro de configuração do
     * usuário, não uma agenda vazia: devolver vazio esconderia o problema e o
     * ADMIN nunca saberia que aquele login está quebrado.
     */
    private Long requireLink(Long linkedId, String role) {
        if (linkedId == null) {
            throw new AccessDeniedException("Usuário autenticado não está vinculado a nenhum " + role);
        }
        return linkedId;
    }
}
