package br.com.tonus.scheduling.internal;

/**
 * Um horário padrão por aluno. O banco também garante (uk_default_schedule_student);
 * esta exceção existe para a resposta ser 409 com mensagem, em vez de erro de
 * integridade cru.
 */
public class ScheduleAlreadyExistsException extends RuntimeException {

    public ScheduleAlreadyExistsException(Long studentId) {
        super("Aluno já possui horário padrão: " + studentId);
    }
}
