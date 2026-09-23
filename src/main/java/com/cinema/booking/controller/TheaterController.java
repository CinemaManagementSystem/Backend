package com.cinema.booking.controller;

import com.cinema.booking.dto.cinemas.TheaterRequestDto;
import com.cinema.booking.dto.cinemas.TheaterResponseDto;
import com.cinema.booking.service.TheaterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/theaters")
@RequiredArgsConstructor
public class TheaterController {

    private final TheaterService theaterService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TheaterResponseDto> create(@Valid @RequestBody TheaterRequestDto dto) {
        return new ResponseEntity<>(theaterService.create(dto), HttpStatus.CREATED);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TheaterResponseDto> createWithImage(
            @Valid @ModelAttribute TheaterRequestDto dto,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        return new ResponseEntity<>(theaterService.create(dto, image), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TheaterResponseDto> update(@PathVariable Long id, @Valid @RequestBody TheaterRequestDto dto) {
        return ResponseEntity.ok(theaterService.update(id, dto));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TheaterResponseDto> updateWithImage(
            @PathVariable Long id,
            @Valid @ModelAttribute TheaterRequestDto dto,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(theaterService.update(id, dto, image));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TheaterResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(theaterService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<TheaterResponseDto>> getAll() {
        return ResponseEntity.ok(theaterService.getAll());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        theaterService.delete(id);
        return ResponseEntity.noContent().build();
    }
}