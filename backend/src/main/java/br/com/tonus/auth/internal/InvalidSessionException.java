package br.com.tonus.auth.internal;

/**
 * The token is legitimate, but the user behind it is no longer valid — left
 * the studio or had access revoked.
 *
 * Happens because the access token is self-contained: it remains
 * cryptographically valid until it expires, even after the login is
 * deactivated. The refresh is already blocked at revocation time, so the
 * window is short; what this exception guarantees is that the session check
 * doesn't return 200 within it.
 */
public class InvalidSessionException extends RuntimeException {

    public InvalidSessionException() {
        super("Sessão inválida");
    }
}
