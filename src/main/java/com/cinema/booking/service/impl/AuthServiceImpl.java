package com.cinema.booking.service.impl;

import com.cinema.booking.dto.auth.AuthResponseDto;
import com.cinema.booking.dto.auth.LoginRequestDto;
import com.cinema.booking.dto.auth.LogoutRequestDto;
import com.cinema.booking.dto.auth.LogoutResponseDto;
import com.cinema.booking.dto.auth.RefreshTokenRequestDto;
import com.cinema.booking.dto.auth.RegisterRequestDto;
import com.cinema.booking.dto.auth.RegisterResponseDto;
import com.cinema.booking.dto.users.UserResponseDto;
import com.cinema.booking.entity.User;
import com.cinema.booking.enums.Role;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.exception.UserAlreadyExistsException;
import com.cinema.booking.security.AuthTokenService;
import com.cinema.booking.security.AuthorizationService;
import com.cinema.booking.repository.UserRepository;
import com.cinema.booking.security.JwtService;
import com.cinema.booking.mapper.UserMapper;
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
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthTokenService authTokenService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final AuthorizationService authorizationService;
    private final UserMapper userMapper;
    private final com.cinema.booking.service.GoogleAuthService googleAuthService;
    private final com.cinema.booking.service.OtpService otpService;

    @Override
    @Transactional
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
    @Transactional
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

        return buildAuthResponse(userDetails, user, authTokenService.createRefreshToken(user));
    }

    @Override
    @Transactional
    public AuthResponseDto refresh(RefreshTokenRequestDto dto) {
        AuthTokenService.TokenRefreshResult result = authTokenService.rotateRefreshToken(dto.getRefreshToken());
        User user = result.user();

        if (user.getStatus() != null && !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("Account is disabled or inactive");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername() != null ? user.getUsername() : user.getEmail());
        return buildAuthResponse(userDetails, user, result.refreshToken());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getCurrentUser() {
        return userMapper.toResponseDto(authorizationService.getCurrentUser());
    }

    @Override
    @Transactional
    public LogoutResponseDto logout(String authorizationHeader, LogoutRequestDto dto) {
        authTokenService.revokeAccessToken(authorizationHeader);
        if (dto != null) {
            authTokenService.revokeRefreshToken(dto.getRefreshToken());
        }

        return LogoutResponseDto.builder()
                .message("Logout successful")
                .build();
    }

    @Override
    @Transactional
    public AuthResponseDto loginWithGoogle(com.cinema.booking.dto.auth.GoogleAuthRequestDto dto) {
        com.cinema.booking.service.GoogleAuthService.GoogleUserInfo info = googleAuthService.verifyToken(dto);
        String email = info.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");
            if (baseUsername.isBlank()) baseUsername = "user";
            String candidateUsername = baseUsername;
            int suffix = 1;
            while (userRepository.existsByUsername(candidateUsername)) {
                candidateUsername = baseUsername + suffix++;
            }
            newUser.setUsername(candidateUsername);
            newUser.setEmail(email);
            newUser.setName(info.name() != null && !info.name().isBlank() ? info.name() : candidateUsername);
            newUser.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            newUser.setRole(Role.USER);
            newUser.setStatus("ACTIVE");
            return userRepository.save(newUser);
        });

        if (user.getStatus() != null && !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("Account is disabled or inactive");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername() != null ? user.getUsername() : user.getEmail());
        return buildAuthResponse(userDetails, user, authTokenService.createRefreshToken(user));
    }

    @Override
    public com.cinema.booking.dto.auth.OtpResponseDto sendOtp(com.cinema.booking.dto.auth.OtpSendRequestDto dto) {
        return otpService.sendOtp(dto);
    }

    @Override
    @Transactional
    public AuthResponseDto verifyOtp(com.cinema.booking.dto.auth.OtpVerifyRequestDto dto) {
        otpService.verifyOtp(dto);
        String email = dto.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");
            if (baseUsername.isBlank()) baseUsername = "user";
            String candidateUsername = baseUsername;
            int suffix = 1;
            while (userRepository.existsByUsername(candidateUsername)) {
                candidateUsername = baseUsername + suffix++;
            }
            newUser.setUsername(candidateUsername);
            newUser.setEmail(email);
            newUser.setName(candidateUsername);
            newUser.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            newUser.setRole(Role.USER);
            newUser.setStatus("ACTIVE");
            return userRepository.save(newUser);
        });

        if (user.getStatus() != null && !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("Account is disabled or inactive");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername() != null ? user.getUsername() : user.getEmail());
        return buildAuthResponse(userDetails, user, authTokenService.createRefreshToken(user));
    }

    private AuthResponseDto buildAuthResponse(UserDetails userDetails, User user, String refreshToken) {
        String jwtToken = jwtService.generateToken(userDetails);
        UserResponseDto userDto = buildUserResponse(user);
        return AuthResponseDto.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .user(userDto)
                .build();
    }

    private UserResponseDto buildUserResponse(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername() != null ? user.getUsername() : user.getEmail())
                .email(user.getEmail())
                .role("ROLE_" + user.getRole().name())
                .build();
    }
}
