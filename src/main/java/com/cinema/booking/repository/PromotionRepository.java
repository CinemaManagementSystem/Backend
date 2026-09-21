package com.cinema.booking.repository;

import com.cinema.booking.entity.Promotion;
import com.cinema.booking.enums.PromotionScope;
import com.cinema.booking.enums.PromotionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByCodeIgnoreCase(String code);

    List<Promotion> findByStatusOrderByStartDateDesc(PromotionStatus status);

    @Query("""
            select p from Promotion p
            where p.code is null
              and p.status = com.cinema.booking.enums.PromotionStatus.ACTIVE
              and p.startDate <= :now
              and p.endDate >= :now
            order by p.discountValue desc, p.id asc
            """)
    List<Promotion> findActiveAutomaticPromotions(@Param("now") LocalDateTime now);

    @Query("""
            select p from Promotion p
            where (:status is null or p.status = :status)
              and (:scope is null or p.scope = :scope)
              and (:code is null or lower(p.code) like lower(concat('%', :code, '%')))
            order by p.startDate desc, p.id desc
            """)
    List<Promotion> search(
            @Param("status") PromotionStatus status,
            @Param("scope") PromotionScope scope,
            @Param("code") String code
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where p.id = :id")
    Optional<Promotion> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Promotion p
            set p.usedCount = p.usedCount + 1
            where p.id = :id
              and (p.usageLimitTotal is null or p.usedCount < p.usageLimitTotal)
            """)
    int reserveUsageSlot(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Promotion p
            set p.usedCount = p.usedCount - 1
            where p.id = :id
              and p.usedCount > 0
            """)
    int releaseUsageSlot(@Param("id") Long id);
}
