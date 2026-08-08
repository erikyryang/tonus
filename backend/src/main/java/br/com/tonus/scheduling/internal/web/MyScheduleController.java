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
 * /me endpoints: the student's and teacher's id come from the token, and there is no
 * way to provide it — not path, not query, not body.
 *
 * The difference matters. An endpoint that accepted the id and checked whether it matches
 * the token's would depend on someone remembering to do the check; that is exactly
 * what gets forgotten, and it turns into IDOR. Here there is nothing to check.
 */
@RestController
@RequestMapping("/me")
class MyScheduleController {

    /** One month. More than that is not the question "when is my next class". */
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
     * The student's window in a single call.
     *
     * Their screen shows the upcoming days, and /me/schedule answers per day: it was
     * one request per date, each expanding the whole studio's schedule
     * to return one student's row. The contract here is the same as the single
     * day, repeated per date — the client does not recompose anything.
     *
     * The cap of {@value #MAX_WINDOW_DAYS} days exists because the cost is
     * proportional to the requested size: without it, `days=3650` would be a ten-year
     * expansion that any authenticated student could trigger.
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
        return TeacherGridResponse.of(day, schedulingService.gridOfTeacher(teacherId, day));
    }

    /**
     * Without a date, "today" — it is what student and teacher want in 99% of accesses.
     * The ADMIN is still required to provide the date in /schedule/day: there the
     * query is deliberate, and a silent default would hide a mistake.
     *
     * "Today" comes from the injected Clock, with the studio's timezone (see ClockConfig).
     */
    private LocalDate orToday(LocalDate date) {
        return date != null ? date : LocalDate.now(clock);
    }

    /**
     * A token with the right role but no link is a user configuration error,
     * not an empty schedule: returning empty would hide the problem and the
     * ADMIN would never know that login is broken.
     */
    private Long requireLink(Long linkedId, String role) {
        if (linkedId == null) {
            throw new AccessDeniedException("Usuário autenticado não está vinculado a nenhum " + role);
        }
        return linkedId;
    }

    /** A window outside the accepted range is a client usage error: 400. */
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
