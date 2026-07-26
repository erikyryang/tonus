package br.com.tonus.auth.internal;

/**
 * Usuário de acesso apontando para um aluno/professor que não existe no
 * cadastro. Erro de entrada (400): o vínculo veio errado do cliente.
 */
public class UnknownLinkedPersonException extends RuntimeException {

    public UnknownLinkedPersonException(String message) {
        super(message);
    }
}
