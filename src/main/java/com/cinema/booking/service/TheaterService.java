package com.cinema.booking.service;

import com.cinema.booking.dto.cinemas.TheaterRequestDto;
import com.cinema.booking.dto.cinemas.TheaterResponseDto;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface TheaterService {

    TheaterResponseDto create(TheaterRequestDto dto);
    TheaterResponseDto create(TheaterRequestDto dto, MultipartFile image);
    TheaterResponseDto update(Long id, TheaterRequestDto dto);
    TheaterResponseDto update(Long id, TheaterRequestDto dto, MultipartFile image);
    TheaterResponseDto getById(Long id);
    List<TheaterResponseDto> getAll();
    void delete(Long id);
}