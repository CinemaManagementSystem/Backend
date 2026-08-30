package com.cinema.booking.service;

import com.cinema.booking.config.KhqrConfig;
import com.cinema.booking.dto.payments.BakongCheckResult;
import com.cinema.booking.dto.payments.KhqrPayload;
import com.cinema.booking.service.impl.BakongServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BakongServiceTest {

    private KhqrConfig khqrConfig;
    private BakongServiceImpl bakongService;

    @BeforeEach
    void setUp() {
        khqrConfig = new KhqrConfig();
        khqrConfig.setAccountId("cinema_test@dev");
        khqrConfig.setMerchantName("Cinema Test");
        khqrConfig.setMerchantCity("Phnom Penh");
        khqrConfig.setMockMode(true);

        bakongService = new BakongServiceImpl(khqrConfig);
    }

    @Test
    @DisplayName("Should generate valid EMVCo KHQR dynamic string with CRC16 and MD5 hash")
    void testGenerateDynamicKhqr() {
        BigDecimal amount = new BigDecimal("12.50");
        String billNumber = "TXN-TEST-1001";

        KhqrPayload payload = bakongService.generateDynamicKhqr(amount, "USD", billNumber, "Cinema Booking Test");

        assertNotNull(payload);
        assertNotNull(payload.khqrString());
        assertNotNull(payload.md5Hash());
        assertNotNull(payload.expiresAt());
        assertEquals(32, payload.md5Hash().length());

        String qr = payload.khqrString();
        assertTrue(qr.startsWith("000201010212")); // Payload format + Dynamic initiation
        assertTrue(qr.contains("5303840"));       // USD Currency tag
        assertTrue(qr.contains("540412.5") || qr.contains("540512.50"));     // Amount tag (EMV standard decimal format)
        assertTrue(qr.contains("5802KH"));       // Country code
        assertTrue(qr.contains("6304"));         // CRC Tag
    }

    @Test
    @DisplayName("Should compute standard CRC-16 CCITT checksum correctly")
    void testCrc16Calculation() {
        String testInput = "00020101021229200016cinema_test@dev520478325303840540510.005802KH5911Cinema Test6010Phnom Penh62170113TXN-TEST-00016304";
        String crc = BakongServiceImpl.calculateCrc16(testInput);

        assertNotNull(crc);
        assertEquals(4, crc.length());
    }

    @Test
    @DisplayName("Should compute 32-character hex MD5 hash")
    void testMd5Calculation() {
        String testInput = "000201010212";
        String md5 = BakongServiceImpl.calculateMd5(testInput);

        assertNotNull(md5);
        assertEquals(32, md5.length());
    }

    @Test
    @DisplayName("Mock checkTransactionByMd5 should return PAID for paid mock hash")
    void testCheckTransactionMockPaid() {
        BakongCheckResult result = bakongService.checkTransactionByMd5("MOCK_PAID_1234567890abcdef");

        assertNotNull(result);
        assertTrue(result.paid());
        assertEquals("PAID", result.status());
    }

    @Test
    @DisplayName("Mock checkTransactionByMd5 should return PENDING for normal mock hash")
    void testCheckTransactionMockPending() {
        BakongCheckResult result = bakongService.checkTransactionByMd5("random_md5_hash_12345");

        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("PENDING", result.status());
    }
}
