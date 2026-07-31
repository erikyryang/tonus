package br.com.tonus.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Critério de aceite: GET /me/schedule de um aluno nunca retorna dado de outro.
 *
 * O id do aluno vem do token e não existe caminho para informá-lo — nem path,
 * nem query, nem corpo. É o desenho que previne IDOR: não basta o endpoint
 * conferir se o id pedido é o do usuário, porque essa conferência é justamente
 * o que se esquece de fazer. Aqui não há o que conferir.
 */
class MyScheduleIT extends AbstractSchedulingIntegrationTest {

    /**
     * Quarta-feira sem uso em nenhum outro teste. Feriado é global e o banco é
     * compartilhado pela suíte: cadastrar um em MONDAY/TUESDAY apagaria a
     * agenda daquele dia para todas as outras classes.
     */
    private static final String OWN_HOLIDAY = "2026-08-26";

    /**
     * Uma janela só desta classe, longe das datas que as outras usam.
     *
     * Feriado é global e o banco é compartilhado pela suíte: marcar um feriado
     * dentro de MONDAY..WEDNESDAY apagaria aquele dia para todo mundo, e
     * WEDNESDAY é justamente o destino das remarcações dos outros testes.
     */
    private static final String WEEK_START = "2026-09-07";
    private static final String WEEK_SECOND = "2026-09-08";
    private static final String WEEK_HOLIDAY = "2026-09-09";

    @Test
    void studentSeesOnlyOwnClasses() throws Exception {
        long teacherId = createTeacher(unique("Professora Do Me"));
        long mine = createStudent(unique("Aluno Dono Do Token"), teacherId);
        long other = createStudent(unique("Aluno Alheio"), teacherId);
        createDefaultSchedule(mine, "07:00");
        createDefaultSchedule(other, "08:00");

        List<Long> visible = studentIdsOf(mySchedule(linkedStudentCookie(mine), MONDAY));

        assertThat(visible).containsOnly(mine);
    }

