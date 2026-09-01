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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BakongServiceImpl implements BakongService {

    private final KhqrConfig khqrConfig;

    @Override
    public KhqrPayload generateDynamicKhqr(BigDecimal amount, String currency, String billNumber, String description) {
        return generateDynamicKhqr(amount, currency, billNumber, description, null, null);
    }

    @Override
    public KhqrPayload generateDynamicKhqr(BigDecimal amount, String currency, String billNumber, String description, String customAccountId, String customMerchantName) {
        String accountId = (customAccountId != null && !customAccountId.isBlank())
                ? customAccountId.trim()
                : (khqrConfig.getAccountId() != null && !khqrConfig.getAccountId().isBlank() ? khqrConfig.getAccountId().trim() : "sothearith_kim@bkrt");

        String merchantName = (customMerchantName != null && !customMerchantName.isBlank())
                ? customMerchantName.trim()
                : (khqrConfig.getMerchantName() != null && !khqrConfig.getMerchantName().isBlank() ? khqrConfig.getMerchantName().trim() : "Cinema Booking System");

        String merchantCity = (khqrConfig.getMerchantCity() != null && !khqrConfig.getMerchantCity().isBlank())
                ? khqrConfig.getMerchantCity().trim()
                : "Phnom Penh";

        String normalizedCurrency = (currency != null && !currency.isBlank()) ? currency.trim().toUpperCase() : "USD";
        KHQRCurrency khqrCurrency = "KHR".equalsIgnoreCase(normalizedCurrency) ? KHQRCurrency.KHR : KHQRCurrency.USD;

        String bill = (billNumber != null && !billNumber.isBlank()) ? billNumber.trim() : "BILL-" + System.currentTimeMillis();
        if (bill.length() > 25) {
            bill = bill.substring(0, 25);
        }

        String storeLabel = (description != null && !description.isBlank()) ? description.trim() : "Cinema Booking";
        if (storeLabel.length() > 25) {
            storeLabel = storeLabel.substring(0, 25);
        }

        int expiryMinutes = khqrConfig.getExpiryMinutes() > 0 ? khqrConfig.getExpiryMinutes() : 10;
        long expirationEpochMillis = System.currentTimeMillis() + ((long) expiryMinutes * 60 * 1000L);

        log.info("Generating KHQR: amount={}, currency={}, accountId={}, billNumber={}",
                amount, normalizedCurrency, accountId, bill);

        // Populate IndividualInfo model for official NBC KHQR SDK
        IndividualInfo individualInfo = new IndividualInfo();
        individualInfo.setBakongAccountId(accountId);
        individualInfo.setMerchantName(merchantName);
        individualInfo.setMerchantCity(merchantCity);
        individualInfo.setCurrency(khqrCurrency);
        individualInfo.setAmount(amount != null ? amount.doubleValue() : 0.0);
        individualInfo.setBillNumber(bill);
        individualInfo.setStoreLabel(storeLabel);
        individualInfo.setTerminalLabel("POS-01");
        // Tag 99 expirationTimestamp: must be future epoch timestamp in milliseconds for dynamic QR
        individualInfo.setExpirationTimestamp(expirationEpochMillis);

        // Generate dynamic KHQR using official NBC Bakong KHQR SDK
        KHQRResponse response = BakongKHQR.generateIndividual(individualInfo);

        if (response == null || response.getKHQRStatus() == null || response.getKHQRStatus().getCode() != 0) {
            String errorMsg = (response != null && response.getKHQRStatus() != null)
                    ? response.getKHQRStatus().getMessage()
                    : "Unknown error during KHQR generation";
            log.error("Failed to generate dynamic KHQR: {}", errorMsg);
            throw new IllegalStateException("Failed to generate dynamic KHQR: " + errorMsg);
        }

        if (!(response.getData() instanceof KHQRData khqrData)) {
            log.error("Invalid response data format from Bakong KHQR SDK: {}", response.getData());
            throw new IllegalStateException("Invalid response data format from Bakong KHQR SDK");
        }

        String khqrString = khqrData.getQr();
        String md5Hash = khqrData.getMd5();

        // Validate generated QR string with SDK verify method
        KHQRResponse verifyResponse = BakongKHQR.verify(khqrString);
        if (verifyResponse != null && verifyResponse.getKHQRStatus() != null && verifyResponse.getKHQRStatus().getCode() != 0) {
            log.warn("Generated KHQR failed verification: code={}, message={}",
                    verifyResponse.getKHQRStatus().getCode(), verifyResponse.getKHQRStatus().getMessage());
        }

        // Application-level timeout in Asia/Phnom_Penh timezone
        LocalDateTime expiresAt = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh")).plusMinutes(expiryMinutes);

        log.info("Generated KHQR MD5: {}", md5Hash);
        log.info("Application payment expiry: {}", expiresAt);

        return new KhqrPayload(khqrString, md5Hash, expiresAt, amount, normalizedCurrency, bill);
    }

    @Override
    public BakongCheckResult checkTransactionByMd5(String md5Hash) {
        if (md5Hash == null || md5Hash.isBlank()) {
            return BakongCheckResult.failed(md5Hash, "Invalid or missing MD5 hash");
        }

        // Mock / Development mode check
        if (khqrConfig.isMockMode() || khqrConfig.getToken() == null || khqrConfig.getToken().isBlank()) {
            log.debug("Bakong running in mock mode for MD5 check: {}", md5Hash);
            if (md5Hash.contains("PAID") || md5Hash.startsWith("MOCK_PAID_")) {
                return BakongCheckResult.paid(md5Hash, "mock_customer@bakong", khqrConfig.getAccountId());
            }
            return BakongCheckResult.pending(md5Hash, "Transaction is pending in mock mode");
        }

        // Live Bakong Open API call
        try {
            String url = khqrConfig.getBaseUrl() + "/v1/check_transaction_by_md5";
            RestClient restClient = RestClient.builder().build();

            Map<?, ?> response = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + khqrConfig.getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("md5", md5Hash))
                    .retrieve()
                    .body(Map.class);

            if (response != null) {
                Object responseCode = response.get("responseCode");
                Object dataObj = response.get("data");

                if (Integer.valueOf(0).equals(responseCode) && dataObj instanceof Map<?, ?> dataMap) {
                    String fromAccount = (String) dataMap.get("fromAccountId");
                    String toAccount = (String) dataMap.get("toAccountId");
                    String hash = (String) dataMap.get("hash");
                    log.info("Bakong transaction confirmed for MD5 {}: from={}, to={}", md5Hash, fromAccount, toAccount);
                    return BakongCheckResult.paid(hash != null ? hash : md5Hash, fromAccount, toAccount);
                } else {
                    String message = response.get("responseMessage") != null ? response.get("responseMessage").toString() : "Pending";
                    return BakongCheckResult.pending(md5Hash, message);
                }
            }
            return BakongCheckResult.notFound(md5Hash);
        } catch (Exception ex) {
            log.warn("Failed to check Bakong transaction by MD5 {} from live API: {}", md5Hash, ex.getMessage());
            return BakongCheckResult.pending(md5Hash, "Bakong check failed: " + ex.getMessage());
        }
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
