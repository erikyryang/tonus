package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.SchedulingService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A visão do estúdio inteiro num dia — só ADMIN. Aluno e professor têm os
 * endpoints /me, que devolvem apenas a própria fatia.
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
