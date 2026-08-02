package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.ExceptionAlreadyExistsException;
import br.com.tonus.scheduling.internal.AnnouncementAlreadyExistsException;
import br.com.tonus.scheduling.internal.InvalidExceptionTargetException;
import br.com.tonus.scheduling.internal.InvalidRescheduleDateException;
import br.com.tonus.scheduling.internal.ScheduleAlreadyExistsException;
import br.com.tonus.scheduling.internal.SchedulingNotFoundException;
import br.com.tonus.scheduling.internal.UnknownStudentException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        DefaultScheduleController.class,
        ScheduleExceptionController.class,
        AnnouncementController.class,
        DayScheduleController.class,
        MyScheduleController.class})
class SchedulingExceptionHandler {

    @ExceptionHandler(SchedulingNotFoundException.class)
    ProblemDetail handleNotFound(SchedulingNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({ScheduleAlreadyExistsException.class,
            ExceptionAlreadyExistsException.class,
            AnnouncementAlreadyExistsException.class})
    ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({UnknownStudentException.class,
            InvalidExceptionTargetException.class,
            InvalidRescheduleDateException.class})
    ProblemDetail handleBadRequest(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Rede de segurança para a corrida que a pré-checagem não cobre: duas
     * requisições simultâneas passam as duas pelo {@code existsBy...} antes de
     * qualquer uma gravar, e a segunda só descobre o conflito na constraint.
     * Sem isto, esse caso raro viraria 500.
     *
     * Só {@link DuplicateKeyException}, e não DataIntegrityViolationException
     * inteira: violação de CHECK é bug nosso — a validação deveria ter pegado
     * antes — e precisa continuar aparecendo como erro, não virar 409.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail handleConcurrentDuplicate(DuplicateKeyException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Registro já existe");
    }
}
