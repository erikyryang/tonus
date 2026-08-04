package br.com.tonus.auth.internal;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Quem não se autenticou recebe 401 em Problem Details, não o 403 padrão do
 * Spring Security.
 *
 * A distinção importa para o cliente: 401 é "não sei quem você é" — e é o
 * gatilho do refresh no front —, enquanto 403 fica para quem já se identificou
 * e não tem permissão. Sem isso, um access token expirado seria indistinguível
 * de falta de permissão e a sessão nunca se renovaria.
 *
 * A mensagem é genérica de propósito: dizer se o token expirou, foi forjado ou
 * simplesmente não veio ajudaria quem está sondando a API.
 */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ProblemAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                          HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Não autenticado");

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
