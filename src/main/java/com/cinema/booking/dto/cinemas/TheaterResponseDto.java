package com.cinema.booking.dto.cinemas;

import lombok.Data;

@Data
public class TheaterResponseDto {

    private Long id;

    private String address;
    private String name;
    private String phone;
    private String status;
    private Long locationId;
    private Long managerId;
    private String imageUrl;

    private String imagePublicId;
}