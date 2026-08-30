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

    private String accountId;
    private String token;
    private String baseUrl;
    private String merchantName;
    private String merchantCity;
    private String currency;
    private String email;
    private boolean mockMode;

}
