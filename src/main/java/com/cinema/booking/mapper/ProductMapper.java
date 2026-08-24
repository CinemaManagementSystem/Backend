package com.cinema.booking.mapper;

import com.cinema.booking.entity.Product;
import com.cinema.booking.dto.products.ProductRequestDto;
import com.cinema.booking.dto.products.ProductResponseDto;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public Product toEntity(ProductRequestDto dto) {
        Product product = new Product();
        product.setIsAvailable(dto.getIsAvailable());
        product.setName(dto.getName());
        product.setPrice(dto.getPrice());
        product.setStockQuantity(dto.getStockQuantity());
        // createdAt/updatedAt are managed by JPA lifecycle callbacks (@PrePersist/@PreUpdate)
        // TODO: FK fields (category) are resolved in the Service layer
        // using their respective repositories, then set on product before saving.
        return product;
    }

    public ProductResponseDto toResponseDto(Product product) {
        ProductResponseDto dto = new ProductResponseDto();
        dto.setId(product.getId());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setImageUrl(product.getImageUrl());
        dto.setImagePublicId(product.getImagePublicId());
        dto.setIsAvailable(product.getIsAvailable());
        dto.setName(product.getName());
        dto.setPrice(product.getPrice());
        dto.setStockQuantity(product.getStockQuantity());
        dto.setUpdatedAt(product.getUpdatedAt());
        dto.setProductCategoryId(product.getProductCategory() != null ? product.getProductCategory().getId() : null);
        return dto;
    }
}