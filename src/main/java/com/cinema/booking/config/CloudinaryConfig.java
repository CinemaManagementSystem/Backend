package com.cinema.booking.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "cloudinary")
public class CloudinaryConfig {

    private String url;

    private String cloudName;

    private String apiKey;

    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        if (hasRealText(url)) {
            return new Cloudinary(url.trim());
        }
        if (hasRealText(cloudName) && hasRealText(apiKey) && hasRealText(apiSecret)) {
            return new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName.trim(),
                    "api_key", apiKey.trim(),
                    "api_secret", apiSecret.trim(),
                    "secure", true
            ));
        }
        throw new IllegalStateException(
                "Cloudinary is not configured. Set CLOUDINARY_URL=cloudinary://<api_key>:<api_secret>@<cloud_name> "
                        + "or set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, and CLOUDINARY_API_SECRET."
        );
    }

    private boolean hasRealText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return !normalized.contains("your_cloudinary")
                && !normalized.contains("your_")
                && !normalized.contains("<your_")
                && !normalized.contains("your-")
                && !normalized.contains("placeholder");
    }
}
