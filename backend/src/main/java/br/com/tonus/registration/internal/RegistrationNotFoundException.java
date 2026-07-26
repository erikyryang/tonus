package br.com.tonus.registration.internal;

public class RegistrationNotFoundException extends RuntimeException {

    public RegistrationNotFoundException(String message) {
        super(message);
    }
}
