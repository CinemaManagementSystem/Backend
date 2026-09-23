package com.cinema.booking.dto.cinemas;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class TheaterRequestDto {

    @NotBlank

    private String address;
    @NotBlank
    private String name;
    @NotBlank
    private String phone;
    @NotBlank
    private String status;
    @NotNull
    private Long locationId;
    @NotNull
    private Long managerId;

    private String imageUrl;
}