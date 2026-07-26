package br.com.tonus.shared;

/**
 * Quem está autenticado, do ponto de vista de qualquer módulo.
 *
 * Existe para os endpoints {@code /me/...}: o id do aluno/professor tem de vir
 * do token, nunca do path ou da query (previne IDOR), mas o módulo que atende
 * {@code /me/schedule} é o scheduling, e scheduling não pode depender de auth
 * — a aresta permitida é auth -> registration, e a inversa fecharia ciclo.
 *
 * A saída é o token carregar o vínculo e o principal ser um tipo de shared, de
 * quem todos podem depender. O auth preenche; os demais só leem.
 *
 * {@code studentId} e {@code teacherId} são nulos para ADMIN, que não é nem
 * aluno nem professor.
 */
public record AuthenticatedUser(Long userId, Long studentId, Long teacherId) {
}
