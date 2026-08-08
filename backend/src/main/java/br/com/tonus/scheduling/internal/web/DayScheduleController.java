package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.SchedulingService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The view of the whole studio on a day — ADMIN only. Student and teacher have the
 * /me endpoints, which return only their own slice.
 */
@RestController
@RequestMapping("/schedule")
class DayScheduleController {

    private final SchedulingService schedulingService;

    DayScheduleController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @GetMapping("/day")
    DayScheduleResponse day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return DayScheduleResponse.of(schedulingService.dayOf(date));
    }
}
