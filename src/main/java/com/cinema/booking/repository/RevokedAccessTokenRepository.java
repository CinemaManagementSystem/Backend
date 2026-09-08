package com.cinema.booking.repository;

import com.cinema.booking.entity.RevokedAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, Long> {
    boolean existsByTokenIdAndExpiresAtAfter(String tokenId, LocalDateTime now);

    boolean existsByTokenId(String tokenId);

    void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
