package br.com.tonus.shared;

/**
 * Whoever is authenticated, from any module's point of view.
 *
 * Exists for the {@code /me/...} endpoints: the student/teacher id has to
 * come from the token, never from the path or query (prevents IDOR), but the
 * module that serves {@code /me/schedule} is scheduling, and scheduling
 * can't depend on auth — the allowed edge is auth -> registration, and the
 * reverse would close a cycle.
 *
 * The way out is for the token to carry the link and for the principal to be
 * a type from shared, which everyone can depend on. auth fills it in; the
 * others only read it.
 *
 * {@code studentId} and {@code teacherId} are null for ADMIN, who is neither
 * a student nor a teacher.
 */
public record AuthenticatedUser(Long userId, Long studentId, Long teacherId) {
}
