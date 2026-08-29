package br.com.tonus.auth.internal.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByFamily(UUID family);

    List<RefreshToken> findByUserIdAndUsedFalse(Long userId);

    /**
     * Atomically consumes the token: only a not-yet-used row is marked used.
     *
     * Returns 1 when this call won the race, 0 when another request already
     * consumed it. Used by {@code AuthService.refresh} instead of the
     * read-check-write sequence, which under concurrency lets two requests
     * with the same token pass the {@code isUsed} check together and defeats
     * the reuse detection that rotation relies on.
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.used = true WHERE t.id = :id AND t.used = false")
    int markUsedIfUnused(@Param("id") Long id);
}
