package com.cinema.booking.repository;

import com.cinema.booking.entity.BakongRequestBudget;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface BakongRequestBudgetRepository extends JpaRepository<BakongRequestBudget, LocalDate> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BakongRequestBudget b where b.requestDate = :requestDate")
    Optional<BakongRequestBudget> findByDateForUpdate(@Param("requestDate") LocalDate requestDate);
}
