package com.cinema.booking.controller;

import com.cinema.booking.dto.banners.BannerReorderRequestDto;
import com.cinema.booking.entity.Banner;
import com.cinema.booking.entity.User;
import com.cinema.booking.enums.BannerSection;
import com.cinema.booking.enums.Role;
import com.cinema.booking.repository.BannerRepository;
import com.cinema.booking.repository.RefreshTokenRepository;
import com.cinema.booking.repository.RevokedAccessTokenRepository;
import com.cinema.booking.repository.UserRepository;
import com.cinema.booking.security.JwtService;
import com.cinema.booking.security.ratelimit.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminBannerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BannerRepository bannerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RevokedAccessTokenRepository revokedAccessTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private RateLimiterService rateLimiterService;

    private String adminToken;

    @BeforeEach
    void setUp() {
        rateLimiterService.reset();
        refreshTokenRepository.deleteAllInBatch();
        revokedAccessTokenRepository.deleteAllInBatch();
        bannerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        User admin = new User();
        admin.setUsername("banner-admin");
        admin.setEmail("banner-admin@example.test");
        admin.setPassword(passwordEncoder.encode("Password123"));
        admin.setName("Banner Admin");
        admin.setRole(Role.ADMIN);
        admin.setStatus("ACTIVE");
        userRepository.save(admin);

        adminToken = jwtService.generateToken(userDetailsService.loadUserByUsername(admin.getUsername()));
    }

    @Test
    void reorder_withSectionOnlyUpdatesBannersInThatSection() throws Exception {
        Banner first = bannerRepository.save(banner("First home", BannerSection.HOME, 0));
        Banner second = bannerRepository.save(banner("Second home", BannerSection.HOME, 1));
        Banner offer = bannerRepository.save(banner("Offer", BannerSection.OFFER, 7));

        BannerReorderRequestDto request = BannerReorderRequestDto.builder()
                .section(BannerSection.HOME)
                .bannerIds(List.of(second.getId(), first.getId(), offer.getId()))
                .build();

        mockMvc.perform(patch("/api/admin/banners/reorder")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(second.getId().intValue(), first.getId().intValue())))
                .andExpect(jsonPath("$[*].section", contains("HOME", "HOME")));

        assertThat(bannerRepository.findById(second.getId()).orElseThrow().getSortOrder()).isZero();
        assertThat(bannerRepository.findById(first.getId()).orElseThrow().getSortOrder()).isEqualTo(1);
        assertThat(bannerRepository.findById(offer.getId()).orElseThrow().getSortOrder()).isEqualTo(7);
    }

    private Banner banner(String title, BannerSection section, int sortOrder) {
        return Banner.builder()
                .section(section)
                .title(title)
                .imageUrl("https://example.test/" + title.toLowerCase().replace(' ', '-') + ".jpg")
                .sortOrder(sortOrder)
                .isActive(true)
                .build();
    }
}
