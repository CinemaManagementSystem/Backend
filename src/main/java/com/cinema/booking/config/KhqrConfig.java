package com.cinema.booking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "bakong")
public class KhqrConfig {

    private String accountId = "cinema_official@dev";
    private String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJkYXRhIjp7ImlkIjoiNGFkM2MwNzJjNDE4NDIzMiJ9LCJpYXQiOjE3ODc4MDk3NDQsImV4cCI6MTc5NTU4NTc0NH0.UcrmHeh0C1xWBeYEd5KvONxmnydKU8yE904wUtTx2kY";
    private String baseUrl = "https://api-bakong.nbc.gov.kh";
    private String merchantName = "Movie System Ticket";
    private String merchantCity = "Phnom Penh";
    private String currency = "USD";
    private String email = "rithrith8442@gmail.com";

}
