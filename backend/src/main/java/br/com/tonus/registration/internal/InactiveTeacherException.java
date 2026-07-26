package br.com.tonus.registration.internal;

/**
 * Professor que saiu do estúdio não recebe aluno novo. Como a desativação é a
 * forma de registrar a saída, vincular um aluno a um professor inativo é erro
 * de entrada (400), não recurso ausente.
 */
public class InactiveTeacherException extends RuntimeException {

    public InactiveTeacherException(Long teacherId) {
        super("Professor inativo: " + teacherId);
    }
}
