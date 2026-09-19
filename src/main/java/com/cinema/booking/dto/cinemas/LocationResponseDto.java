package com.cinema.booking.dto.cinemas;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class LocationResponseDto {

    private Long id;
    private String address;
    private String city;
    private String googleMapsUrl;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String name;
}