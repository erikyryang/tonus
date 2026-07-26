package br.com.tonus.auth.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Jwt jwt, RefreshToken refreshToken) {

    public record Jwt(String secret, Duration accessTokenTtl) {
    }

    public record RefreshToken(Duration ttl) {
    }
}
