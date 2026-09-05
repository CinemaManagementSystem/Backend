package com.cinema.booking.dto.cinemas;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LocationRequestDto {

    @NotBlank

    private String address;
    @NotBlank
    private String city;
    private String googleMapsUrl;
    @NotNull
    private BigDecimal latitude;
    
    @NotNull
    private BigDecimal longitude;
    @NotBlank
    private String name;
}