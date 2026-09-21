package com.cinema.booking.repository;

import com.cinema.booking.entity.UserMembership;
import com.cinema.booking.enums.MembershipStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserMembershipRepository extends JpaRepository<UserMembership, UUID> {
    List<UserMembership> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Optional<UserMembership> findFirstByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId, MembershipStatus status);
    boolean existsByCustomerIdAndStatus(Long customerId, MembershipStatus status);
    List<UserMembership> findByStatusAndExpiresAtBefore(MembershipStatus status, LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select um from UserMembership um where um.id = :id")
    Optional<UserMembership> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select um from UserMembership um where um.customer.id = :customerId and um.status = :status order by um.createdAt desc")
    List<UserMembership> findByCustomerIdAndStatusForUpdate(
            @Param("customerId") Long customerId,
            @Param("status") MembershipStatus status
    );
}
