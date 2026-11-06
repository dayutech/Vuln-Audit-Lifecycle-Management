package com.vulnaudit.repository;

import com.vulnaudit.entity.RememberMeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** remember-me 令牌仓储（t124） */
@Repository
public interface RememberMeTokenRepository extends JpaRepository<RememberMeToken, UUID> {

    List<RememberMeToken> findBySeriesId(String seriesId);

    Optional<RememberMeToken> findByTokenHash(String tokenHash);

    List<RememberMeToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    long countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, LocalDateTime now);
}
