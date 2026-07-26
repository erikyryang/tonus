package br.com.tonus.auth.internal;

import br.com.tonus.auth.internal.domain.RefreshToken;
import br.com.tonus.auth.internal.domain.RefreshTokenRepository;
import br.com.tonus.auth.internal.domain.User;
import br.com.tonus.auth.internal.domain.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        AuthProperties properties,
                        Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.refreshTokenTtl = properties.refreshToken().ttl();
    }

    @Transactional
    public AuthTokens login(String login, String rawPassword) {
        User user = userRepository.findByLogin(login)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPassword()))
                .orElseThrow(InvalidCredentialsException::new);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = issueRefreshToken(user.getId(), UUID.randomUUID());
        return new AuthTokens(accessToken, refreshToken);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthTokens refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (token.isUsed()) {
            revokeFamily(token.getFamily());
            throw new InvalidRefreshTokenException();
        }
        if (token.getExpiresAt().isBefore(clock.instant())) {
            throw new InvalidRefreshTokenException();
        }

        token.markUsed();
        refreshTokenRepository.save(token);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);

        String accessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = issueRefreshToken(user.getId(), token.getFamily());
        return new AuthTokens(accessToken, newRefreshToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .ifPresent(token -> {
                    token.markUsed();
                    refreshTokenRepository.save(token);
                });
    }

    private String issueRefreshToken(Long userId, UUID family) {
        String raw = generateRawToken();
        RefreshToken entity = new RefreshToken(userId, hash(raw), family, clock.instant().plus(refreshTokenTtl));
        refreshTokenRepository.save(entity);
        return raw;
    }

    private void revokeFamily(UUID family) {
        refreshTokenRepository.findByFamily(family).forEach(token -> {
            token.markUsed();
            refreshTokenRepository.save(token);
        });
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record AuthTokens(String accessToken, String refreshToken) {
    }
}
