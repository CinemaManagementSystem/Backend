package com.cinema.booking.service;

import com.cinema.booking.dto.movies.MovieRequestDto;
import com.cinema.booking.dto.movies.MovieResponseDto;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface MovieService {

    MovieResponseDto create(MovieRequestDto dto);
    MovieResponseDto create(MovieRequestDto dto, MultipartFile poster);
    MovieResponseDto update(Long id, MovieRequestDto dto);
    MovieResponseDto update(Long id, MovieRequestDto dto, MultipartFile poster);
    MovieResponseDto getById(Long id);
    List<MovieResponseDto> getAll();
    void delete(Long id);
}
