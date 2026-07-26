package br.com.tonus.auth.internal;

public class LoginAlreadyTakenException extends RuntimeException {

    public LoginAlreadyTakenException(String login) {
        super("Login já cadastrado: " + login);
    }
}
