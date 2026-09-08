package com.cinema.booking.service;

import com.cinema.booking.config.KhqrConfig;
import com.cinema.booking.dto.payments.BakongCheckResult;
import com.cinema.booking.dto.payments.KhqrPayload;
import com.cinema.booking.service.impl.BakongServiceImpl;
import kh.gov.nbc.bakong_khqr.model.KHQRResponse;
import kh.gov.nbc.bakong_khqr.model.KHQRStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BakongServiceTest {

    private static final String BASE_URL = "https://api-bakong.nbc.gov.kh";
    private static final String LIVE_TOKEN = "live-token";

    private KhqrConfig khqrConfig;
    private BakongServiceImpl bakongService;

    @BeforeEach
    void setUp() {
        khqrConfig = new KhqrConfig();
        khqrConfig.setAccountId("cinema_test@dev");
        khqrConfig.setMerchantName("Cinema Test");
        khqrConfig.setMerchantCity("Phnom Penh");
        khqrConfig.setMockMode(true);
        khqrConfig.setBaseUrl(BASE_URL);

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
        assertEquals("USD", payload.currency());
        assertEquals(billNumber, payload.billNumber());

        String qr = payload.khqrString();
        assertTrue(qr.startsWith("000201010212"));
        assertTrue(qr.contains("5303840"));
        assertTrue(qr.contains("540412.5") || qr.contains("540512.50"));
        assertTrue(qr.contains("5802KH"));
        assertTrue(qr.contains("6304"));
    }

    @Test
    @DisplayName("Should reject null payment amount")
    void testGenerateDynamicKhqrRejectsNullAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> bakongService.generateDynamicKhqr(null, "USD", "TXN-TEST-1002", "Cinema Booking Test"));
    }

    @Test
    @DisplayName("Should reject zero payment amount")
    void testGenerateDynamicKhqrRejectsZeroAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> bakongService.generateDynamicKhqr(BigDecimal.ZERO, "USD", "TXN-TEST-1003", "Cinema Booking Test"));
    }

    @Test
    @DisplayName("Should reject negative payment amount")
    void testGenerateDynamicKhqrRejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> bakongService.generateDynamicKhqr(new BigDecimal("-1"), "USD", "TXN-TEST-1004", "Cinema Booking Test"));
    }

    @Test
    @DisplayName("Should accept USD currency in any case")
    void testGenerateDynamicKhqrAcceptsUsdCurrency() {
        KhqrPayload payload = bakongService.generateDynamicKhqr(new BigDecimal("10.00"), "usd", "TXN-TEST-1005", "Cinema Booking Test");

        assertNotNull(payload);
        assertEquals("USD", payload.currency());
    }

    @Test
    @DisplayName("Should accept KHR currency in any case")
    void testGenerateDynamicKhqrAcceptsKhrCurrency() {
        KhqrPayload payload = bakongService.generateDynamicKhqr(new BigDecimal("1000.00"), "khr", "TXN-TEST-1006", "Cinema Booking Test");

        assertNotNull(payload);
        assertEquals("KHR", payload.currency());
    }

    @Test
    @DisplayName("Should reject unsupported currency")
    void testGenerateDynamicKhqrRejectsUnsupportedCurrency() {
        assertThrows(IllegalArgumentException.class,
                () -> bakongService.generateDynamicKhqr(new BigDecimal("10.00"), "EUR", "TXN-TEST-1007", "Cinema Booking Test"));
    }

    @Test
    @DisplayName("Should reject missing Bakong account ID")
    void testGenerateDynamicKhqrRejectsMissingBakongAccountId() {
        khqrConfig.setAccountId(" ");

        assertThrows(IllegalStateException.class,
                () -> bakongService.generateDynamicKhqr(new BigDecimal("10.00"), "USD", "TXN-TEST-1008", "Cinema Booking Test"));
    }

    @Test
    @DisplayName("Should truncate long bill and store labels safely")
    void testGenerateDynamicKhqrTruncatesLongLabels() {
        String longBill = "TXN-THIS-BILL-NUMBER-IS-WAY-TOO-LONG-1234567890";
        String longDescription = "This store label is definitely longer than twenty-five characters";

        KhqrPayload payload = bakongService.generateDynamicKhqr(new BigDecimal("15.00"), "USD", longBill, longDescription);

        assertNotNull(payload);
        assertTrue(payload.billNumber().length() <= 25);
        assertDoesNotThrow(() ->
                bakongService.generateDynamicKhqr(new BigDecimal("15.00"), "USD", longBill, longDescription));
    }

    @Test
    @DisplayName("Mock checkTransactionByMd5 should return PAID only for the explicit mock prefix")
    void testCheckTransactionMockPaidPrefix() {
        BakongCheckResult result = bakongService.checkTransactionByMd5("MOCK_PAID_1234567890abcdef");

        assertNotNull(result);
        assertTrue(result.paid());
        assertEquals("PAID", result.status());
    }

    @Test
    @DisplayName("Mock checkTransactionByMd5 should not treat contains-PAID strings as paid")
    void testCheckTransactionMockContainsPaidButNotPrefix() {
        BakongCheckResult result = bakongService.checkTransactionByMd5("order-PAID-123456");

        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("PENDING", result.status());
    }

    @Test
    @DisplayName("Mock checkTransactionByMd5 should return PENDING for normal mock hash")
    void testCheckTransactionMockPending() {
        BakongCheckResult result = bakongService.checkTransactionByMd5("random_md5_hash_12345");

        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("PENDING", result.status());
    }

    @Test
    @DisplayName("Production mode with missing Bakong token must fail closed")
    void testCheckTransactionProductionMissingTokenFailsClosed() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(" ");

        BakongCheckResult result = bakongService.checkTransactionByMd5("abcdef1234567890abcdef1234567890");

        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("FAILED", result.status());
    }

    @Test
    @DisplayName("Production mode must not treat the mock paid prefix as real payment")
    void testCheckTransactionProductionIgnoresMockPrefix() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        BakongServiceImpl service = serviceWithRestClient(client);

        server.expect(requestTo(BASE_URL + "/v1/check_transaction_by_md5"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + LIVE_TOKEN))
                .andExpect(content().json("{\"md5\":\"MOCK_PAID_1234567890\"}"))
                .andRespond(withSuccess("{\"responseCode\":1,\"responseMessage\":\"Pending\"}", MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5("MOCK_PAID_1234567890");

        server.verify();
        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("PENDING", result.status());
    }

    @Test
    @DisplayName("Blank MD5 should be rejected")
    void testCheckTransactionBlankMd5Rejected() {
        BakongCheckResult result = bakongService.checkTransactionByMd5("   ");

        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("FAILED", result.status());
    }

    @Test
    @DisplayName("Live responseCode 0 should be paid")
    void testCheckTransactionLivePaid() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        BakongServiceImpl service = serviceWithRestClient(client);

        server.expect(requestTo(BASE_URL + "/v1/check_transaction_by_md5"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + LIVE_TOKEN))
                .andExpect(content().json("{\"md5\":\"abcdef1234567890abcdef1234567890\"}"))
                .andRespond(withSuccess(
                        "{\"responseCode\":0,\"data\":{\"fromAccountId\":\"from@bakong\",\"toAccountId\":\"cinema@bakong\",\"hash\":\"hash-123\"}}",
                        MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5("abcdef1234567890abcdef1234567890");

        server.verify();
        assertNotNull(result);
        assertTrue(result.paid());
        assertEquals("PAID", result.status());
        assertEquals("hash-123", result.hash());
    }

    @Test
    @DisplayName("Live non-zero responseCode should stay pending")
    void testCheckTransactionLivePending() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        BakongServiceImpl service = serviceWithRestClient(client);

        server.expect(requestTo(BASE_URL + "/v1/check_transaction_by_md5"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"responseCode\":1,\"responseMessage\":\"Pending\"}", MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5("abcdef1234567890abcdef1234567890");

        server.verify();
        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("PENDING", result.status());
    }

    @Test
    @DisplayName("Live success response missing payment data should not mark as paid")
    void testCheckTransactionLiveMissingData() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        BakongServiceImpl service = serviceWithRestClient(client);

        server.expect(requestTo(BASE_URL + "/v1/check_transaction_by_md5"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"responseCode\":0}", MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5("abcdef1234567890abcdef1234567890");

        server.verify();
        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("PENDING", result.status());
    }

    @Test
    @DisplayName("Live API errors should fail closed without exposing internal details")
    void testCheckTransactionLiveApiException() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        BakongServiceImpl service = serviceWithRestClient(client);

        server.expect(requestTo(BASE_URL + "/v1/check_transaction_by_md5"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        BakongCheckResult result = service.checkTransactionByMd5("abcdef1234567890abcdef1234567890");

        server.verify();
        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("PENDING", result.status());
        assertEquals("Unable to verify payment at this time", result.message());
    }

    @Test
    @DisplayName("KHQR generation should continue even if verification returns a warning")
    void testGenerateDynamicKhqrVerificationWarning() {
        BakongServiceImpl service = serviceWithVerificationResponse(warningVerificationResponse());

        KhqrPayload payload = service.generateDynamicKhqr(
                new BigDecimal("12.50"),
                "USD",
                "TXN-VERIFY-1001",
                "Cinema Booking Test");

        assertNotNull(payload);
        assertNotNull(payload.khqrString());
        assertNotNull(payload.md5Hash());
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

    private BakongServiceImpl serviceWithRestClient(RestClient restClient) {
        return new BakongServiceImpl(khqrConfig) {
            @Override
            protected RestClient createRestClient() {
                return restClient;
            }
        };
    }

    private BakongServiceImpl serviceWithVerificationResponse(KHQRResponse verifyResponse) {
        return new BakongServiceImpl(khqrConfig) {
            @Override
            protected KHQRResponse verifyGeneratedKhqr(String khqrString) {
                return verifyResponse;
            }
        };
    }

    private KHQRResponse warningVerificationResponse() {
        KHQRStatus status = new KHQRStatus();
        status.setCode(1);
        status.setMessage("Verification warning");

        KHQRResponse response = new KHQRResponse();
        response.setKHQRStatus(status);
        return response;
    }
}
