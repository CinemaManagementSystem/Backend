package com.cinema.booking.dto.products;

import jakarta.validation.constraints.*;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductRequestDto {

    @NotBlank
    private String name;

    @NotNull
    private Boolean isAvailable;

    @NotNull @Positive
    private BigDecimal price;

    @NotNull @Min(0)
    private Integer stockQuantity;

    @NotNull
    private Long productCategoryId;
}