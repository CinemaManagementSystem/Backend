package com.cinema.booking.mapper;

import com.cinema.booking.entity.User;
import com.cinema.booking.dto.users.UserRequestDto;
import com.cinema.booking.dto.users.UserResponseDto;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(UserRequestDto dto) {
        User user = new User();
      
        user.setUsername(dto.getUsername() != null ? dto.getUsername() : dto.getEmail());
        user.setEmail(dto.getEmail());
        user.setName(dto.getName());
        user.setPassword(dto.getPassword());
        user.setRole(dto.getRole());
        user.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");
        // createdAt/updatedAt are managed by JPA lifecycle callbacks (@PrePersist/@PreUpdate)
        return user;
    }

    public UserResponseDto toResponseDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername() != null ? user.getUsername() : user.getEmail())
                .email(user.getEmail())
                .role(user.getRole() != null ? "ROLE_" + user.getRole().name() : "ROLE_USER")
                .name(user.getName())
                .status(user.getStatus())
                .build();
    }
}
