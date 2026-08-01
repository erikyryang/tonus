package br.com.tonus.auth.internal.web;

import br.com.tonus.auth.internal.AuthProperties;
import br.com.tonus.auth.internal.AuthService;
import br.com.tonus.auth.internal.InvalidCredentialsException;
import br.com.tonus.auth.internal.InvalidRefreshTokenException;
import br.com.tonus.auth.internal.InvalidSessionException;
import br.com.tonus.auth.internal.LoginRateLimiter;
import br.com.tonus.shared.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String ROLE_PREFIX = "ROLE_";

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;
    private final AuthProperties properties;

    public AuthController(AuthService authService, LoginRateLimiter loginRateLimiter, AuthProperties properties) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
        this.properties = properties;
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse response) {
        if (!loginRateLimiter.tryConsume(request.login())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        AuthService.AuthTokens tokens = authService.login(request.login(), request.password());
        setAuthCookies(httpRequest, response, tokens);
        return ResponseEntity.ok().build();
    }

    /**
     * Quem está logado. Existe porque a sessão vive em cookie httpOnly: o front
     * não lê o token e não deve decodificar JWT — pergunta aqui.
     *
     * Papel e vínculo saem do token, não do banco: é o token que autoriza as
     * demais chamadas, e a tela tem de refletir exatamente o que a API vai
     * aceitar. Só o login exige uma leitura, e da própria tabela do auth.
     */
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        return new MeResponse(
                roleOf(authentication),
                authService.activeLoginOf(principal.userId()),
                principal.studentId(),
                principal.teacherId());
    }

    private String roleOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Autenticação sem papel"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(@CookieValue(value = REFRESH_COOKIE, required = false) String refreshTokenCookie,
                                         HttpServletRequest httpRequest,
                                         HttpServletResponse response) {
        if (refreshTokenCookie == null) {
            throw new InvalidRefreshTokenException();
        }
        AuthService.AuthTokens tokens = authService.refresh(refreshTokenCookie);
        setAuthCookies(httpRequest, response, tokens);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(value = REFRESH_COOKIE, required = false) String refreshTokenCookie,
                                        HttpServletRequest httpRequest,
                                        HttpServletResponse response) {
        authService.logout(refreshTokenCookie);
        clearAuthCookies(httpRequest, response);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
    }

    /**
     * 401 e não 403: para o cliente, sessão que não vale mais é
     * indistinguível de sessão expirada, e o caminho de recuperação é o mesmo.
     */
    @ExceptionHandler(InvalidSessionException.class)
    public ProblemDetail handleInvalidSession() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Sessão inválida");
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Refresh token inválido ou reutilizado");
    }

    private void setAuthCookies(HttpServletRequest request, HttpServletResponse response,
                                 AuthService.AuthTokens tokens) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildCookie(request, ACCESS_COOKIE, tokens.accessToken(), properties.jwt().accessTokenTtl())
                        .toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildCookie(request, REFRESH_COOKIE, tokens.refreshToken(), properties.refreshToken().ttl())
                        .toString());
    }

    private void clearAuthCookies(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildCookie(request, ACCESS_COOKIE, "", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildCookie(request, REFRESH_COOKIE, "", Duration.ZERO).toString());
    }

    /**
     * O {@code Secure} sai do transporte da requisição, não de configuração.
     *
     * Sob HTTPS o cookie exige HTTPS; em desenvolvimento, onde tudo é HTTP puro
     * em localhost, o atributo não aparece — e é isso que faz o Safari guardar
     * a sessão, já que ele, ao contrário de Chrome e Firefox, não abre exceção
     * para localhost e simplesmente descarta cookie {@code Secure} recebido por
     * HTTP. A alternativa era uma flag por ambiente, que só funciona enquanto
     * todo mundo lembra de aplicá-la em toda máquina.
     *
     * Em produção quem responde por essa verdade é
     * {@code server.forward-headers-strategy}: o Caddy termina o TLS e repassa
     * HTTP, então sem os forwarded headers isto veria HTTP e o cookie sairia
     * sem {@code Secure}. É o único modo de falha desta abordagem, e tem teste.
     */
    private ResponseCookie buildCookie(HttpServletRequest request, String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
