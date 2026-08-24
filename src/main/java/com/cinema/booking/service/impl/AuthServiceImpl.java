package com.cinema.booking.service.impl;

import com.cinema.booking.dto.auth.AuthResponseDto;
import com.cinema.booking.dto.auth.LoginRequestDto;
import com.cinema.booking.dto.auth.RegisterRequestDto;
import com.cinema.booking.dto.auth.RegisterResponseDto;
import com.cinema.booking.dto.users.UserResponseDto;
import com.cinema.booking.entity.User;
import com.cinema.booking.enums.Role;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.exception.UserAlreadyExistsException;
import com.cinema.booking.repository.UserRepository;
import com.cinema.booking.security.JwtService;
import com.cinema.booking.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    @Override
    public RegisterResponseDto register(RegisterRequestDto dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new UserAlreadyExistsException("Username already exists: " + dto.getUsername());
        }
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new UserAlreadyExistsException("Email already exists: " + dto.getEmail());
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setName(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(Role.USER);
        user.setStatus("ACTIVE");

        userRepository.save(user);

        UserResponseDto userDto = UserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role("ROLE_" + user.getRole().name())
                .build();

        return RegisterResponseDto.builder()
                .message("Registration successful")
                .user(userDto)
                .build();
    }

    @Override
    public AuthResponseDto login(LoginRequestDto dto) {
        String principal = dto.getPrincipal();
        if (principal.isBlank()) {
            throw new BadCredentialsException("Username or email is required");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        dto.getPassword()
                )
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(principal);
        User user = userRepository.findByUsernameOrEmail(principal, principal)
                .orElseGet(() -> userRepository.findByEmail(principal)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found with identifier: " + principal)));

        if (user.getStatus() != null && !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("Account is disabled or inactive");
        }

        String jwtToken = jwtService.generateToken(userDetails);

        UserResponseDto userDto = UserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername() != null ? user.getUsername() : user.getEmail())
                .email(user.getEmail())
                .role("ROLE_" + user.getRole().name())
                .build();

        return AuthResponseDto.builder()
                .accessToken(jwtToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .user(userDto)
                .build();
    }
}
