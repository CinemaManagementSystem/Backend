package com.cinema.booking.controller;

import com.cinema.booking.dto.payments.PaymentRequestDto;
import com.cinema.booking.entity.Payment;
import com.cinema.booking.entity.PaymentTransaction;
import com.cinema.booking.entity.User;
import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.enums.Role;
import com.cinema.booking.repository.PaymentRepository;
import com.cinema.booking.repository.PaymentTransactionRepository;
import com.cinema.booking.repository.UserRepository;
import com.cinema.booking.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

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

    private User customerUser;
    private User adminUser;
    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        paymentTransactionRepository.deleteAll();
        paymentRepository.deleteAll();

        customerUser = new User();
        customerUser.setName("Payment Customer");
        customerUser.setUsername("payment_customer_" + System.currentTimeMillis());
        customerUser.setEmail("customer_" + System.currentTimeMillis() + "@cinema.com");
        customerUser.setPassword(passwordEncoder.encode("Password123"));
        customerUser.setRole(Role.USER);
        customerUser.setStatus("ACTIVE");
        customerUser = userRepository.save(customerUser);

        adminUser = new User();
        adminUser.setName("Payment Admin");
        adminUser.setUsername("payment_admin_" + System.currentTimeMillis());
        adminUser.setEmail("admin_" + System.currentTimeMillis() + "@cinema.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPassword123"));
        adminUser.setRole(Role.ADMIN);
        adminUser.setStatus("ACTIVE");
        adminUser = userRepository.save(adminUser);

        UserDetails customerDetails = userDetailsService.loadUserByUsername(customerUser.getUsername());
        customerToken = jwtService.generateToken(customerDetails);

        UserDetails adminDetails = userDetailsService.loadUserByUsername(adminUser.getUsername());
        adminToken = jwtService.generateToken(adminDetails);
    }

    @Test
    @DisplayName("Should create KHQR payment and return generated EMV KHQR string & MD5 hash")
    void testCreateKhqrPayment() throws Exception {
        PaymentRequestDto dto = new PaymentRequestDto(
                new BigDecimal("15.50"),
                PaymentMethod.KHQR,
                customerUser.getId(),
                null,
                null
        );

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentMethod", is("KHQR")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.khqrString", notNullValue()))
                .andExpect(jsonPath("$.khqrString", startsWith("000201010212")))
                .andExpect(jsonPath("$.md5Hash", notNullValue()))
                .andExpect(jsonPath("$.expiresAt", notNullValue()))
                .andExpect(jsonPath("$.customerId", is(customerUser.getId().intValue())));
    }

    @Test
    @DisplayName("Should create CASH payment with null KHQR fields")
    void testCreateCashPayment() throws Exception {
        PaymentRequestDto dto = new PaymentRequestDto(
                new BigDecimal("20.00"),
                PaymentMethod.CASH,
                customerUser.getId(),
                null,
                null
        );

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentMethod", is("CASH")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.khqrString", nullValue()))
                .andExpect(jsonPath("$.md5Hash", nullValue()));
    }

    @Test
    @DisplayName("Admin or Staff should successfully confirm a payment")
    void testConfirmPaymentByAdmin() throws Exception {
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("10.00"));
        payment.setPaymentMethod(PaymentMethod.CASH);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCustomer(customerUser);
        payment.setTransactionId("TXN-CASH-TEST101");
        payment = paymentRepository.save(payment);

        mockMvc.perform(post("/api/payments/" + payment.getId() + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAID")))
                .andExpect(jsonPath("$.paidAt", notNullValue()));
    }

    @Test
    @DisplayName("Regular user cannot confirm payments (Forbidden 403)")
    void testConfirmPaymentForbiddenForUser() throws Exception {
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("10.00"));
        payment.setPaymentMethod(PaymentMethod.CASH);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCustomer(customerUser);
        payment.setTransactionId("TXN-CASH-TEST102");
        payment = paymentRepository.save(payment);

        mockMvc.perform(post("/api/payments/" + payment.getId() + "/confirm")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should retrieve payment status by ID")
    void testCheckStatus() throws Exception {
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("18.00"));
        payment.setPaymentMethod(PaymentMethod.KHQR);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCustomer(customerUser);
        payment.setTransactionId("TXN-KHQR-TEST103");
        payment.setMd5Hash("randomhash12345");
        payment = paymentRepository.save(payment);

        mockMvc.perform(get("/api/payments/" + payment.getId() + "/status")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(payment.getId().intValue())))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    @DisplayName("Status check should confirm KHQR payment and append PAID transaction when Bakong reports paid")
    void testCheckStatusConfirmsPaidKhqrPayment() throws Exception {
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("18.00"));
        payment.setPaymentMethod(PaymentMethod.KHQR);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCustomer(customerUser);
        payment.setTransactionId("TXN-KHQR-TEST104");
        payment.setMd5Hash("MOCK_PAID_1234567890abcdef");
        payment = paymentRepository.save(payment);

        mockMvc.perform(get("/api/payments/" + payment.getId() + "/status")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(payment.getId().intValue())))
                .andExpect(jsonPath("$.status", is("PAID")))
                .andExpect(jsonPath("$.paidAt", notNullValue()));

        List<PaymentTransaction> transactions = paymentTransactionRepository.findByPaymentId(payment.getId());
        assertTrue(transactions.stream().anyMatch(transaction -> transaction.getStatus() == PaymentStatus.PAID));
    }
}
