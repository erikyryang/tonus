package br.com.tonus.auth.internal;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Whoever is authenticated and lacks permission receives 403 in Problem
 * Details — written straight to the response.
 *
 * The "straight to the response" is the whole point, and mirrors
 * {@link ProblemAuthenticationEntryPoint}. Spring Security's default handler
 * calls {@code response.sendError(403)}, which makes the container re-dispatch
 * to {@code /error}; the security chain filters ERROR dispatches too, the
 * stateless SecurityContext no longer exists by then, and the entry point's
 * 401 overwrites the 403 that had already been decided. The application
 * announced 403 in the README and asserted it in every test, and answered 401
 * to every real client.
 *
 * The distinction is not cosmetic: by the front end's rule a 401 means refresh,
 * retry, and then logout. Landing on a route for another role logged the user
 * out instead of saying they lack permission.
 *
 * The message says nothing about the route or the role that was missing —
 * that is reconnaissance material for whoever is probing the API.
 */
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Sem permissão");

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
