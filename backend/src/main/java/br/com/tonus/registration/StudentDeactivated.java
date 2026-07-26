package br.com.tonus.registration;

/**
 * Aluno saiu do estúdio. Consumido pelo auth para revogar o acesso do usuário
 * vinculado. Entrega at-least-once: o listener é idempotente.
 */
public record StudentDeactivated(Long studentId) {
}
