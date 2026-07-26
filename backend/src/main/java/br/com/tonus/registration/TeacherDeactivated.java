package br.com.tonus.registration;

/**
 * Professor saiu do estúdio. Consumido pelo auth para revogar o acesso do
 * usuário vinculado. Entrega at-least-once: o listener é idempotente.
 */
public record TeacherDeactivated(Long teacherId) {
}
