package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.InvalidExceptionTargetException;
import br.com.tonus.scheduling.internal.SchedulingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/schedule-exceptions")
class ScheduleExceptionController {

    private final SchedulingService schedulingService;

    ScheduleExceptionController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    /**
     * The type picks the service method instead of becoming a parameter of it:
     * canceling and rescheduling publish different events and have different
     * invariants, so a single method with ifs inside would hide the two rules.
     */
    @PostMapping
    ResponseEntity<ScheduleExceptionResponse> create(@Valid @RequestBody ScheduleExceptionRequest request) {
        ScheduleExceptionResponse response = ScheduleExceptionResponse.from(switch (request.type()) {
            case CANCELED -> {
                if (request.newDate() != null || request.newTime() != null) {
                    throw new InvalidExceptionTargetException("Cancelamento não aceita nova data nem nova hora");
                }
                yield schedulingService.cancelClass(request.studentId(), request.date());
            }
            case RESCHEDULED -> schedulingService.rescheduleClass(
                    request.studentId(), request.date(), request.newDate(), request.newTime());
        });
        return ResponseEntity.created(URI.create("/schedule-exceptions/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    ScheduleExceptionResponse get(@PathVariable Long id) {
        return ScheduleExceptionResponse.from(schedulingService.findException(id));
    }

    /**
     * Filters by student and by interval, all optional. The interval is over
     * {@code date} — the date on which the change happens — and not over the destination
     * date: whoever looks for "what changed this week" is asking about the
     * days that stopped being normal.
     */
    @GetMapping
    Page<ScheduleExceptionResponse> list(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault Pageable pageable) {
        return schedulingService.findExceptions(studentId, from, to, pageable)
                .map(ScheduleExceptionResponse::from);
    }

    /**
     * Deleting returns the day to the template. It is the undo of a mistakenly
     * marked cancellation — without it, the correction would only exist in the database.
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        schedulingService.deleteException(id);
        return ResponseEntity.noContent().build();
    }
}