    /**
     * Mandar o id de outro aluno na query não muda nada: o parâmetro não existe
     * no contrato, então é ignorado — nunca vira o id consultado.
     */
    @Test
    void studentIdInQueryIsIgnored() throws Exception {
        long teacherId = createTeacher(unique("Professor Do IDOR"));
        long mine = createStudent(unique("Aluno Que Tenta"), teacherId);
        long victim = createStudent(unique("Aluno Vitima"), teacherId);
        createDefaultSchedule(mine, "07:00");
        createDefaultSchedule(victim, "08:00");

        MvcResult result = mockMvc.perform(get("/me/schedule")
                        .param("date", MONDAY)
                        .param("studentId", String.valueOf(victim))
                        .cookie(linkedStudentCookie(mine)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(studentIdsOf(json(result))).containsOnly(mine);
    }

    @Test
    void cancellationRemovesTheClassFromMySchedule() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Me Cancelado"));
        long studentId = createStudent(unique("Aluno Do Me Cancelado"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        Cookie student = linkedStudentCookie(studentId);

        assertThat(studentIdsOf(mySchedule(student, MONDAY))).containsOnly(studentId);

        cancelClass(studentId, MONDAY);

        assertThat(mySchedule(student, MONDAY).get("entries")).isEmpty();
    }

    @Test
    void rescheduleMovesTheClassInMySchedule() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Me Remarcado"));
        long studentId = createStudent(unique("Aluno Do Me Remarcado"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        Cookie student = linkedStudentCookie(studentId);

        rescheduleClass(studentId, MONDAY, WEDNESDAY, "08:00");

        assertThat(mySchedule(student, MONDAY).get("entries")).isEmpty();
        assertThat(studentIdsOf(mySchedule(student, WEDNESDAY))).containsOnly(studentId);
    }

    /**
     * O aluno precisa saber por que o dia está vazio (SPEC-009). Sem a
     * descrição do feriado, a tela dele teria de deduzir "está vazio, deve ser
     * feriado" — inventando um motivo que não tem como conferir.
     */
    @Test
    void holidayExplainsTheEmptyDay() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Feriado No Me"));
        long studentId = createStudent(unique("Aluno Do Feriado No Me"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        createHoliday(OWN_HOLIDAY, "Feriado do Me");

        JsonNode day = mySchedule(linkedStudentCookie(studentId), OWN_HOLIDAY);

        assertThat(day.get("entries")).isEmpty();
        assertThat(day.get("holiday").asText()).isEqualTo("Feriado do Me");
    }

    /**
     * A aula cancelada some da agenda por definição — e é justamente por isso
     * que o aluno precisa vê-la em separado: sem o cancelamento na resposta,
     * "não tem aula hoje" e "sua aula de hoje foi cancelada" ficam idênticos.
     */
    @Test
    void ownCancellationIsVisible() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Cancelamento No Me"));
        long mine = createStudent(unique("Aluno Que Ve O Cancelamento"), teacherId);
        long other = createStudent(unique("Aluno Alheio Cancelado"), teacherId);
        createDefaultSchedule(mine, "07:00");
        createDefaultSchedule(other, "08:00");
        cancelClass(mine, MONDAY);
        cancelClass(other, MONDAY);

        JsonNode day = mySchedule(linkedStudentCookie(mine), MONDAY);

        assertThat(day.get("cancellations")).hasSize(1);
        assertThat(day.get("cancellations").get(0).get("studentId").asLong()).isEqualTo(mine);
    }

    /**
     * ADMIN não tem aluno vinculado no token. Chamar um endpoint /me sem
     * vínculo é erro de uso, não uma agenda vazia — vazio esconderia o bug.
     */
    @Test
    void adminHasNoOwnSchedule() throws Exception {
        mockMvc.perform(get("/me/schedule").param("date", MONDAY).cookie(admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotUseStudentEndpoint() throws Exception {
        long teacherId = createTeacher(unique("Professor Curioso"));

        mockMvc.perform(get("/me/schedule")
                        .param("date", MONDAY)
                        .cookie(linkedTeacherCookie(teacherId)))
                .andExpect(status().isForbidden());
    }

    /**
     * A janela inteira numa chamada só.
     *
     * A tela do aluno mostra os próximos dias, e perguntar dia a dia eram sete
     * requisições — cada uma expandindo a agenda do estúdio inteiro para
     * devolver a linha de um aluno. O contrato é o mesmo do dia avulso, repetido
     * por data: o cliente não recompõe nada.
     */
    @Test
    void weekReturnsOneDayPerDateInTheWindow() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Semana"));
        long studentId = createStudent(unique("Aluno Da Semana"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        JsonNode week = myWeek(linkedStudentCookie(studentId), MONDAY, 3);

        assertThat(week).hasSize(3);
        assertThat(week.findValues("date").stream().map(JsonNode::asText).toList())
                .containsExactly(MONDAY, TUESDAY, WEDNESDAY);
        // Segunda, terça e quarta são dias úteis: a aula de 07:00 aparece nos três.
        assertThat(week.findValues("entries").stream().allMatch(entries -> entries.size() == 1)).isTrue();
    }

    /** A janela do aluno enxerga a mesma fatia que o dia avulso: só ele. */
    @Test
    void weekNeverLeaksAnotherStudent() throws Exception {
        long teacherId = createTeacher(unique("Professora Da Semana Alheia"));
        long mine = createStudent(unique("Aluno Dono Da Semana"), teacherId);
        long other = createStudent(unique("Aluno Alheio Da Semana"), teacherId);
        createDefaultSchedule(mine, "07:00");
        createDefaultSchedule(other, "08:00");

        JsonNode week = myWeek(linkedStudentCookie(mine), MONDAY, 2);

        assertThat(week.findValues("studentId").stream().map(JsonNode::asLong).distinct().toList())
                .containsOnly(mine);
    }

    /** Feriado e cancelamento explicam o dia vazio também na janela. */
    @Test
    void weekCarriesHolidayAndCancellation() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Semana Cheia"));
        long studentId = createStudent(unique("Aluno Da Semana Cheia"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        cancelClass(studentId, WEEK_SECOND);
        createHoliday(WEEK_HOLIDAY, "Feriado da Semana");

        JsonNode week = myWeek(linkedStudentCookie(studentId), WEEK_START, 3);

        assertThat(week.get(1).get("cancellations")).hasSize(1);
        assertThat(week.get(2).get("holiday").asText()).isEqualTo("Feriado da Semana");
        assertThat(week.get(2).get("entries")).isEmpty();
    }

    @Test
    void teacherCannotUseTheStudentWeek() throws Exception {
        long teacherId = createTeacher(unique("Professor Curioso Da Semana"));

        mockMvc.perform(get("/me/schedule/week")
                        .param("from", MONDAY)
                        .cookie(linkedTeacherCookie(teacherId)))
                .andExpect(status().isForbidden());
    }

    /** Janela sem teto seria uma expansão sem teto: o custo é por dia pedido. */
    @Test
    void weekRefusesAWindowLargerThanTheMaximum() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Janela Grande"));
        long studentId = createStudent(unique("Aluno Da Janela Grande"), teacherId);

        mockMvc.perform(get("/me/schedule/week")
                        .param("from", MONDAY)
                        .param("days", "32")
                        .cookie(linkedStudentCookie(studentId)))
                .andExpect(status().isBadRequest());
    }

    private JsonNode mySchedule(Cookie student, String date) throws Exception {
        MvcResult result = mockMvc.perform(get("/me/schedule").param("date", date).cookie(student))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private JsonNode myWeek(Cookie student, String from, int days) throws Exception {
        MvcResult result = mockMvc.perform(get("/me/schedule/week")
                        .param("from", from)
                        .param("days", String.valueOf(days))
                        .cookie(student))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private List<Long> studentIdsOf(JsonNode day) {
        return day.get("entries").findValues("studentId").stream().map(JsonNode::asLong).toList();
    }
}
