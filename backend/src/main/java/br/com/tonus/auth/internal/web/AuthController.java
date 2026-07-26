package br.com.tonus.auth.internal.web;

import br.com.tonus.auth.internal.AuthProperties;
import br.com.tonus.auth.internal.AuthService;
import br.com.tonus.auth.internal.InvalidCredentialsException;
import br.com.tonus.auth.internal.InvalidRefreshTokenException;
import br.com.tonus.auth.internal.LoginRateLimiter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;
    private final AuthProperties properties;

    public AuthController(AuthService authService, LoginRateLimiter loginRateLimiter, AuthProperties properties) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
        this.properties = properties;
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        if (!loginRateLimiter.tryConsume(request.login())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        AuthService.AuthTokens tokens = authService.login(request.login(), request.password());
        setAuthCookies(response, tokens);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(@CookieValue(value = REFRESH_COOKIE, required = false) String refreshTokenCookie,
                                         HttpServletResponse response) {
        if (refreshTokenCookie == null) {
            throw new InvalidRefreshTokenException();
        }
        AuthService.AuthTokens tokens = authService.refresh(refreshTokenCookie);
        setAuthCookies(response, tokens);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(value = REFRESH_COOKIE, required = false) String refreshTokenCookie,
                                        HttpServletResponse response) {
        authService.logout(refreshTokenCookie);
        clearAuthCookies(response);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Refresh token inválido ou reutilizado");
    }

    private void setAuthCookies(HttpServletResponse response, AuthService.AuthTokens tokens) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildCookie(ACCESS_COOKIE, tokens.accessToken(), properties.jwt().accessTokenTtl()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildCookie(REFRESH_COOKIE, tokens.refreshToken(), properties.refreshToken().ttl()).toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(ACCESS_COOKIE, "", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(REFRESH_COOKIE, "", Duration.ZERO).toString());
    }

    private ResponseCookie buildCookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
