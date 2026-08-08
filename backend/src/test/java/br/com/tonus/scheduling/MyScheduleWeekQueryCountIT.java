package br.com.tonus.scheduling;

import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The student's window costs the same as a single day: the queries run over
 * the whole range, not one round per date.
 *
 * Without this test the guarantee does not exist. Going back to expanding day
 * by day breaks no behavioral assertion — the response stays identical — and
 * the cost would only show up in production, on the screen every student
 * opens. That is how it was before: seven HTTP calls, each loading all active
 * students and all the studio's default schedules to return a single
 * student's row.
 *
 * Separate class because `generate_statistics` changes the context's
 * properties, and enabling it for the whole suite would add overhead and a
 * per-session metrics summary to every log.
 *
 * The ceiling is deliberately loose: the exact number depends on Hibernate
 * details that can change without the design changing. What it separates is
 * "per range" from "per day" — with 14 days, one query per day would blow
 * past it.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"})
class MyScheduleWeekQueryCountIT extends AbstractSchedulingIntegrationTest {

    private static final int DAYS = 14;

     /**
      * Headroom over the batched design: student, default schedule, holidays in
      * the range, exceptions in the range and reschedules into the range.
      */
    private static final int CEILING = 12;

    @Autowired
    private SessionFactory sessionFactory;

    @Test
    void windowDoesNotQueryPerDay() throws Exception {
        String marker = unique("Semana");
        long teacherId = createTeacher(marker + " Professor");
        long studentId = createStudent(marker + " Aluno", teacherId);
        createDefaultSchedule(studentId, "07:00");
        // Neighbor students: the window cannot get more expensive because the
        // studio has people, which is exactly what the full expansion used to do.
        for (int i = 0; i < 5; i++) {
            createDefaultSchedule(createStudent(marker + " Vizinho " + i, teacherId), "08:00");
        }
        Cookie student = linkedStudentCookie(studentId);

        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        mockMvc.perform(get("/me/schedule/week")
                        .param("from", MONDAY)
                        .param("days", String.valueOf(DAYS))
                        .cookie(student))
                .andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount())
                .as("consultas para montar uma janela de %d dias", DAYS)
                .isLessThanOrEqualTo(CEILING);
    }
}
