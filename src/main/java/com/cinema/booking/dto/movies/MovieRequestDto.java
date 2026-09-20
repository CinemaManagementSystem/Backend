package com.cinema.booking.dto.movies;

import jakarta.validation.constraints.*;

import lombok.Data;
import java.time.LocalDate;

@Data
public class MovieRequestDto {

    private Long categoryId;
    private String description;
    @NotNull @Min(1)
    private Integer durationMinutes;
    @NotBlank
    private String genre;
    @NotBlank
    private String language;
    private String posterUrl;
    @NotNull
    private LocalDate releaseDate;
    @NotBlank
    private String status;
    @NotBlank
    private String title;
}
