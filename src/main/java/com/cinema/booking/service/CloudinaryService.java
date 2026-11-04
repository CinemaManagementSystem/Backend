package com.cinema.booking.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface CloudinaryService {

    Map<String, Object> upload(MultipartFile file);

    Map<String, Object> upload(MultipartFile file, String folder);

    Map<String, Object> uploadUrl(String imageUrl, String folder);

    void delete(String publicId);
}
