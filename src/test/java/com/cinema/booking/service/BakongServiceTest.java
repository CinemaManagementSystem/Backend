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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BakongServiceTest {

    private static final String BASE_URL      = "https://api-bakong.nbc.gov.kh";
    private static final String SIT_BASE_URL  = "https://sit-api-bakong.nbc.gov.kh";
    private static final String LIVE_TOKEN    = "live-token";
    private static final String VALID_MD5     = "abcdef1234567890abcdef1234567890";
    private static final String MOCK_PAID_MD5 = "deadbeefdeadbeefdeadbeefdeadbeef";
    private static final String VERIFY_PATH   = "/v1/check_transaction_by_md5";
    private static final String TOKEN_PATH    = "/v1/renew_token";
    private static final String REQUEST_TOKEN_PATH = "/v1/request_token";
    private static final String VERIFY_TOKEN_PATH = "/v1/verify";

    // A real HS256 JWT with exp = 9999999999 (far future).
    // Payload: {"data":{},"iat":1000000000,"exp":9999999999}
    private static final String FRESH_TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJkYXRhIjp7fSwiaWF0IjoxMDAwMDAwMDAwLCJleHAiOjk5OTk5OTk5OTl9" +
            ".placeholder-sig";

    // SIT token payload: {"env":"sit","exp":9999999999}
    private static final String SIT_TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJlbnYiOiJzaXQiLCJleHAiOjk5OTk5OTk5OTl9" +
            ".sig";

    // Production token payload: {"env":"prod","exp":9999999999}
    private static final String PROD_TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJlbnYiOiJwcm9kIiwiZXhwIjo5OTk5OTk5OTk5fQ" +
            ".sig";

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
        khqrConfig.setOrganization("Cinema Test Org");
        khqrConfig.setProject("Cinema Booking Test");

        bakongService = new BakongServiceImpl(khqrConfig);
    }

    // -----------------------------------------------------------------------
    // KHQR generation tests
    // -----------------------------------------------------------------------

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
        String longBill        = "TXN-THIS-BILL-NUMBER-IS-WAY-TOO-LONG-1234567890";
        String longDescription = "This store label is definitely longer than twenty-five characters";

        KhqrPayload payload = bakongService.generateDynamicKhqr(new BigDecimal("15.00"), "USD", longBill, longDescription);

        assertNotNull(payload);
        assertTrue(payload.billNumber().length() <= 25);
        assertDoesNotThrow(() ->
                bakongService.generateDynamicKhqr(new BigDecimal("15.00"), "USD", longBill, longDescription));
    }

    @Test
    @DisplayName("KHQR generation should continue even if verification returns a warning")
    void testGenerateDynamicKhqrVerificationWarning() {
        BakongServiceImpl service = serviceWithVerificationResponse(warningVerificationResponse());

        KhqrPayload payload = service.generateDynamicKhqr(
                new BigDecimal("12.50"), "USD", "TXN-VERIFY-1001", "Cinema Booking Test");

        assertNotNull(payload);
        assertNotNull(payload.khqrString());
        assertNotNull(payload.md5Hash());
    }

    @Test
    @DisplayName("KHQR expiration should never exceed ten minutes")
    void testGenerateDynamicKhqrCapsExpirationAtTenMinutes() {
        khqrConfig.setExpiryMinutes(30);
        LocalDateTime before = LocalDateTime.now();

        KhqrPayload payload = bakongService.generateDynamicKhqr(
                new BigDecimal("12.50"), "USD", "TXN-EXPIRY-CAP", "Cinema Booking Test");

        assertTrue(Duration.between(before, payload.expiresAt()).compareTo(Duration.ofMinutes(10)) <= 0);
        assertTrue(payload.expiresAt().isAfter(before));
    }

    // -----------------------------------------------------------------------
    // Mock-mode payment check tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Mock checkTransactionByMd5 should return PAID only for the configured mock MD5")
    void testCheckTransactionMockPaidPrefix() {
        BakongCheckResult result = bakongService.checkTransactionByMd5(MOCK_PAID_MD5);

        assertNotNull(result);
        assertTrue(result.paid());
        assertEquals("PAID", result.status());
    }

    @Test
    @DisplayName("Invalid MD5 should be rejected before mock status matching")
    void testCheckTransactionMockContainsPaidButNotPrefix() {
        BakongCheckResult result = bakongService.checkTransactionByMd5("order-PAID-123456");

        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("FAILED", result.status());
        assertFalse(result.authoritative());
        assertFalse(result.retryable());
    }

    @Test
    @DisplayName("Mock checkTransactionByMd5 should return PENDING for normal mock hash")
    void testCheckTransactionMockPending() {
        BakongCheckResult result = bakongService.checkTransactionByMd5(VALID_MD5);

        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("PENDING", result.status());
    }

    // -----------------------------------------------------------------------
    // Configuration validation tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Production mode with missing Bakong token must be retryable and non-authoritative")
    void testCheckTransactionProductionMissingTokenFailsClosed() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(" ");
        khqrConfig.setPassword(null);

        BakongCheckResult result = bakongService.checkTransactionByMd5("abcdef1234567890abcdef1234567890");

        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("VERIFICATION_ERROR", result.status());
        assertFalse(result.authoritative());
        assertTrue(result.retryable());
        assertFalse(result.rateLimited());
    }

    @Test
    @DisplayName("Missing token configuration does not fail application startup")
    void testMissingConfigurationDoesNotFailStartup() {
        KhqrConfig config = new KhqrConfig();
        config.setMockMode(false);
        config.setBaseUrl(BASE_URL);
        config.setToken(null);
        config.setEmail(null);
        config.setPassword(null);

        BakongServiceImpl service = new BakongServiceImpl(config);
        assertDoesNotThrow(service::validateConfiguration);
    }

    @Test
    @DisplayName("Sandbox/production configuration mismatch is detected")
    void testEnvironmentMismatchDetection() {
        // SIT URL + Prod Token => mismatch
        assertTrue(BakongServiceImpl.isEnvironmentMismatch(SIT_BASE_URL, PROD_TOKEN));
        // Prod URL + SIT Token => mismatch
        assertTrue(BakongServiceImpl.isEnvironmentMismatch(BASE_URL, SIT_TOKEN));
        // SIT URL + SIT Token => match (no mismatch)
        assertFalse(BakongServiceImpl.isEnvironmentMismatch(SIT_BASE_URL, SIT_TOKEN));
        // Prod URL + Prod Token => match (no mismatch)
        assertFalse(BakongServiceImpl.isEnvironmentMismatch(BASE_URL, PROD_TOKEN));
    }

    @Test
    @DisplayName("Blank MD5 should be rejected")
    void testCheckTransactionBlankMd5Rejected() {
        BakongCheckResult result = bakongService.checkTransactionByMd5("   ");

        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("FAILED", result.status());
        assertFalse(result.authoritative());
        assertFalse(result.retryable());
    }

    @Test
    @DisplayName("Malformed MD5 values should be rejected")
    void testCheckTransactionMalformedMd5Rejected() {
        assertEquals("FAILED", bakongService.checkTransactionByMd5("abcdef1234567890").status());
        assertEquals("FAILED", bakongService.checkTransactionByMd5("abcdef1234567890abcdef123456789g").status());
        assertEquals("FAILED", bakongService.checkTransactionByMd5(" " + VALID_MD5).status());
    }

    // -----------------------------------------------------------------------
    // Live-mode payment check tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Production mode must not use mock payment matching")
    void testCheckTransactionProductionIgnoresMockPrefix() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());
        service.resolveToken("seed");

        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + LIVE_TOKEN))
                .andExpect(content().json("{\"md5\":\"" + MOCK_PAID_MD5 + "\"}"))
                .andRespond(withSuccess("{\"responseCode\":1,\"responseMessage\":\"Pending\"}", MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5(MOCK_PAID_MD5);

        server.verify();
        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("PENDING", result.status());
    }

    @Test
    @DisplayName("Live responseCode 0 should be paid")
    void testCheckTransactionLivePaid() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());
        service.resolveToken("seed");

        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + LIVE_TOKEN))
                .andExpect(content().json("{\"md5\":\"abcdef1234567890abcdef1234567890\"}"))
                .andRespond(withSuccess(
                        "{\"responseCode\":0,\"data\":{\"fromAccountId\":\"from@bakong\",\"toAccountId\":\"cinema@bakong\",\"hash\":\"hash-123\",\"amount\":12.50,\"currency\":\"USD\"}}",
                        MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5("abcdef1234567890abcdef1234567890");

        server.verify();
        assertNotNull(result);
        assertTrue(result.paid());
        assertEquals("PAID", result.status());
        assertEquals("hash-123", result.hash());
        assertEquals(new BigDecimal("12.5"), result.amount());
        assertEquals("USD", result.currency());
    }

    @Test
    @DisplayName("Live responseCode 0 with non-null data should be paid")
    void testCheckTransactionLivePaidWithMinimalData() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());
        service.resolveToken("seed");

        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"md5\":\"" + VALID_MD5 + "\"}"))
                .andRespond(withSuccess(
                        "{\"responseCode\":0,\"data\":{}}",
                        MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5(VALID_MD5);

        server.verify();
        assertTrue(result.paid());
        assertEquals("PAID", result.status());
    }

    @Test
    @DisplayName("Live request should trim whitespace from the configured Bakong token")
    void testCheckTransactionTrimsTokenWhitespace() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN + "\r\n");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());
        service.resolveToken("seed");

        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + LIVE_TOKEN))
                .andRespond(withSuccess(
                        "{\"responseCode\":0,\"data\":{\"fromAccountId\":\"from@bakong\",\"toAccountId\":\"cinema@bakong\",\"hash\":\"hash-123\"}}",
                        MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5("abcdef1234567890abcdef1234567890");

        server.verify();
        assertTrue(result.paid());
    }

    @Test
    @DisplayName("Live non-zero responseCode should stay pending")
    void testCheckTransactionLivePending() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());
        service.resolveToken("seed");

        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"responseCode\":1,\"responseMessage\":\"Pending\"}", MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5("abcdef1234567890abcdef1234567890");

        server.verify();
        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("PENDING", result.status());
    }

    @Test
    @DisplayName("Bakong daily request limit is inconclusive, not an unpaid transaction")
    void testCheckTransactionRateLimited() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());
        service.resolveToken("seed");

        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andRespond(withSuccess(
                        "{\"responseCode\":1,\"responseMessage\":\"Daily request limit of 100 exceeded. Please try again tomorrow.\"}",
                        MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5("abcdef1234567890abcdef1234567890");

        server.verify();
        assertFalse(result.paid());
        assertEquals("VERIFICATION_ERROR", result.status());
        assertFalse(result.authoritative());
        assertFalse(result.retryable());
        assertTrue(result.rateLimited());
    }

    @Test
    @DisplayName("Live unknown MD5 must not be treated as paid")
    void testCheckTransactionLiveUnknownMd5() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());
        service.resolveToken("seed");

        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"md5\":\"abcdef1234567890abcdef1234567890\"}"))
                .andRespond(withSuccess(
                        "{\"responseCode\":1,\"responseMessage\":\"Transaction not found\"}",
                        MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5("abcdef1234567890abcdef1234567890");

        server.verify();
        assertFalse(result.paid());
        assertEquals("NOT_FOUND", result.status());
    }

    @Test
    @DisplayName("Live success response missing payment data should not mark as paid")
    void testCheckTransactionLiveMissingData() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());
        service.resolveToken("seed");

        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"responseCode\":0}", MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5("abcdef1234567890abcdef1234567890");

        server.verify();
        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("PENDING", result.status());
    }

    @Test
    @DisplayName("Live API errors should be retryable without exposing internal details")
    void testCheckTransactionLiveApiException() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());
        service.resolveToken("seed");

        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        BakongCheckResult result = service.checkTransactionByMd5("abcdef1234567890abcdef1234567890");

        server.verify();
        assertNotNull(result);
        assertFalse(result.paid());
        assertEquals("VERIFICATION_ERROR", result.status());
        assertEquals("Unable to verify payment at this time", result.message());
        assertFalse(result.authoritative());
        assertTrue(result.retryable());
    }

    // -----------------------------------------------------------------------
    // Token lifecycle tests (the core of this fix)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Valid cached token is reused without a token request")
    void testCachedTokenIsReused() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());

        // Seed the cache manually: token present and far-future expiry.
        service.setCachedToken(LIVE_TOKEN);
        service.setTokenExpiryEpochSeconds(Instant.now().getEpochSecond() + 86400);

        // Only ONE request expected — the verification call. No token request.
        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + LIVE_TOKEN))
                .andRespond(withSuccess("{\"responseCode\":1,\"responseMessage\":\"Pending\"}", MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5(VALID_MD5);

        server.verify();
        assertEquals("PENDING", result.status());
    }

    @Test
    @DisplayName("Expired cached token triggers a refresh before verification")
    void testExpiredTokenIsRefreshedBeforeVerification() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken("old-stale-token");
        khqrConfig.setEmail("test@cinema.com");
        khqrConfig.setPassword("secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());

        // Force the cached token to look expired.
        service.setCachedToken("old-stale-token");
        service.setTokenExpiryEpochSeconds(Instant.now().getEpochSecond() - 1); // expired 1 second ago

        String tokenResponse = "{\"responseCode\":0,\"data\":{\"token\":\"" + FRESH_TOKEN + "\"}}";

        // Expect: first a token request, then a verification request with the fresh token.
        server.expect(requestTo(BASE_URL + TOKEN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(tokenResponse, MediaType.APPLICATION_JSON));

        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + FRESH_TOKEN))
                .andRespond(withSuccess("{\"responseCode\":1,\"responseMessage\":\"Pending\"}", MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5(VALID_MD5);

        server.verify();
        assertEquals("PENDING", result.status());
        // Confirm cache was updated with fresh token.
        assertEquals(FRESH_TOKEN, service.getCachedToken());
    }

    @Test
    @DisplayName("First verification 401 triggers token refresh; retry with new token returns PAID")
    void testFirst401TriggersRefreshAndRetryReturnsPaid() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);
        khqrConfig.setEmail("test@cinema.com");
        khqrConfig.setPassword("secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongRequestBudgetService budget = mock(BakongRequestBudgetService.class);
        when(budget.tryAcquire()).thenReturn(
                new BakongRequestBudgetService.Permit(true, 0, 1, 95, null),
                new BakongRequestBudgetService.Permit(true, 0, 2, 95, null));
        RestClient client = builder.build();
        BakongServiceImpl service = new BakongServiceImpl(khqrConfig, budget) {
            @Override
            protected RestClient createRestClient() {
                return client;
            }
        };
        // Seed with the old token.
        service.setCachedToken(LIVE_TOKEN);
        service.setTokenExpiryEpochSeconds(Instant.now().getEpochSecond() + 86400);

        String tokenResponse = "{\"responseCode\":0,\"data\":{\"token\":\"" + FRESH_TOKEN + "\"}}";
        String paidResponse  = "{\"responseCode\":0,\"data\":{\"fromAccountId\":\"from@bakong\"," +
                "\"toAccountId\":\"cinema@bakong\",\"hash\":\"hash-after-refresh\",\"amount\":12.50,\"currency\":\"USD\"}}";

        // 1. First verification → 401
        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + LIVE_TOKEN))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // 2. Token refresh request → 200 with new token
        server.expect(requestTo(BASE_URL + TOKEN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(tokenResponse, MediaType.APPLICATION_JSON));

        // 3. Retry verification with the NEW token → PAID
        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + FRESH_TOKEN))
                .andRespond(withSuccess(paidResponse, MediaType.APPLICATION_JSON));

        BakongCheckResult result = service.checkTransactionByMd5(VALID_MD5);

        server.verify();
        assertTrue(result.paid());
        assertEquals("PAID", result.status());
        assertEquals("hash-after-refresh", result.hash());
        verify(budget, times(2)).tryAcquire();
    }

    @Test
    @DisplayName("First verification 401, token refresh fails → VERIFICATION_ERROR, not FAILED")
    void testFirst401RefreshFailsReturnsVerificationError() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);
        khqrConfig.setEmail("test@cinema.com");
        khqrConfig.setPassword("wrong-password");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());
        service.setCachedToken(LIVE_TOKEN);
        service.setTokenExpiryEpochSeconds(Instant.now().getEpochSecond() + 86400);

        // 1. Verification 401
        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(header("Authorization", "Bearer " + LIVE_TOKEN))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // 2. Token refresh also fails with 401
        server.expect(requestTo(BASE_URL + TOKEN_PATH))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        BakongCheckResult result = service.checkTransactionByMd5(VALID_MD5);

        server.verify();
        assertFalse(result.paid());
        assertEquals("VERIFICATION_ERROR", result.status());
        assertFalse(result.authoritative());
        assertTrue(result.retryable());
    }

    @Test
    @DisplayName("Second request uses a different (refreshed) token than the first")
    void testRetryUsesNewToken() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);
        khqrConfig.setEmail("test@cinema.com");
        khqrConfig.setPassword("secret");

        AtomicReference<String> firstAuthHeader  = new AtomicReference<>();
        AtomicReference<String> secondAuthHeader = new AtomicReference<>();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());
        service.setCachedToken(LIVE_TOKEN);
        service.setTokenExpiryEpochSeconds(Instant.now().getEpochSecond() + 86400);

        String tokenResponse = "{\"responseCode\":0,\"data\":{\"token\":\"" + FRESH_TOKEN + "\"}}";
        String paidResponse  = "{\"responseCode\":0,\"data\":{\"fromAccountId\":\"from@bakong\"," +
                "\"toAccountId\":\"cinema@bakong\",\"hash\":\"h\",\"amount\":1,\"currency\":\"USD\"}}";

        // First verification → 401 (capture header)
        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(request -> firstAuthHeader.set(request.getHeaders().getFirst("Authorization")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // Token refresh → success
        server.expect(requestTo(BASE_URL + TOKEN_PATH))
                .andRespond(withSuccess(tokenResponse, MediaType.APPLICATION_JSON));

        // Second verification (capture header)
        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(request -> secondAuthHeader.set(request.getHeaders().getFirst("Authorization")))
                .andRespond(withSuccess(paidResponse, MediaType.APPLICATION_JSON));

        service.checkTransactionByMd5(VALID_MD5);

        server.verify();
        assertNotNull(firstAuthHeader.get());
        assertNotNull(secondAuthHeader.get());
        assertNotEquals(firstAuthHeader.get(), secondAuthHeader.get(),
                "Second request must use a different Authorization header than the first");
        assertEquals("Bearer " + LIVE_TOKEN,  firstAuthHeader.get());
        assertEquals("Bearer " + FRESH_TOKEN, secondAuthHeader.get());
    }

    @Test
    @DisplayName("Second consecutive 401 after refresh returns VERIFICATION_ERROR")
    void testSecond401AfterRefreshReturnsVerificationError() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(LIVE_TOKEN);
        khqrConfig.setEmail("test@cinema.com");
        khqrConfig.setPassword("secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());
        service.setCachedToken(LIVE_TOKEN);
        service.setTokenExpiryEpochSeconds(Instant.now().getEpochSecond() + 86400);

        String tokenResponse = "{\"responseCode\":0,\"data\":{\"token\":\"" + FRESH_TOKEN + "\"}}";

        // First verification → 401
        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // Token refresh succeeds
        server.expect(requestTo(BASE_URL + TOKEN_PATH))
                .andRespond(withSuccess(tokenResponse, MediaType.APPLICATION_JSON));

        // Second verification with fresh token → still 401 (Bakong rejected it too)
        server.expect(requestTo(BASE_URL + VERIFY_PATH))
                .andExpect(header("Authorization", "Bearer " + FRESH_TOKEN))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        BakongCheckResult result = service.checkTransactionByMd5(VALID_MD5);

        server.verify();
        assertFalse(result.paid());
        assertEquals("VERIFICATION_ERROR", result.status());
        assertFalse(result.authoritative());
        assertTrue(result.retryable());
    }

    @Test
    @DisplayName("Missing token configuration with no password produces VERIFICATION_ERROR, not FAILED")
    void testMissingCredentialsProducesVerificationError() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(null);
        khqrConfig.setPassword(null);
        khqrConfig.setEmail(null);

        BakongCheckResult result = bakongService.checkTransactionByMd5(VALID_MD5);

        assertFalse(result.paid());
        assertEquals("VERIFICATION_ERROR", result.status());
        assertFalse(result.authoritative());
        assertTrue(result.retryable());
    }

    @Test
    @DisplayName("Token with Bearer prefix, quotes, or whitespace is cleaned properly")
    void testCleanTokenRemovesQuotesAndWhitespaceAndBearer() {
        assertEquals(FRESH_TOKEN, BakongServiceImpl.cleanToken("Bearer " + FRESH_TOKEN));
        assertEquals(FRESH_TOKEN, BakongServiceImpl.cleanToken("BEARER " + FRESH_TOKEN));
        assertEquals(FRESH_TOKEN, BakongServiceImpl.cleanToken("\"" + FRESH_TOKEN + "\""));
        assertEquals(FRESH_TOKEN, BakongServiceImpl.cleanToken("'" + FRESH_TOKEN + "'"));
        assertEquals(FRESH_TOKEN, BakongServiceImpl.cleanToken("  \"Bearer " + FRESH_TOKEN + "\"  "));
        assertEquals(FRESH_TOKEN, BakongServiceImpl.cleanToken("  'Bearer " + FRESH_TOKEN + "'  "));
        assertEquals(FRESH_TOKEN, BakongServiceImpl.stripBearerPrefix("Bearer " + FRESH_TOKEN));
    }

    @Test
    @DisplayName("JWT expiry extraction returns epoch seconds from the exp claim")
    void testExtractJwtExpiry() {
        // FRESH_TOKEN payload: {"data":{},"iat":1000000000,"exp":9999999999}
        long exp = BakongServiceImpl.extractJwtExpiry(FRESH_TOKEN);
        assertEquals(9999999999L, exp);
    }

    @Test
    @DisplayName("JWT expiry extraction returns 0 for non-JWT string")
    void testExtractJwtExpiryReturnsZeroForNonJwt() {
        assertEquals(0L, BakongServiceImpl.extractJwtExpiry("not-a-jwt"));
        assertEquals(0L, BakongServiceImpl.extractJwtExpiry(null));
        assertEquals(0L, BakongServiceImpl.extractJwtExpiry(""));
    }

    @Test
    @DisplayName("Token cache is invalidated when invalidateCachedToken() is called")
    void testInvalidateCachedToken() {
        bakongService.setCachedToken(LIVE_TOKEN);
        bakongService.setTokenExpiryEpochSeconds(Instant.now().getEpochSecond() + 86400);

        bakongService.invalidateCachedToken();

        assertNull(bakongService.getCachedToken());
        assertEquals(0L, bakongService.getTokenExpiryEpochSeconds());
    }

    @Test
    @DisplayName("resolveToken returns null when no token is available and refresh credentials are missing")
    void testResolveTokenReturnsNullWhenNoCredentials() {
        khqrConfig.setToken(null);
        khqrConfig.setEmail(null);
        khqrConfig.setPassword(null);
        bakongService.setCachedToken(null);
        bakongService.setTokenExpiryEpochSeconds(0);

        String token = bakongService.resolveToken("test");

        assertNull(token);
    }

    @Test
    @DisplayName("Blank token starts initial request_token flow and does not call renew_token")
    void testBlankTokenUsesInitialRequestTokenNotRenewToken() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(null);
        khqrConfig.setEmail("test@cinema.com");
        khqrConfig.setOrganization("Cinema Org");
        khqrConfig.setProject("Cinema Project");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());

        server.expect(requestTo(BASE_URL + REQUEST_TOKEN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"email\":\"test@cinema.com\",\"organization\":\"Cinema Org\",\"project\":\"Cinema Project\"}"))
                .andRespond(withSuccess("{\"responseCode\":0,\"responseMessage\":\"Email has been sent\",\"data\":null}", MediaType.APPLICATION_JSON));

        String token = service.resolveToken("initial-missing-token");

        server.verify();
        assertNull(token);
    }

    @Test
    @DisplayName("Initial request_token flow verifies email code and caches returned production token")
    void testInitialRequestTokenThenVerifyCachesToken() {
        khqrConfig.setMockMode(false);
        khqrConfig.setToken(null);
        khqrConfig.setEmail("test@cinema.com");
        khqrConfig.setOrganization("Cinema Org");
        khqrConfig.setProject("Cinema Project");
        khqrConfig.setVerificationCode("12345678901234567890");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());

        server.expect(requestTo(BASE_URL + REQUEST_TOKEN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"responseCode\":0,\"responseMessage\":\"Email has been sent\",\"data\":null}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(BASE_URL + VERIFY_TOKEN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"code\":\"12345678901234567890\"}"))
                .andRespond(withSuccess("{\"responseCode\":0,\"responseMessage\":\"Token has been issued\",\"data\":{\"token\":\"" + FRESH_TOKEN + "\"}}", MediaType.APPLICATION_JSON));

        String token = service.resolveToken("initial-with-code");

        server.verify();
        assertEquals(FRESH_TOKEN, token);
        assertEquals(FRESH_TOKEN, service.getCachedToken());
        assertEquals(9999999999L, service.getTokenExpiryEpochSeconds());
    }

    @Test
    @DisplayName("requestNewToken returns null when email is missing")
    void testRequestNewTokenReturnsNullWhenCredentialsMissing() {
        khqrConfig.setEmail(null);
        khqrConfig.setPassword(null);

        String token = bakongService.requestNewToken();

        assertNull(token);
    }

    @Test
    @DisplayName("requestNewToken uses official NBC /v1/renew_token when password is not configured")
    void testRequestNewTokenUsesRenewTokenWhenPasswordNotConfigured() {
        khqrConfig.setEmail("test@cinema.com");
        khqrConfig.setPassword(null);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BakongServiceImpl service = serviceWithRestClient(builder.build());

        server.expect(requestTo(BASE_URL + "/v1/renew_token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"email\":\"test@cinema.com\"}"))
                .andRespond(withSuccess("{\"responseCode\":0,\"data\":{\"token\":\"" + FRESH_TOKEN + "\"}}", MediaType.APPLICATION_JSON));

        String token = service.requestNewToken();

        server.verify();
        assertEquals(FRESH_TOKEN, token);
    }

    // -----------------------------------------------------------------------
    // Static utility tests
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Test helpers
    // -----------------------------------------------------------------------

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
