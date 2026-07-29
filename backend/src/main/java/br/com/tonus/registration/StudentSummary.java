package br.com.tonus.registration;

/**
 * O nome do professor vem junto porque quem consome a lista praticamente sempre
 * precisa dele: a agenda do dia agrupa por professor + hora, e o grupo aparece
 * na tela com nome, não com id. Sem isso, cada consumidor faria uma consulta por
 * aluno para resolver o mesmo id — N+1 na tela mais quente do sistema.
 */
public record StudentSummary(Long studentId, String name, Long teacherId, String teacherName, boolean active) {
}
