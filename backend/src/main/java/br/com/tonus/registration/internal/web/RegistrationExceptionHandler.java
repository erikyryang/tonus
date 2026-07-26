package br.com.tonus.registration.internal.web;

import br.com.tonus.registration.internal.InactiveTeacherException;
import br.com.tonus.registration.internal.RegistrationNotFoundException;
import br.com.tonus.registration.internal.UnknownTeacherException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {TeacherController.class, StudentController.class})
class RegistrationExceptionHandler {

    @ExceptionHandler(RegistrationNotFoundException.class)
    ProblemDetail handleNotFound(RegistrationNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(UnknownTeacherException.class)
    ProblemDetail handleUnknownTeacher(UnknownTeacherException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InactiveTeacherException.class)
    ProblemDetail handleInactiveTeacher(InactiveTeacherException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
