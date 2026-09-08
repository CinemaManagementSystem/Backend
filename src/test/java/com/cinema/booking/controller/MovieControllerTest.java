package com.cinema.booking.controller;

import com.cinema.booking.dto.movies.MovieRequestDto;
import com.cinema.booking.entity.Movie;
import com.cinema.booking.entity.User;
import com.cinema.booking.enums.Role;
import com.cinema.booking.repository.MovieRepository;
import com.cinema.booking.repository.UserRepository;
import com.cinema.booking.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class MovieControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private User testAdmin;
    private String userToken;
    private String adminToken;
    private Movie testMovie;

    @BeforeEach
    void setUp() {
        movieRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        testUser = new User();
        testUser.setUsername("regularuser");
        testUser.setEmail("regular@example.com");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser.setName("Regular User");
        testUser.setRole(Role.USER);
        testUser.setStatus("ACTIVE");
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
        userRepository.save(testUser);

        testAdmin = new User();
        testAdmin.setUsername("adminuser");
        testAdmin.setEmail("admin@example.com");
        testAdmin.setPassword(passwordEncoder.encode("admin123"));
        testAdmin.setName("Admin User");
        testAdmin.setRole(Role.ADMIN);
        testAdmin.setStatus("ACTIVE");
        testAdmin.setCreatedAt(LocalDateTime.now());
        testAdmin.setUpdatedAt(LocalDateTime.now());
        userRepository.save(testAdmin);

        UserDetails userDetails = userDetailsService.loadUserByUsername("regularuser");
        userToken = jwtService.generateToken(userDetails);

        UserDetails adminDetails = userDetailsService.loadUserByUsername("adminuser");
        adminToken = jwtService.generateToken(adminDetails);

        testMovie = new Movie();
        testMovie.setTitle("Inception");
        testMovie.setDescription("A mind-bending thriller");
        testMovie.setDurationMinutes(148);
        testMovie.setGenre("Sci-Fi");
        testMovie.setLanguage("English");
        testMovie.setReleaseDate(LocalDate.of(2010, 7, 16));
        testMovie.setPosterUrl("https://example.com/poster.jpg");
        testMovie.setStatus("NOW_SHOWING");
        testMovie = movieRepository.save(testMovie);
    }

    @Test
    void getById_ExistingId_Returns200() throws Exception {
        mockMvc.perform(get("/api/movies/{id}", testMovie.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testMovie.getId()))
                .andExpect(jsonPath("$.title").value("Inception"))
                .andExpect(jsonPath("$.durationMinutes").value(148));
    }

    @Test
    void getById_NonExistentId_Returns404_ErrorResponse() throws Exception {
        long nonExistentId = 999999L;
        mockMvc.perform(get("/api/movies/{id}", nonExistentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Movie not found with value: " + nonExistentId))
                .andExpect(jsonPath("$.path").value("/api/movies/" + nonExistentId));
    }

    @Test
    void create_ValidData_Admin_Returns201() throws Exception {
        MovieRequestDto dto = new MovieRequestDto();
        dto.setTitle("Interstellar");
        dto.setDescription("Space exploration drama");
        dto.setDurationMinutes(169);
        dto.setGenre("Sci-Fi");
        dto.setLanguage("English");
        dto.setReleaseDate(LocalDate.of(2014, 11, 7));
        dto.setPosterUrl("https://example.com/interstellar.jpg");
        dto.setStatus("NOW_SHOWING");

        mockMvc.perform(post("/api/movies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title").value("Interstellar"))
                .andExpect(jsonPath("$.durationMinutes").value(169));
    }

    @Test
    void create_InvalidData_Admin_Returns400_ErrorResponse() throws Exception {
        MovieRequestDto dto = new MovieRequestDto();
        // Title and other required fields are missing

        mockMvc.perform(post("/api/movies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("title")))
                .andExpect(jsonPath("$.path").value("/api/movies"));
    }

    @Test
    void update_ExistingId_Admin_Returns200() throws Exception {
        MovieRequestDto dto = new MovieRequestDto();
        dto.setTitle("Inception - Remastered");
        dto.setDescription("Updated description");
        dto.setDurationMinutes(150);
        dto.setGenre("Sci-Fi");
        dto.setLanguage("English");
        dto.setReleaseDate(LocalDate.of(2010, 7, 16));
        dto.setPosterUrl("https://example.com/inception2.jpg");
        dto.setStatus("NOW_SHOWING");

        mockMvc.perform(put("/api/movies/{id}", testMovie.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testMovie.getId()))
                .andExpect(jsonPath("$.title").value("Inception - Remastered"))
                .andExpect(jsonPath("$.durationMinutes").value(150));
    }

    @Test
    void update_NonExistentId_Admin_Returns404_ErrorResponse() throws Exception {
        long nonExistentId = 888888L;
        MovieRequestDto dto = new MovieRequestDto();
        dto.setTitle("Nonexistent");
        dto.setDescription("Does not exist");
        dto.setDurationMinutes(120);
        dto.setGenre("Action");
        dto.setLanguage("English");
        dto.setReleaseDate(LocalDate.of(2025, 1, 1));
        dto.setPosterUrl("https://example.com/poster.jpg");
        dto.setStatus("COMING_SOON");

        mockMvc.perform(put("/api/movies/{id}", nonExistentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Movie not found with value: " + nonExistentId))
                .andExpect(jsonPath("$.path").value("/api/movies/" + nonExistentId));
    }

    @Test
    void delete_ExistingId_Admin_Returns204() throws Exception {
        mockMvc.perform(delete("/api/movies/{id}", testMovie.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_NonExistentId_Admin_Returns404_ErrorResponse() throws Exception {
        long nonExistentId = 777777L;
        mockMvc.perform(delete("/api/movies/{id}", nonExistentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Movie not found with value: " + nonExistentId))
                .andExpect(jsonPath("$.path").value("/api/movies/" + nonExistentId));
    }

    @Test
    void publicReadEndpoint_WithoutAuth_Returns200() throws Exception {
        mockMvc.perform(get("/api/movies/{id}", testMovie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testMovie.getId()))
                .andExpect(jsonPath("$.title").value(testMovie.getTitle()));
    }

    @Test
    void adminEndpoint_AsNormalUser_Returns403_ErrorResponse() throws Exception {
        MovieRequestDto dto = new MovieRequestDto();
        dto.setTitle("Unauthorized Movie");
        dto.setDescription("Testing authorization");
        dto.setDurationMinutes(100);
        dto.setGenre("Drama");
        dto.setLanguage("English");
        dto.setReleaseDate(LocalDate.of(2025, 1, 1));
        dto.setPosterUrl("https://example.com/poster.jpg");
        dto.setStatus("COMING_SOON");

        mockMvc.perform(post("/api/movies")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/api/movies"));
    }
}
