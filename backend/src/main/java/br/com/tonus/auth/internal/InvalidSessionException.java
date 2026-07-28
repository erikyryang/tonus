package br.com.tonus.auth.internal;

/**
 * O token é legítimo, mas o usuário por trás dele não vale mais — saiu do
 * estúdio ou teve o acesso revogado.
 *
 * Acontece porque o access token é autocontido: ele continua criptograficamente
 * válido até expirar, mesmo depois de o login ser desativado. O refresh já é
 * bloqueado na hora da revogação, então a janela é curta; o que esta exceção
 * garante é que a verificação de sessão não devolva 200 dentro dela.
 */
public class InvalidSessionException extends RuntimeException {

    public InvalidSessionException() {
        super("Sessão inválida");
    }
}
