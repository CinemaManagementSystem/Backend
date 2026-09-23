package com.cinema.booking.controller;

import com.cinema.booking.dto.movies.MovieRequestDto;
import com.cinema.booking.dto.movies.MovieResponseDto;
import com.cinema.booking.service.MovieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MovieResponseDto> create(@Valid @RequestBody MovieRequestDto dto) {
        return new ResponseEntity<>(movieService.create(dto), HttpStatus.CREATED);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MovieResponseDto> createMultipart(
            @Valid @ModelAttribute MovieRequestDto dto,
            @RequestParam(value = "poster", required = false) MultipartFile poster) {
        return new ResponseEntity<>(movieService.create(dto, poster), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MovieResponseDto> update(@PathVariable Long id, @Valid @RequestBody MovieRequestDto dto) {
        return ResponseEntity.ok(movieService.update(id, dto));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MovieResponseDto> updateMultipart(
            @PathVariable Long id,
            @Valid @ModelAttribute MovieRequestDto dto,
            @RequestParam(value = "poster", required = false) MultipartFile poster) {
        return ResponseEntity.ok(movieService.update(id, dto, poster));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MovieResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(movieService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<MovieResponseDto>> getAll() {
        return ResponseEntity.ok(movieService.getAll());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        movieService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
