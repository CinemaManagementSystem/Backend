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
    /** Static bootstrap token loaded from BAKONG_TOKEN. Used until the first
     *  automatic refresh; after that the in-memory cache takes precedence. */
    private String token;
    private String baseUrl;
    private String merchantName;
    private String merchantCity;
    private String currency;
    private String email;
    /** Legacy placeholder only; current Bakong token renewal uses BAKONG_EMAIL with POST /v1/renew_token. */
    private String password;
    private String organization;
    private String project;
    private String verificationCode;
    private boolean mockMode;
    private int expiryMinutes = 5;
    private int recoveryWindowMinutes = 15;
    /** Application-side cap kept below Bakong's documented 100 checks/day. */
    private int dailyRequestLimit = 95;
    private int minVerificationIntervalSeconds = 60;
    private int temporaryErrorBackoffSeconds = 60;
    private int maxTemporaryErrorBackoffSeconds = 900;
    private int pollingBatchSize = 25;

}
