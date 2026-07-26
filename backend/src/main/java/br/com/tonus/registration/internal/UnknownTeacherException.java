package br.com.tonus.registration.internal;

/**
 * Vínculo aluno->professor apontando para um professor que não existe.
 * É erro do cliente na entrada (400), não recurso ausente (404): o recurso
 * pedido é o aluno, e ele é que está inválido.
 */
public class UnknownTeacherException extends RuntimeException {

    public UnknownTeacherException(Long teacherId) {
        super("Professor inexistente: " + teacherId);
    }
}
