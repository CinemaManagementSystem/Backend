package com.cinema.booking.service.impl;

import com.cinema.booking.dto.users.UserRequestDto;
import com.cinema.booking.dto.users.UserResponseDto;
import com.cinema.booking.entity.User;
import com.cinema.booking.enums.Role;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.exception.UserAlreadyExistsException;
import com.cinema.booking.mapper.UserMapper;
import com.cinema.booking.repository.UserRepository;
import com.cinema.booking.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponseDto create(UserRequestDto dto) {
        String username = dto.getUsername() != null ? dto.getUsername() : dto.getEmail();
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username already exists: " + username);
        }
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new UserAlreadyExistsException("Email already exists: " + dto.getEmail());
        }

        User user = userMapper.toEntity(dto);
        user.setUsername(username);
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        user = userRepository.save(user);
        return userMapper.toResponseDto(user);
    }

    @Override
    public UserResponseDto update(Long id, UserRequestDto dto) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        if (existing.getRole() == Role.ADMIN && dto.getRole() != Role.ADMIN) {
            throw new IllegalStateException("An ADMIN user cannot be changed to USER or STAFF");
        }

        boolean protectedRole = existing.getRole() == Role.USER || existing.getRole() == Role.STAFF;
        if (protectedRole && !Objects.equals(existing.getStatus(), dto.getStatus())) {
            throw new IllegalStateException("USER and STAFF account statuses cannot be changed");
        }

        User updated = userMapper.toEntity(dto);
        updated.setId(existing.getId());
        updated.setCreatedAt(existing.getCreatedAt());

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            if (!dto.getPassword().startsWith("$2a$") && !dto.getPassword().startsWith("$2b$")) {
                updated.setPassword(passwordEncoder.encode(dto.getPassword()));
            } else {
                updated.setPassword(dto.getPassword());
            }
        } else {
            updated.setPassword(existing.getPassword());
        }

        updated = userRepository.save(updated);
        return userMapper.toResponseDto(updated);
    }

    @Override
    public UserResponseDto getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return userMapper.toResponseDto(user);
    }

    @Override
    public List<UserResponseDto> getAll() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User", id);
        }
        userRepository.deleteById(id);
    }
}
