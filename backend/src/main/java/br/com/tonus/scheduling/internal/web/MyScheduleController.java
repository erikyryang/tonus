package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.SchedulingService;
import br.com.tonus.shared.AuthenticatedUser;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

    /** Um mês. Mais que isso não é a pergunta "quando é a minha próxima aula". */
    private static final int MAX_WINDOW_DAYS = 31;

    private final SchedulingService schedulingService;
    private final Clock clock;

    MyScheduleController(SchedulingService schedulingService, Clock clock) {
        this.schedulingService = schedulingService;
        this.clock = clock;
    }

    @GetMapping("/schedule")
    DayScheduleResponse mySchedule(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long studentId = requireLink(user == null ? null : user.studentId(), "aluno");
        LocalDate day = orToday(date);
        return DayScheduleResponse.of(schedulingService.dayOfStudent(studentId, day));
    }

    /**
     * A janela do aluno numa chamada só.
     *
     * A tela dele mostra os próximos dias, e /me/schedule responde por dia: era
     * uma requisição por data, cada uma expandindo a agenda do estúdio inteiro
     * para devolver a linha de um aluno. O contrato aqui é o mesmo do dia
     * avulso, repetido por data — o cliente não recompõe nada.
     *
     * O teto de {@value #MAX_WINDOW_DAYS} dias existe porque o custo é
     * proporcional ao tamanho pedido: sem ele, `days=3650` seria uma expansão
     * de dez anos que qualquer aluno autenticado poderia disparar.
     */
    @GetMapping("/schedule/week")
    List<DayScheduleResponse> myScheduleWeek(@AuthenticationPrincipal AuthenticatedUser user,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(defaultValue = "7") int days) {
        Long studentId = requireLink(user == null ? null : user.studentId(), "aluno");
        if (days < 1 || days > MAX_WINDOW_DAYS) {
            throw new InvalidWindowException(days, MAX_WINDOW_DAYS);
        }
        return schedulingService.weekOfStudent(studentId, orToday(from), days).stream()
                .map(DayScheduleResponse::of)
                .toList();
    }

    @GetMapping("/grid")
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

    /** Janela fora do intervalo aceito é erro de uso do cliente: 400. */
    static class InvalidWindowException extends RuntimeException {

        InvalidWindowException(int asked, int max) {
            super("A janela deve ter entre 1 e %d dias; vieram %d".formatted(max, asked));
        }
    }

    @ExceptionHandler(InvalidWindowException.class)
    ProblemDetail handleInvalidWindow(InvalidWindowException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
