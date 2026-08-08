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
     * Who is logged in. Exists because the session lives in an httpOnly
     * cookie: the front doesn't read the token and shouldn't decode JWT — it
     * asks here.
     *
     * Role and link come from the token, not the database: it's the token
     * that authorizes the other calls, and the screen has to reflect exactly
     * what the API will accept. Only the login requires a read, and from
     * auth's own table.
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
     * 401, not 403: to the client, a session that's no longer valid is
     * indistinguishable from an expired session, and the recovery path is the
     * same.
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
     * The {@code Secure} attribute comes from the request's transport, not
     * from configuration.
     *
     * Under HTTPS the cookie requires HTTPS; in development, where
     * everything is plain HTTP on localhost, the attribute doesn't appear —
     * and that's what makes Safari keep the session, since, unlike Chrome and
     * Firefox, it makes no exception for localhost and simply discards a
     * {@code Secure} cookie received over HTTP. The alternative was a
     * per-environment flag, which only works as long as everyone remembers
     * to apply it on every machine.
     *
     * In production what backs this truth is
     * {@code server.forward-headers-strategy}: Caddy terminates TLS and
     * forwards HTTP, so without the forwarded headers this would see HTTP and
     * the cookie would go out without {@code Secure}. It's the only failure
     * mode of this approach, and it has a test.
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
