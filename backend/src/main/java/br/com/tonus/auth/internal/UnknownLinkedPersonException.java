package br.com.tonus.auth.internal;

/**
 * Access user pointing to a student/teacher that doesn't exist in
 * registration. Input error (400): the link came in wrong from the client.
 */
public class UnknownLinkedPersonException extends RuntimeException {

    public UnknownLinkedPersonException(String message) {
        super(message);
    }
}
