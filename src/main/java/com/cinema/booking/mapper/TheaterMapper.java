package com.cinema.booking.mapper;

import com.cinema.booking.entity.Theater;
import com.cinema.booking.dto.cinemas.TheaterRequestDto;
import com.cinema.booking.dto.cinemas.TheaterResponseDto;
import org.springframework.stereotype.Component;

@Component
public class TheaterMapper {

    public Theater toEntity(TheaterRequestDto dto) {
        Theater theater = new Theater();
        theater.setAddress(dto.getAddress());
        theater.setName(dto.getName());
        theater.setPhone(dto.getPhone());
        theater.setStatus(dto.getStatus());
        theater.setImageUrl(dto.getImageUrl());
        // TODO: FK fields (location, manager) are resolved in the Service layer
        // using their respective repositories, then set on theater before saving.
        return theater;
    }

    public TheaterResponseDto toResponseDto(Theater theater) {
        TheaterResponseDto dto = new TheaterResponseDto();
        dto.setId(theater.getId());
        dto.setAddress(theater.getAddress());
        dto.setName(theater.getName());
        dto.setPhone(theater.getPhone());
        dto.setStatus(theater.getStatus());
        dto.setImageUrl(theater.getImageUrl());
        dto.setImagePublicId(theater.getImagePublicId());
        dto.setLocationId(theater.getLocation() != null ? theater.getLocation().getId() : null);
        dto.setManagerId(theater.getManager() != null ? theater.getManager().getId() : null);
        return dto;
    }
}