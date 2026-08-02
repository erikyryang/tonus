package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.FuturePaymentDateException;
import br.com.tonus.billing.internal.InvoiceAlreadyPaidException;
import br.com.tonus.billing.internal.InvoiceNotFoundException;
import br.com.tonus.billing.internal.EnrollmentAlreadyExistsException;
import br.com.tonus.billing.internal.EnrollmentNotFoundException;
import br.com.tonus.billing.internal.PlanNotFoundException;
import br.com.tonus.billing.internal.UnknownPlanException;
import br.com.tonus.billing.internal.UnknownStudentException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        InvoiceController.class,
        MyInvoicesController.class,
        PlanController.class,
        PlanEnrollmentController.class,
        BillingSettingsController.class})
class BillingExceptionHandler {

    @ExceptionHandler({InvoiceNotFoundException.class,
            PlanNotFoundException.class, EnrollmentNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({InvoiceAlreadyPaidException.class,
            EnrollmentAlreadyExistsException.class})
    ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({UnknownStudentException.class, FuturePaymentDateException.class,
            UnknownPlanException.class})
    ProblemDetail handleBadRequest(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Rede de segurança para a corrida que a pré-checagem não cobre, mesmo
     * padrão de SchedulingExceptionHandler.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail handleConcurrentDuplicate(DuplicateKeyException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Registro já existe");
    }
}
