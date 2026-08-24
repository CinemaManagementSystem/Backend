package com.cinema.booking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    private static final Map<String, String> TAG_ALIASES = Map.ofEntries(
            Map.entry("auth-controller", "Authentication"),
            Map.entry("location-controller", "Locations"),
            Map.entry("theater-controller", "Theaters"),
            Map.entry("screen-controller", "Screens"),
            Map.entry("seat-controller", "Seats"),
            Map.entry("movie-controller", "Movies"),
            Map.entry("show-controller", "Shows / Showtimes"),
            Map.entry("movie-category-controller", "Movie Categories"),
            Map.entry("product-category-controller", "Product Categories"),
            Map.entry("product-controller", "Products"),
            Map.entry("booking-controller", "Bookings"),
            Map.entry("booking-seat-controller", "Booking Seats"),
            Map.entry("order-controller", "Orders"),
            Map.entry("order-item-controller", "Order Items"),
            Map.entry("payment-controller", "Payments"),
            Map.entry("payment-transaction-controller", "Payment Transactions"),
            Map.entry("user-controller", "User Management")
    );

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cinema Management API")
                        .version("1.0.0")
                        .description("REST API for managing the Cinema Booking System"))
                .tags(List.of(
                        new Tag().name("Authentication").description("Endpoints for User Registration and Authentication"),
                        new Tag().name("Movie Categories").description("Endpoints for managing Movie Categories"),
                        new Tag().name("Movies").description("Endpoints for managing Movies"),
                        new Tag().name("Locations").description("Endpoints for managing Locations"),
                        new Tag().name("Theaters").description("Endpoints for managing Theaters"),
                        new Tag().name("Screens").description("Endpoints for managing Screens"),
                        new Tag().name("Seats").description("Endpoints for managing Seats"),
                        new Tag().name("Shows / Showtimes").description("Endpoints for managing Shows and Showtimes"),
                        new Tag().name("Bookings").description("Endpoints for managing Bookings"),
                        new Tag().name("Booking Seats").description("Endpoints for managing Booking Seats"),
                        new Tag().name("Product Categories").description("Endpoints for managing Product Categories"),
                        new Tag().name("Products").description("Endpoints for managing Products"),
                        new Tag().name("Orders").description("Endpoints for managing Orders"),
                        new Tag().name("Order Items").description("Endpoints for managing Order Items"),
                        new Tag().name("Payments").description("Endpoints for managing Payments"),
                        new Tag().name("Payment Transactions").description("Endpoints for managing Payment Transactions"),
                        new Tag().name("User Management").description("Endpoints for User Administration")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Authorization header using the Bearer scheme.")));
    }

    @Bean
    public OpenApiCustomizer normalizeControllerTags() {
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    if (operation.getTags() == null) {
                        return;
                    }
                    operation.setTags(operation.getTags().stream()
                            .map(tag -> TAG_ALIASES.getOrDefault(tag, tag))
                            .distinct()
                            .toList());
                }));
    }
}
