package br.com.tonus.auth.internal;

import br.com.tonus.registration.RegistrationFacade;
import br.com.tonus.auth.internal.domain.RefreshToken;
import br.com.tonus.auth.internal.domain.RefreshTokenRepository;
import br.com.tonus.auth.internal.domain.Role;
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
    private final RegistrationFacade registrationFacade;
    private final Clock clock;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        RegistrationFacade registrationFacade,
                        AuthProperties properties,
                        Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.registrationFacade = registrationFacade;
        this.clock = clock;
        this.refreshTokenTtl = properties.refreshToken().ttl();
    }

    @Transactional
    public User createUser(String login, String rawPassword, Role role, Long studentId, Long teacherId) {
        if (userRepository.findByLogin(login).isPresent()) {
            throw new LoginAlreadyTakenException(login);
        }
        requireExistingLink(role, studentId, teacherId);
        return userRepository.save(new User(login, passwordEncoder.encode(rawPassword), role, studentId, teacherId));
    }

    /**
     * Usuário desativado recebe a mesma resposta de senha errada, de propósito:
     * distinguir os dois casos contaria a quem tenta que aquele login existe.
     */
    @Transactional
    public AuthTokens login(String login, String rawPassword) {
        User user = userRepository.findByLogin(login)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPassword()))
                .filter(User::isActive)
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

    /**
     * Revoga o acesso do usuário ligado a um aluno/professor que saiu.
     * Idempotente: a entrega de eventos é at-least-once, então este método roda
     * mais de uma vez para o mesmo desligamento. Marcar como inativo quem já
     * está inativo e invalidar token já invalidado são no-ops.
     */
    @Transactional
    public void revokeAccessOfStudent(Long studentId) {
        userRepository.findByStudentId(studentId).forEach(this::revokeAccessOf);
    }

    @Transactional
    public void revokeAccessOfTeacher(Long teacherId) {
        userRepository.findByTeacherId(teacherId).forEach(this::revokeAccessOf);
    }

    @Transactional
    public User activateUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.activate();
        return userRepository.save(user);
    }

    private void revokeAccessOf(User user) {
        user.deactivate();
        userRepository.save(user);
        // Bloquear o login novo não basta: um refresh token vivo renovaria o
        // acesso sozinho pelos 7 dias seguintes.
        refreshTokenRepository.findByUserIdAndUsedFalse(user.getId()).forEach(token -> {
            token.markUsed();
            refreshTokenRepository.save(token);
        });
    }

    /**
     * O vínculo precisa apontar para alguém que existe e que continua no
     * estúdio. Criar login para quem já saiu produziria um acesso ativo que
     * ninguém revoga: o evento de desativação já passou e não se repete.
     */
    private void requireExistingLink(Role role, Long studentId, Long teacherId) {
        if (role == Role.STUDENT) {
            if (!registrationFacade.studentExists(studentId)) {
                throw new UnknownLinkedPersonException("Aluno inexistente: " + studentId);
            }
            if (!registrationFacade.studentIsActive(studentId)) {
                throw new UnknownLinkedPersonException("Aluno inativo: " + studentId);
            }
        }
        if (role == Role.TEACHER) {
            if (!registrationFacade.teacherExists(teacherId)) {
                throw new UnknownLinkedPersonException("Professor inexistente: " + teacherId);
            }
            if (!registrationFacade.teacherIsActive(teacherId)) {
                throw new UnknownLinkedPersonException("Professor inativo: " + teacherId);
            }
        }
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
