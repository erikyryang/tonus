package br.com.tonus.notification.internal.web;

import br.com.tonus.notification.internal.WhatsAppGatewayException;
import br.com.tonus.notification.internal.WhatsAppGatewayNotConfiguredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Two failures that are not the application's fault and must not read as 500.
 *
 * Scoped to this module's controllers, like BillingExceptionHandler: the
 * distinction only means something for whoever administers the gateway.
 */
@RestControllerAdvice(assignableTypes = WhatsAppAdminController.class)
class WhatsAppExceptionHandler {

    /** Upstream is down. 500 here would send someone hunting a bug that is not ours. */
    @ExceptionHandler(WhatsAppGatewayException.class)
    ProblemDetail gatewayUnavailable(WhatsAppGatewayException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "O gateway de WhatsApp não respondeu. Tente novamente em instantes.");
        problem.setTitle("Gateway indisponível");
        return problem;
    }

    /** The deployment is incomplete — nothing the ADMIN clicks would help. */
    @ExceptionHandler(WhatsAppGatewayNotConfiguredException.class)
    ProblemDetail notConfigured(WhatsAppGatewayNotConfiguredException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "O gateway de WhatsApp não está configurado neste ambiente.");
        problem.setTitle("Gateway não configurado");
        return problem;
    }
}
