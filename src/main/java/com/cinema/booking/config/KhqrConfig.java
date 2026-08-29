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
    private String token = "";
    private String baseUrl = "https://api-bakong.nbc.gov.kh";
    private String merchantName = "Sothearith Kim";
    private String merchantCity = "Phnom Penh";
    private String currency = "USD";
    private String email = "";
    private boolean mockMode = false;
}
