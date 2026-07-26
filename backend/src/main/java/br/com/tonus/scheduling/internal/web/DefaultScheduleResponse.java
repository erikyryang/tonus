package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.domain.DefaultSchedule;
import java.time.LocalTime;

public record DefaultScheduleResponse(Long id, Long studentId, LocalTime time) {

    public static DefaultScheduleResponse from(DefaultSchedule schedule) {
        return new DefaultScheduleResponse(schedule.getId(), schedule.getStudentId(), schedule.getTime());
    }
}
