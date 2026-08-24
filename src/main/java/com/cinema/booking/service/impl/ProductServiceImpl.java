package com.cinema.booking.service.impl;

import com.cinema.booking.entity.Product;
import com.cinema.booking.entity.ProductCategory;
import com.cinema.booking.dto.products.ProductRequestDto;
import com.cinema.booking.dto.products.ProductResponseDto;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.mapper.ProductMapper;
import com.cinema.booking.repository.ProductRepository;
import com.cinema.booking.repository.ProductCategoryRepository;
import com.cinema.booking.service.CloudinaryService;
import com.cinema.booking.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductCategoryRepository productCategoryRepository;
    private final CloudinaryService cloudinaryService;

    @Override
    public ProductResponseDto create(ProductRequestDto dto, MultipartFile image) {
        Product product = productMapper.toEntity(dto);

        // Resolve FK
        ProductCategory productCategory = productCategoryRepository.findById(dto.getProductCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", dto.getProductCategoryId()));
        product.setProductCategory(productCategory);

        // Upload image to Cloudinary
        if (image != null && !image.isEmpty()) {
            Map<String, Object> uploadResult = cloudinaryService.upload(image);
            product.setImageUrl((String) uploadResult.get("secure_url"));
            product.setImagePublicId((String) uploadResult.get("public_id"));
        }

        product = productRepository.save(product);
        return productMapper.toResponseDto(product);
    }

    @Override
    public ProductResponseDto update(Long id, ProductRequestDto dto, MultipartFile image) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        Product updated = productMapper.toEntity(dto);
        updated.setId(existing.getId());
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setProductCategory(productCategoryRepository.findById(dto.getProductCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", dto.getProductCategoryId())));

        if (image != null && !image.isEmpty()) {
            // Delete old image from Cloudinary
            cloudinaryService.delete(existing.getImagePublicId());

            // Upload new image
            Map<String, Object> uploadResult = cloudinaryService.upload(image);
            updated.setImageUrl((String) uploadResult.get("secure_url"));
            updated.setImagePublicId((String) uploadResult.get("public_id"));
        } else {
            // Keep existing image
            updated.setImageUrl(existing.getImageUrl());
            updated.setImagePublicId(existing.getImagePublicId());
        }

        updated = productRepository.save(updated);
        return productMapper.toResponseDto(updated);
    }

    @Override
    public ProductResponseDto getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return productMapper.toResponseDto(product);
    }

    @Override
    public List<ProductResponseDto> getAll() {
        return productRepository.findAll().stream()
                .map(productMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        // Delete image from Cloudinary before deleting from DB
        cloudinaryService.delete(product.getImagePublicId());

        productRepository.deleteById(id);
    }
}