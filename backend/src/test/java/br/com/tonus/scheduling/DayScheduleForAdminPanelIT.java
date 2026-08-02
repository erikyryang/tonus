package br.com.tonus.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O que a agenda do dia precisa carregar para o painel de recepção da SPEC-007.
 *
 * A tela não pode remontar a regra do dia por conta própria: se ela cruzasse
 * comunicado, exceção e template no navegador, existiriam duas implementações da
 * mesma expansão e uma delas ficaria para trás. Então o dia já vem com nome do
 * professor (para agrupar), origem da remarcação (para a badge), id da exceção
 * (para desfazer), o comunicado do dia (para o banner) e os cancelamentos —
 * que a agenda esconde por definição e o ADMIN precisa ver para poder desfazer.
 */
class DayScheduleForAdminPanelIT extends AbstractSchedulingIntegrationTest {

    @Test
    void entryCarriesTeacherNameForGrouping() throws Exception {
        String teacherName = unique("Professora Do Painel");
        long teacherId = createTeacher(teacherName);
        long studentId = createStudent(unique("Aluna Do Painel"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        JsonNode entry = entryOf(daySchedule(MONDAY), studentId);

        assertThat(entry.get("teacherName").asText()).isEqualTo(teacherName);
    }

    /**
     * A badge "remarcada de dd/MM" só existe se o dia disser de onde a aula
     * veio: sem a data de origem, a tela mostraria a aula como se fosse do
     * horário padrão do aluno.
     *
     * O aluno aparece DUAS vezes na quarta — às 07:00 pelo template, que
     * continua valendo, e às 09:00 pela remarcação. Não é duplicação: ele
     * perdeu a aula de segunda e ganhou uma a mais na quarta. Por isso o teste
     * seleciona pela hora, não só pelo aluno.
     */
    @Test
    void rescheduledEntryCarriesItsOriginAndExceptionId() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Origem"));
        long studentId = createStudent(unique("Aluno Remarcado Pro Painel"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        long exceptionId = rescheduleClass(studentId, MONDAY, WEDNESDAY, "09:00");

        JsonNode day = daySchedule(WEDNESDAY);
        JsonNode entry = entryAt(day, studentId, "09:00");

        assertThat(entry.get("origin").asText()).isEqualTo("RESCHEDULED");
        assertThat(entry.get("originDate").asText()).isEqualTo(MONDAY);
        assertThat(entry.get("exceptionId").asLong()).isEqualTo(exceptionId);

        assertThat(entryAt(day, studentId, "07:00").get("origin").asText()).isEqualTo("TEMPLATE");
    }

    @Test
    void templateEntryHasNoOriginNorException() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Template Puro"));
        long studentId = createStudent(unique("Aluno Do Template Puro"), teacherId);
        createDefaultSchedule(studentId, "08:00");

        JsonNode entry = entryOf(daySchedule(MONDAY), studentId);

        assertThat(entry.get("originDate").isNull()).isTrue();
        assertThat(entry.get("exceptionId").isNull()).isTrue();
    }

    /**
     * Data exclusiva deste teste. {@code announcement.date} é UNIQUE e o banco é
     * compartilhado por toda a execução: reaproveitar a data de outro IT faz o
     * segundo a rodar receber 409 — e qual dos dois quebra depende da ordem.
     */
    @Test
    void suspendedDayCarriesItsAnnouncement() throws Exception {
        String date = "2026-10-28";
        createAnnouncement(date, "Feriado do painel", true);

        JsonNode day = daySchedule(date);

        assertThat(day.get("announcement").get("description").asText()).isEqualTo("Feriado do painel");
        assertThat(day.get("entries")).isEmpty();
        assertThat(day.get("cancellations")).isEmpty();
    }

    @Test
    void ordinaryDayHasNoAnnouncement() throws Exception {
        assertThat(daySchedule(TUESDAY).get("announcement").isNull()).isTrue();
    }

    /**
     * Cancelado sai da agenda e entra na lista de cancelamentos — não some. É a
     * diferença entre o ADMIN perceber o engano e descobri-lo pelo aluno que
     * apareceu no estúdio.
     */
    @Test
    void canceledStudentMovesToTheCancellationList() throws Exception {
        String teacherName = unique("Professor Do Cancelado");
        long teacherId = createTeacher(teacherName);
        String studentName = unique("Aluna Cancelada Do Painel");
        long studentId = createStudent(studentName, teacherId);
        createDefaultSchedule(studentId, "10:00");
        long exceptionId = cancelClass(studentId, MONDAY);

        JsonNode day = daySchedule(MONDAY);

        assertThat(studentIdsOf(day.get("entries"))).doesNotContain(studentId);

        JsonNode cancellation = nodeFor(day.get("cancellations"), studentId);
        assertThat(cancellation.get("exceptionId").asLong()).isEqualTo(exceptionId);
        assertThat(cancellation.get("studentName").asText()).isEqualTo(studentName);
        assertThat(cancellation.get("teacherName").asText()).isEqualTo(teacherName);
        assertThat(cancellation.get("time").asText()).isEqualTo("10:00");
    }

    /**
     * Remarcação não é cancelamento: quem foi remarcado aparece — com o desfazer
     * disponível — no dia de destino, onde a aula de fato acontece.
     */
    @Test
    void rescheduledStudentIsNotListedAsCanceled() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Nao Cancelado"));
        long studentId = createStudent(unique("Aluno Apenas Remarcado"), teacherId);
        createDefaultSchedule(studentId, "11:00");
        rescheduleClass(studentId, MONDAY, WEDNESDAY, "09:00");

        assertThat(studentIdsOf(daySchedule(MONDAY).get("cancellations"))).doesNotContain(studentId);
    }

    @Test
    void weekendHasNoCancellations() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Sabado Cancelado"));
        long studentId = createStudent(unique("Aluno Do Sabado Cancelado"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        cancelClass(studentId, SATURDAY);

        assertThat(daySchedule(SATURDAY).get("cancellations")).isEmpty();
    }

    private JsonNode entryOf(JsonNode day, long studentId) {
        return nodeFor(day.get("entries"), studentId);
    }

    /** O mesmo aluno pode ter mais de uma aula no dia: template + remarcação. */
    private JsonNode entryAt(JsonNode day, long studentId, String time) {
        for (JsonNode node : day.get("entries")) {
            if (node.get("studentId").asLong() == studentId && time.equals(node.get("time").asText())) {
                return node;
            }
        }
        throw new AssertionError("Aluno %d não tem aula às %s".formatted(studentId, time));
    }

    private JsonNode nodeFor(JsonNode list, long studentId) {
        for (JsonNode node : list) {
            if (node.get("studentId").asLong() == studentId) {
                return node;
            }
        }
        throw new AssertionError("Aluno %d não está na lista".formatted(studentId));
    }

    private java.util.List<Long> studentIdsOf(JsonNode list) {
        return list.findValues("studentId").stream().map(JsonNode::asLong).toList();
    }
}
