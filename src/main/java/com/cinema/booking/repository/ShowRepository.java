package com.cinema.booking.repository;

import com.cinema.booking.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

    import java.time.LocalDateTime;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long> {
    boolean existsByMovieIdAndScreenIdAndStartTime(Long movieId, Long screenId, LocalDateTime startTime);
}
