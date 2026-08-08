package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.SchedulingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalTime;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule configuration belongs to the ADMIN. The teacher owns the class, but not
 * who has a schedule: letting them write here would give them power over the schedule of
 * other teachers' students.
 */
@RestController
@RequestMapping("/default-schedules")
class DefaultScheduleController {

    private final SchedulingService schedulingService;

    DefaultScheduleController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @PostMapping
    ResponseEntity<DefaultScheduleResponse> create(@Valid @RequestBody DefaultScheduleRequest request) {
        DefaultScheduleResponse response = DefaultScheduleResponse.from(
                schedulingService.createDefaultSchedule(request.studentId(), request.time()));
        return ResponseEntity.created(URI.create("/default-schedules/" + response.id())).body(response);
    }

    /**
     * The path comes before any {@code /{id}} — same caution as billing's
     * {@code overdue-student-ids}: "by-student" must not be
     * confused with an id.
     */
    @GetMapping("/by-student")
    Map<Long, LocalTime> byStudent() {
        return schedulingService.schedulesByStudent();
    }

    @GetMapping("/{id}")
    DefaultScheduleResponse get(@PathVariable Long id) {
        return DefaultScheduleResponse.from(schedulingService.findDefaultSchedule(id));
    }

    @GetMapping
    Page<DefaultScheduleResponse> list(@PageableDefault Pageable pageable) {
        return schedulingService.findDefaultSchedules(pageable).map(DefaultScheduleResponse::from);
    }

    @PutMapping("/{id}")
    DefaultScheduleResponse update(@PathVariable Long id, @Valid @RequestBody DefaultScheduleRequest request) {
        return DefaultScheduleResponse.from(
                schedulingService.updateDefaultSchedule(id, request.studentId(), request.time()));
    }

    /**
     * Here DELETE really exists, unlike student and teacher: default
     * schedule is current configuration, not history. The history of a class given
     * belongs to the invoice.
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        schedulingService.deleteDefaultSchedule(id);
        return ResponseEntity.noContent().build();
    }
}
