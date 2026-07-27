package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.InvoiceAlreadyPaidException;
import br.com.tonus.billing.internal.InvoiceNotFoundException;
import br.com.tonus.billing.internal.SubscriptionAlreadyExistsException;
import br.com.tonus.billing.internal.SubscriptionNotFoundException;
import br.com.tonus.billing.internal.UnknownStudentException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        SubscriptionController.class,
        InvoiceController.class,
        MyInvoicesController.class})
class BillingExceptionHandler {

    @ExceptionHandler({SubscriptionNotFoundException.class, InvoiceNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({SubscriptionAlreadyExistsException.class, InvoiceAlreadyPaidException.class})
    ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UnknownStudentException.class)
    ProblemDetail handleBadRequest(UnknownStudentException ex) {
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
