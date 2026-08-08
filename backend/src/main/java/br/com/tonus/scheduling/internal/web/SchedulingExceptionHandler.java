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
     * Safety net for the race that the pre-check does not cover: two
     * simultaneous requests both pass through {@code existsBy...} before either
     * one writes, and the second only discovers the conflict at the constraint.
     * Without this, that rare case would turn into a 500.
     *
     * Only {@link DuplicateKeyException}, and not the whole DataIntegrityViolationException:
     * a CHECK violation is our bug — validation should have caught it
     * beforehand — and it needs to keep showing up as an error, not turn into a 409.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail handleConcurrentDuplicate(DuplicateKeyException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Registro já existe");
    }
}
