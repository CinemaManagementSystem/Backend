package com.cinema.booking.repository;

import com.cinema.booking.entity.PromotionUsage;
import com.cinema.booking.enums.PromotionUsageStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, Long> {

    Optional<PromotionUsage> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pu from PromotionUsage pu where pu.order.id = :orderId")
    Optional<PromotionUsage> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    long countByPromotionIdAndUserIdAndStatusIn(
            Long promotionId,
            Long userId,
            List<PromotionUsageStatus> statuses
    );

    List<PromotionUsage> findByPromotionIdOrderByCreatedAtDesc(Long promotionId);

    List<PromotionUsage> findByUserIdOrderByCreatedAtDesc(Long userId);
}
