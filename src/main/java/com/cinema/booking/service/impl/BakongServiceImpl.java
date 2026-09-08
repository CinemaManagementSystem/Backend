package com.cinema.booking.service.impl;

import com.cinema.booking.config.KhqrConfig;
import com.cinema.booking.dto.payments.BakongCheckResult;
import com.cinema.booking.dto.payments.KhqrPayload;
import com.cinema.booking.service.BakongService;
import kh.gov.nbc.bakong_khqr.BakongKHQR;
import kh.gov.nbc.bakong_khqr.model.IndividualInfo;
import kh.gov.nbc.bakong_khqr.model.KHQRCurrency;
import kh.gov.nbc.bakong_khqr.model.KHQRData;
import kh.gov.nbc.bakong_khqr.model.KHQRResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BakongServiceImpl implements BakongService {

    private static final ZoneId PHNOM_PENH_ZONE = ZoneId.of("Asia/Phnom_Penh");
    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_MERCHANT_NAME = "Cinema Booking System";
    private static final String DEFAULT_MERCHANT_CITY = "Phnom Penh";
    private static final String DEFAULT_STORE_LABEL = "Cinema Booking";
    private static final String TERMINAL_LABEL = "POS-01";
    private static final String MOCK_PAID_PREFIX = "MOCK_PAID_";
    private static final String GENERATION_ERROR_MESSAGE = "Failed to generate KHQR";
    private static final String CONFIGURATION_ERROR_MESSAGE = "Payment service is not configured";
    private static final String LIVE_CHECK_ERROR_MESSAGE = "Unable to verify payment at this time";
    private static final String INCOMPLETE_RESPONSE_MESSAGE = "Incomplete Bakong response";
    private static final int DEFAULT_EXPIRY_MINUTES = 10;
    private static final int MAX_BILL_NUMBER_LENGTH = 25;
    private static final int MAX_STORE_LABEL_LENGTH = 25;

    private final KhqrConfig khqrConfig;
    private final RestClient restClient = RestClient.builder().build();

    @Override
    public KhqrPayload generateDynamicKhqr(BigDecimal amount, String currency, String billNumber, String description) {
        return generateDynamicKhqr(amount, currency, billNumber, description, null, null);
    }

    @Override
    public KhqrPayload generateDynamicKhqr(BigDecimal amount, String currency, String billNumber, String description, String customAccountId, String customMerchantName) {
        validateAmount(amount);

        String accountId = resolveAccountId(customAccountId);
        String merchantName = resolveTextWithDefault(customMerchantName, khqrConfig.getMerchantName(), DEFAULT_MERCHANT_NAME);
        String merchantCity = resolveTextWithDefault(khqrConfig.getMerchantCity(), null, DEFAULT_MERCHANT_CITY);
        String normalizedCurrency = normalizeCurrency(currency);
        KHQRCurrency khqrCurrency = "KHR".equals(normalizedCurrency) ? KHQRCurrency.KHR : KHQRCurrency.USD;
        String bill = normalizeLabel(billNumber, "BILL-" + System.currentTimeMillis(), MAX_BILL_NUMBER_LENGTH);
        String storeLabel = normalizeLabel(description, DEFAULT_STORE_LABEL, MAX_STORE_LABEL_LENGTH);
        int expiryMinutes = resolveExpiryMinutes();
        long expirationEpochMillis = System.currentTimeMillis() + ((long) expiryMinutes * 60_000L);

        log.info("Generating KHQR: amount={}, currency={}, billNumber={}", amount, normalizedCurrency, bill);

        // Populate IndividualInfo model for official NBC KHQR SDK
        IndividualInfo individualInfo = new IndividualInfo();
        individualInfo.setBakongAccountId(accountId);
        individualInfo.setMerchantName(merchantName);
        individualInfo.setMerchantCity(merchantCity);
        individualInfo.setCurrency(khqrCurrency);
        individualInfo.setAmount(amount.doubleValue());
        individualInfo.setBillNumber(bill);
        individualInfo.setStoreLabel(storeLabel);
        individualInfo.setTerminalLabel(TERMINAL_LABEL);
        // Tag 99 expirationTimestamp: must be future epoch timestamp in milliseconds for dynamic QR
        individualInfo.setExpirationTimestamp(expirationEpochMillis);

        // Generate dynamic KHQR using official NBC Bakong KHQR SDK
        KHQRResponse response = BakongKHQR.generateIndividual(individualInfo);

        var status = response.getKHQRStatus();
        if (status == null || status.getCode() != 0) {
            String message = safeStatusMessage(status != null ? status.getMessage() : null);
            log.error("Failed to generate KHQR: {}", message);
            throw new IllegalStateException(GENERATION_ERROR_MESSAGE);
        }

        if (!(response.getData() instanceof KHQRData khqrData)) {
            log.error("Invalid response data format from Bakong KHQR SDK");
            throw new IllegalStateException(GENERATION_ERROR_MESSAGE);
        }

        String khqrString = khqrData.getQr();
        String md5Hash = khqrData.getMd5();

        if (!hasText(khqrString) || !hasText(md5Hash)) {
            log.error("Bakong KHQR SDK returned incomplete QR data");
            throw new IllegalStateException(GENERATION_ERROR_MESSAGE);
        }

        // Validate generated QR string with SDK verify method
        KHQRResponse verifyResponse = verifyGeneratedKhqr(khqrString);
        var verifyStatus = verifyResponse.getKHQRStatus();
        if (verifyStatus == null) {
            log.warn("Generated KHQR verification returned no status");
        } else if (verifyStatus.getCode() != 0) {
            log.warn("Generated KHQR failed verification: code={}, message={}",
                    verifyStatus.getCode(), safeStatusMessage(verifyStatus.getMessage()));
        }

        // Application-level timeout in Asia/Phnom_Penh timezone
        LocalDateTime expiresAt = LocalDateTime.now(PHNOM_PENH_ZONE).plusMinutes(expiryMinutes);

        log.info("Generated KHQR MD5: {}", md5Hash);
        log.info("Application payment expiry: {}", expiresAt);

        return new KhqrPayload(khqrString, md5Hash, expiresAt, amount, normalizedCurrency, bill);
    }

    @Override
    public BakongCheckResult checkTransactionByMd5(String md5Hash) {
        if (!hasText(md5Hash)) {
            return BakongCheckResult.failed(md5Hash, "Invalid or missing MD5 hash");
        }

        String normalizedMd5 = md5Hash.trim();

        if (khqrConfig.isMockMode()) {
            log.debug("Bakong running in mock mode for MD5 check: {}", md5Hash);
            if (normalizedMd5.startsWith(MOCK_PAID_PREFIX)) {
                return BakongCheckResult.paid(normalizedMd5, "mock_customer@bakong", khqrConfig.getAccountId());
            }
            return BakongCheckResult.pending(normalizedMd5, "Transaction is pending in mock mode");
        }

        if (!hasText(khqrConfig.getToken())) {
            log.error("Bakong API token is not configured");
            return BakongCheckResult.failed(normalizedMd5, CONFIGURATION_ERROR_MESSAGE);
        }

        // Live Bakong Open API call
        try {
            String url = khqrConfig.getBaseUrl() + "/v1/check_transaction_by_md5";
            Map<?, ?> response = createRestClient().post()
                    .uri(url)
                    .header("Authorization", "Bearer " + khqrConfig.getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("md5", normalizedMd5))
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                log.warn("Bakong API returned an empty response for MD5 {}", normalizedMd5);
                return BakongCheckResult.pending(normalizedMd5, LIVE_CHECK_ERROR_MESSAGE);
            }

            Object responseCode = response.get("responseCode");
            if (isSuccessResponse(responseCode)) {
                Map<?, ?> dataMap = asMap(response.get("data"));
                if (dataMap == null) {
                    log.warn("Bakong API success response for MD5 {} did not include payment data", normalizedMd5);
                    return BakongCheckResult.pending(normalizedMd5, INCOMPLETE_RESPONSE_MESSAGE);
                }

                String fromAccount = asString(dataMap.get("fromAccountId"));
                String toAccount = asString(dataMap.get("toAccountId"));
                String hash = asString(dataMap.get("hash"));

                if (!hasText(fromAccount) || !hasText(toAccount) || !hasText(hash)) {
                    log.warn("Bakong API success response for MD5 {} was missing required payment data", normalizedMd5);
                    return BakongCheckResult.pending(normalizedMd5, INCOMPLETE_RESPONSE_MESSAGE);
                }

                log.info("Bakong transaction confirmed for MD5 {}", normalizedMd5);
                return BakongCheckResult.paid(hash, fromAccount, toAccount);
            }

            String message = asString(response.get("responseMessage"));
            return BakongCheckResult.pending(normalizedMd5, hasText(message) ? message : "Pending");
        } catch (RestClientResponseException ex) {
            log.warn("Bakong API returned an error while checking MD5 {}: status={}, message={}",
                    normalizedMd5, ex.getStatusCode(), ex.getMessage());
            return BakongCheckResult.pending(normalizedMd5, LIVE_CHECK_ERROR_MESSAGE);
        } catch (RestClientException ex) {
            log.warn("Failed to check Bakong transaction by MD5 {} from live API: {}",
                    normalizedMd5, ex.getMessage());
            return BakongCheckResult.pending(normalizedMd5, LIVE_CHECK_ERROR_MESSAGE);
        } catch (Exception ex) {
            log.warn("Unexpected failure while checking Bakong transaction by MD5 {}: {}",
                    normalizedMd5, ex.getMessage());
            return BakongCheckResult.pending(normalizedMd5, LIVE_CHECK_ERROR_MESSAGE);
        }
    }

    protected RestClient createRestClient() {
        return restClient;
    }

    protected KHQRResponse verifyGeneratedKhqr(String khqrString) {
        return BakongKHQR.verify(khqrString);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
    }

    private String resolveAccountId(String customAccountId) {
        if (hasText(customAccountId)) {
            return customAccountId.trim();
        }

        if (hasText(khqrConfig.getAccountId())) {
            return khqrConfig.getAccountId().trim();
        }

        throw new IllegalStateException("Bakong account ID is not configured");
    }

    private String normalizeCurrency(String currency) {
        if (!hasText(currency)) {
            return DEFAULT_CURRENCY;
        }

        String normalizedCurrency = currency.trim().toUpperCase(Locale.ROOT);
        if ("USD".equals(normalizedCurrency) || "KHR".equals(normalizedCurrency)) {
            return normalizedCurrency;
        }

        throw new IllegalArgumentException("Unsupported currency: " + normalizedCurrency);
    }

    private int resolveExpiryMinutes() {
        int expiryMinutes = khqrConfig.getExpiryMinutes();
        return expiryMinutes > 0 ? expiryMinutes : DEFAULT_EXPIRY_MINUTES;
    }

    private String normalizeLabel(String value, String fallback, int maxLength) {
        String normalized = hasText(value) ? value.trim() : fallback;
        return trimToLength(normalized, maxLength);
    }

    private String resolveTextWithDefault(String primary, String fallback, String defaultValue) {
        if (hasText(primary)) {
            return primary.trim();
        }

        if (hasText(fallback)) {
            return fallback.trim();
        }

        return defaultValue;
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private boolean isSuccessResponse(Object responseCode) {
        return responseCode instanceof Number number && number.intValue() == 0;
    }

    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeStatusMessage(String message) {
        return hasText(message) ? message : "Unknown error during KHQR generation";
    }

    public static String calculateCrc16(String input) {
        int crc = 0xFFFF;
        int polynomial = 0x1021;
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);

        for (byte b : bytes) {
            for (int i = 0; i < 8; i++) {
                boolean bit = ((b >> (7 - i)) & 1) == 1;
                boolean c15 = ((crc >> 15) & 1) == 1;
                crc <<= 1;
                if (c15 ^ bit) {
                    crc ^= polynomial;
                }
            }
        }
        crc &= 0xFFFF;
        return String.format("%04X", crc);
    }

    public static String calculateMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm unavailable", e);
        }
    }
}
