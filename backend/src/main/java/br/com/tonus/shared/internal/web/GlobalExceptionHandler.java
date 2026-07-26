package br.com.tonus.shared.internal.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Handler global de erros do projeto (RFC 9457 Problem Details).
 * MethodArgumentNotValidException e HttpMessageNotReadableException já
 * são convertidas para Problem Details pelo suporte nativo do Spring
 * (spring.mvc.problemdetails.enabled=true); aqui só cobrimos o que
 * escapa desse mecanismo — exceções não mapeadas por nenhum módulo,
 * pra nunca vazar stacktrace pro cliente.
 *
 * AccessDeniedException/AuthenticationException são relançadas de
 * propósito: se o DispatcherServlet as resolvesse aqui, elas nunca
 * chegariam ao ExceptionTranslationFilter do Spring Security, que é
 * quem deve gerar o 403/401 (senão viram 500 por engano).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException ex) {
        throw ex;
    }

    @ExceptionHandler(AuthenticationException.class)
    public void rethrowAuthentication(AuthenticationException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
    }
}
