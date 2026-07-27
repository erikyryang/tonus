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
     * O vínculo com aluno/professor entra como claim porque os endpoints
     * {@code /me/...} vivem em outros módulos, que não podem consultar a tabela
     * users — auth é que depende de registration, não o contrário. O token é o
     * único canal por onde esse id atravessa a fronteira.
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
     * A validação usa o mesmo {@code Clock} da emissão.
     *
     * Sem o {@code .clock(...)} explícito, a JJWT compara {@code exp} contra o
     * relógio do sistema, e o token passa a nascer sob um relógio e a ser
     * conferido sob outro. Em produção os dois coincidem e nada aparece; em
     * teste, com relógio fixo, o token nasce expirado assim que a hora real
     * passa do instante fixo + TTL — o que fazia a suíte depender da hora do
     * dia em que rodava.
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
     * O JJWT desserializa número de claim como Integer quando cabe, então pedir
     * Long direto estoura ClassCastException para ids pequenos — que é
     * justamente o caso de todo estúdio real.
     */
    private Long claimAsLong(Claims payload, String name) {
        Number value = payload.get(name, Number.class);
        return value == null ? null : value.longValue();
    }
}
