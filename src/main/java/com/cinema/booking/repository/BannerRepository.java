package com.cinema.booking.repository;

import com.cinema.booking.entity.Banner;
import com.cinema.booking.enums.BannerSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long> {

    @Query("SELECT b FROM Banner b WHERE b.section = :section AND b.isActive = true " +
           "AND (b.startDate IS NULL OR b.startDate <= :now) " +
           "AND (b.endDate IS NULL OR b.endDate >= :now) " +
           "ORDER BY b.sortOrder ASC, b.createdAt DESC")
    List<Banner> findActiveBySection(@Param("section") BannerSection section, @Param("now") LocalDateTime now);

    List<Banner> findAllByOrderBySectionAscSortOrderAscCreatedAtDesc();

    List<Banner> findBySectionOrderBySortOrderAscCreatedAtDesc(BannerSection section);

    @Query("SELECT COALESCE(MAX(b.sortOrder), -1) FROM Banner b WHERE b.section = :section")
    Integer findMaxSortOrderBySection(@Param("section") BannerSection section);
}
