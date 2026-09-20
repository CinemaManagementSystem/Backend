package com.cinema.booking.service.impl;

import com.cinema.booking.entity.Movie;
import com.cinema.booking.entity.MovieCategory;
import com.cinema.booking.dto.movies.MovieRequestDto;
import com.cinema.booking.dto.movies.MovieResponseDto;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.mapper.MovieMapper;
import com.cinema.booking.repository.MovieRepository;
import com.cinema.booking.repository.MovieCategoryRepository;
import com.cinema.booking.service.CloudinaryService;
import com.cinema.booking.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {

    private final MovieRepository movieRepository;
    private final MovieMapper movieMapper;
    private final MovieCategoryRepository categoryRepository;
    private final CloudinaryService cloudinaryService;

    private static final String MOVIE_IMAGE_FOLDER = "Cinema_Project/movie";

    @Override
    public MovieResponseDto create(MovieRequestDto dto) {
        return create(dto, null);
    }

    @Override
    public MovieResponseDto create(MovieRequestDto dto, MultipartFile poster) {
        Movie movie = movieMapper.toEntity(dto);
        if (dto.getCategoryId() != null) {
            MovieCategory category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", dto.getCategoryId()));
            movie.setCategory(category);
        }
        applyPoster(movie, dto.getPosterUrl(), poster, null);
        movie = movieRepository.save(movie);
        return movieMapper.toResponseDto(movie);
    }

    @Override
    public MovieResponseDto update(Long id, MovieRequestDto dto) {
        return update(id, dto, null);
    }

    @Override
    public MovieResponseDto update(Long id, MovieRequestDto dto, MultipartFile poster) {
        Movie existing = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", id));
        Movie updated = movieMapper.toEntity(dto);
        updated.setId(existing.getId());
        if (dto.getCategoryId() != null) {
            updated.setCategory(categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", dto.getCategoryId())));
        } else {
            updated.setCategory(existing.getCategory());
        }
        applyPoster(updated, dto.getPosterUrl(), poster, existing);
        updated = movieRepository.save(updated);
        return movieMapper.toResponseDto(updated);
    }

    @Override
    public MovieResponseDto getById(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", id));
        return movieMapper.toResponseDto(movie);
    }

    @Override
    public List<MovieResponseDto> getAll() {
        return movieRepository.findAll().stream()
                .map(movieMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", id));
        cloudinaryService.delete(movie.getPosterPublicId());
        movieRepository.deleteById(id);
    }

    private void applyPoster(Movie movie, String posterUrl, MultipartFile poster, Movie existing) {
        if (poster != null && !poster.isEmpty()) {
            deletePreviousPoster(existing);
            Map<String, Object> result = cloudinaryService.upload(poster, MOVIE_IMAGE_FOLDER);
            movie.setPosterUrl((String) result.get("secure_url"));
            movie.setPosterPublicId((String) result.get("public_id"));
            return;
        }

        String requestedUrl = posterUrl == null ? "" : posterUrl.trim();
        if (existing != null && requestedUrl.isBlank()) {
            movie.setPosterUrl(existing.getPosterUrl());
            movie.setPosterPublicId(existing.getPosterPublicId());
            return;
        }
        if (existing != null && Objects.equals(requestedUrl, existing.getPosterUrl())) {
            movie.setPosterUrl(existing.getPosterUrl());
            movie.setPosterPublicId(existing.getPosterPublicId());
            return;
        }
        if (!requestedUrl.isBlank()) {
            deletePreviousPoster(existing);
            Map<String, Object> result = cloudinaryService.uploadUrl(requestedUrl, MOVIE_IMAGE_FOLDER);
            movie.setPosterUrl((String) result.get("secure_url"));
            movie.setPosterPublicId((String) result.get("public_id"));
            return;
        }
        throw new IllegalArgumentException("Provide a poster URL or choose an image file");
    }

    private void deletePreviousPoster(Movie existing) {
        if (existing != null && existing.getPosterPublicId() != null) {
            cloudinaryService.delete(existing.getPosterPublicId());
        }
    }
}
