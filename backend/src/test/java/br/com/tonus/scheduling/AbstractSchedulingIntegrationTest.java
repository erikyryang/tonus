package br.com.tonus.scheduling;

import br.com.tonus.registration.AbstractRegistrationIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes do módulo scheduling.
 *
 * Herda de registration porque agenda não existe sem aluno e professor: o seed
 * continua sendo feito pela API REST, nunca por repositório — os repositórios
 * vivem em {@code *.internal.domain} e o teste roda no pacote raiz do módulo.
 *
 * As datas são literais fixos e nunca derivadas de "hoje": a expansão da agenda
 * depende do dia da semana, então um teste que usasse a data corrente passaria
 * de segunda a sexta e quebraria no sábado.
 *
 * Pelo mesmo motivo o relógio é fixo ({@link SchedulingClockConfig}): "hoje" é
 * sempre {@link #MONDAY}, e as datas abaixo têm posição estável em relação a
 * ele — passado e futuro não mudam com a passagem do tempo real.
 */
@Import(SchedulingClockConfig.class)
abstract class AbstractSchedulingIntegrationTest extends AbstractRegistrationIntegrationTest {

    /** Segunda-feira — é também "hoje" para o relógio fixo dos testes. */
    protected static final String MONDAY = "2026-07-27";
    /** Terça-feira. */
    protected static final String TUESDAY = "2026-07-28";
    /** Quarta-feira — destino das remarcações. */
    protected static final String WEDNESDAY = "2026-07-29";
    /** Sábado. */
    protected static final String SATURDAY = "2026-08-01";
    /** Sexta-feira anterior a "hoje" — para exercitar recusa de data passada. */
    protected static final String LAST_FRIDAY = "2026-07-24";

    protected long createDefaultSchedule(long studentId, String time) throws Exception {
        return idOf(postDefaultSchedule(studentId, time)
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected ResultActions postDefaultSchedule(long studentId, String time) throws Exception {
        return mockMvc.perform(post("/default-schedules")
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":%d,"time":"%s"}
                        """.formatted(studentId, time)));
    }

    protected long createHoliday(String date, String description) throws Exception {
        return idOf(postHoliday(date, description)
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected ResultActions postHoliday(String date, String description) throws Exception {
        return mockMvc.perform(post("/holidays")
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"date":"%s","description":"%s"}
                        """.formatted(date, description)));
    }

    protected long cancelClass(long studentId, String date) throws Exception {
        return idOf(postException("""
                {"studentId":%d,"date":"%s","type":"CANCELED"}
                """.formatted(studentId, date))
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected long rescheduleClass(long studentId, String date, String newDate, String newTime) throws Exception {
        return idOf(postException("""
                {"studentId":%d,"date":"%s","type":"RESCHEDULED","newDate":"%s","newTime":"%s"}
                """.formatted(studentId, date, newDate, newTime))
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected ResultActions postException(String body) throws Exception {
        return mockMvc.perform(post("/schedule-exceptions")
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Agenda do dia já expandida, como o ADMIN a vê. */
    protected JsonNode daySchedule(String date) throws Exception {
        MvcResult result = mockMvc.perform(get("/schedule/day").param("date", date).cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    /**
     * Cookie de um aluno de verdade — com vínculo em {@code users.student_id}.
     * O {@code studentCookie} herdado do auth cria usuário sem vínculo, e é
     * justamente o vínculo que os endpoints /me usam para achar o aluno.
     */
    protected Cookie linkedStudentCookie(long studentId) throws Exception {
        return linkedCookie("STUDENT", "studentId", studentId);
    }

    protected Cookie linkedTeacherCookie(long teacherId) throws Exception {
        return linkedCookie("TEACHER", "teacherId", teacherId);
    }

    private Cookie linkedCookie(String role, String linkField, long linkId) throws Exception {
        String login = unique(linkField.toLowerCase()) + "@tonus.com";
        mockMvc.perform(post("/users")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"%s","role":"%s","%s":%d}
                                """.formatted(login, DEFAULT_PASSWORD, role, linkField, linkId)))
                .andExpect(status().isCreated());
        return extractCookie(login(login, DEFAULT_PASSWORD), ACCESS_COOKIE);
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
