package br.com.tonus.auth.internal;

import br.com.tonus.auth.internal.domain.User;
import br.com.tonus.shared.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private static final String ROLE_CLAIM = "role";
    private static final String STUDENT_CLAIM = "studentId";
    private static final String TEACHER_CLAIM = "teacherId";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(AuthProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = properties.jwt().accessTokenTtl();
        this.clock = clock;
    }

    /**
     * The link to student/teacher goes in as a claim because the
     * {@code /me/...} endpoints live in other modules, which can't query the
     * users table — auth depends on registration, not the other way around.
     * The token is the only channel through which this id crosses the
     * boundary.
     */
    public String generateAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim(ROLE_CLAIM, user.getRole().name())
                .claim(STUDENT_CLAIM, user.getStudentId())
                .claim(TEACHER_CLAIM, user.getTeacherId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * Validation uses the same {@code Clock} as issuance.
     *
     * Without the explicit {@code .clock(...)}, JJWT compares {@code exp}
     * against the system clock, and the token would be born under one clock
     * and checked under another. In production the two coincide and nothing
     * shows up; in tests, with a fixed clock, the token is born expired as
     * soon as the real time passes the fixed instant + TTL — which made the
     * suite depend on the time of day it ran.
     */
    public Optional<Jws<Claims>> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String roleClaim(Jws<Claims> jws) {
        return jws.getPayload().get(ROLE_CLAIM, String.class);
    }

    public AuthenticatedUser principal(Jws<Claims> jws) {
        Claims payload = jws.getPayload();
        return new AuthenticatedUser(
                Long.valueOf(payload.getSubject()),
                claimAsLong(payload, STUDENT_CLAIM),
                claimAsLong(payload, TEACHER_CLAIM));
    }

    /**
     * JJWT deserializes claim numbers as Integer when they fit, so asking for
     * Long directly throws ClassCastException for small ids — which is
     * exactly the case for every real studio.
     */
    private Long claimAsLong(Claims payload, String name) {
        Number value = payload.get(name, Number.class);
        return value == null ? null : value.longValue();
    }
}
