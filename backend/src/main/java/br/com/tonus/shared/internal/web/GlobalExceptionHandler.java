package br.com.tonus.shared.internal.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global error handler for the project (RFC 9457 Problem Details).
 * MethodArgumentNotValidException and HttpMessageNotReadableException are
 * already converted to Problem Details by Spring's native support
 * (spring.mvc.problemdetails.enabled=true); here we only cover what
 * escapes that mechanism — exceptions not mapped by any module, so a
 * stacktrace never leaks to the client.
 *
 * AccessDeniedException/AuthenticationException are rethrown on
 * purpose: if the DispatcherServlet resolved them here, they would never
 * reach Spring Security's ExceptionTranslationFilter, which is the one
 * that should generate the 403/401 (otherwise they turn into a 500 by
 * mistake).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException ex) {
        throw ex;
    }

    @ExceptionHandler(AuthenticationException.class)
    public void rethrowAuthentication(AuthenticationException ex) {
        throw ex;
    }

    /**
     * An unknown {@code sort} column on a listing is a client error, not a
     * server bug: Spring Data throws this when {@code ?sort=telefone} names a
     * property that doesn't exist. Without this it would fall into the 500
     * catch-all below — a typo producing a 500.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Propriedade de ordenação desconhecida: " + ex.getPropertyName());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // The client sees a generic 500, but the cause has to be findable:
        // without this log every bug in any module becomes a silent 500.
        log.error("Erro interno inesperado", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
    }
}
