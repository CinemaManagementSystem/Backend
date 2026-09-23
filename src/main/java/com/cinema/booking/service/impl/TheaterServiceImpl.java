package com.cinema.booking.service.impl;

import com.cinema.booking.entity.Theater;
import com.cinema.booking.entity.Location;
import com.cinema.booking.entity.User;
import com.cinema.booking.dto.cinemas.TheaterRequestDto;
import com.cinema.booking.dto.cinemas.TheaterResponseDto;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.mapper.TheaterMapper;
import com.cinema.booking.repository.TheaterRepository;
import com.cinema.booking.repository.LocationRepository;
import com.cinema.booking.repository.UserRepository;
import com.cinema.booking.service.TheaterService;
import com.cinema.booking.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TheaterServiceImpl implements TheaterService {

    @Value("${cloudinary.theater-folder:Cinema_Project/theater}")
    private String theaterImageFolder;

    private final TheaterRepository theaterRepository;
    private final TheaterMapper theaterMapper;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    @Override
    public TheaterResponseDto create(TheaterRequestDto dto) {
        return create(dto, null);
    }

    @Override
    public TheaterResponseDto create(TheaterRequestDto dto, MultipartFile image) {
        Theater theater = theaterMapper.toEntity(dto);
        Location location = locationRepository.findById(dto.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location", dto.getLocationId()));
        theater.setLocation(location);
        if (dto.getManagerId() != null) {
            User manager = userRepository.findById(dto.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", dto.getManagerId()));
            theater.setManager(manager);
        }
        applyImage(theater, dto.getImageUrl(), image, null);

        theater = theaterRepository.save(theater);
        return theaterMapper.toResponseDto(theater);
    }

    @Override
    public TheaterResponseDto update(Long id, TheaterRequestDto dto) {
        return update(id, dto, null);
    }

    @Override
    public TheaterResponseDto update(Long id, TheaterRequestDto dto, MultipartFile image) {
        Theater existing = theaterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Theater", id));
        String previousImagePublicId = existing.getImagePublicId();
        Theater updated = theaterMapper.toEntity(dto);
        updated.setId(existing.getId());
        updated.setLocation(locationRepository.findById(dto.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location", dto.getLocationId())));
        if (dto.getManagerId() != null) {
            updated.setManager(userRepository.findById(dto.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", dto.getManagerId())));
        }
        applyImage(updated, dto.getImageUrl(), image, existing);

        updated = theaterRepository.save(updated);
        deleteReplacedImage(previousImagePublicId, updated.getImagePublicId());
        return theaterMapper.toResponseDto(updated);
    }

    @Override
    public TheaterResponseDto getById(Long id) {
        Theater theater = theaterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Theater", id));
        return theaterMapper.toResponseDto(theater);
    }

    @Override
    public List<TheaterResponseDto> getAll() {
        return theaterRepository.findAll().stream()
                .map(theaterMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        if (!theaterRepository.existsById(id)) {
            throw new ResourceNotFoundException("Theater", id);
        }
        Theater theater = theaterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Theater", id));
        deleteImage(theater);
        theaterRepository.delete(theater);
    }

    private void applyImage(Theater theater, String requestedImageUrl, MultipartFile image, Theater existing) {
        if (image != null && !image.isEmpty()) {
            Map<String, Object> result = cloudinaryService.upload(image, theaterImageFolder);
            theater.setImageUrl((String) result.get("secure_url"));
            theater.setImagePublicId((String) result.get("public_id"));
            return;
        }

        String requestedUrl = requestedImageUrl == null ? "" : requestedImageUrl.trim();
        if (!requestedUrl.isBlank()) {
            theater.setImageUrl(requestedUrl);
            theater.setImagePublicId(existing != null && requestedUrl.equals(existing.getImageUrl())
                    ? existing.getImagePublicId() : null);
            return;
        }

        if (existing != null) {
            theater.setImageUrl(existing.getImageUrl());
            theater.setImagePublicId(existing.getImagePublicId());
        }
    }

    private void deleteReplacedImage(String previousImagePublicId, String currentImagePublicId) {
        if (previousImagePublicId == null || previousImagePublicId.isBlank()
                || previousImagePublicId.equals(currentImagePublicId)) {
            return;
        }
        try {
            cloudinaryService.delete(previousImagePublicId);
        } catch (RuntimeException exception) {
            log.warn("Theater image was replaced, but the previous Cloudinary asset could not be cleaned up: publicId={}",
                    previousImagePublicId, exception);
        }
    }

    private void deleteImage(Theater theater) {
        if (theater == null) {
            return;
        }
        cloudinaryService.delete(theater.getImagePublicId());
        theater.setImageUrl(null);
        theater.setImagePublicId(null);
    }
}