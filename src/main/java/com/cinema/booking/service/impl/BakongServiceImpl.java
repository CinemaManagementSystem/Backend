package com.cinema.booking.service.impl;

import com.cinema.booking.config.KhqrConfig;
import com.cinema.booking.dto.payments.BakongCheckResult;
import com.cinema.booking.dto.payments.KhqrPayload;
import com.cinema.booking.service.BakongService;
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
        String currCode = "USD".equalsIgnoreCase(currency) ? "840" : "116";
        String formattedAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        String accountId = khqrConfig.getAccountId() != null ? khqrConfig.getAccountId() : "cinema_official@dev";
        String merchantName = khqrConfig.getMerchantName() != null ? khqrConfig.getMerchantName() : "Cinema Booking";
        String merchantCity = khqrConfig.getMerchantCity() != null ? khqrConfig.getMerchantCity() : "Phnom Penh";

        // Tag 29: Merchant Account Info (Individual / Bakong Account)
        String tag29Value = tlv("00", accountId);

        // Tag 62: Additional Data Field (Bill number & Store label)
        String tag62Value = tlv("01", billNumber != null ? billNumber : "BILL-" + System.currentTimeMillis())
                + (description != null && !description.isBlank() ? tlv("08", description) : "");

        StringBuilder emv = new StringBuilder();
        emv.append(tlv("00", "01"));                   // Payload Format Indicator
        emv.append(tlv("01", "12"));                   // Point of Initiation Method (12 = Dynamic)
        emv.append(tlv("29", tag29Value));             // Merchant Account Information
        emv.append(tlv("52", "7832"));                 // Merchant Category Code (7832 = Motion Picture Theaters)
        emv.append(tlv("53", currCode));               // Transaction Currency (840 = USD, 116 = KHR)
        emv.append(tlv("54", formattedAmount));         // Transaction Amount
        emv.append(tlv("58", "KH"));                   // Country Code
        emv.append(tlv("59", merchantName));           // Merchant Name
        emv.append(tlv("60", merchantCity));           // Merchant City
        emv.append(tlv("62", tag62Value));             // Additional Data

        emv.append("6304");                            // Tag 63 (CRC) with length 04
        String crc = calculateCrc16(emv.toString());
        emv.append(crc);

        String khqrString = emv.toString();
        String md5Hash = calculateMd5(khqrString);
        LocalDateTime expiresAt = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh")).plusMinutes(10);

        log.info("Generated dynamic KHQR for bill {}: amount={}, md5={}", billNumber, formattedAmount, md5Hash);
        return new KhqrPayload(khqrString, md5Hash, expiresAt, amount, currency, billNumber);
    }

    @Override
    public BakongCheckResult checkTransactionByMd5(String md5Hash) {
        if (md5Hash == null || md5Hash.isBlank()) {
            return BakongCheckResult.failed(md5Hash, "Invalid or missing MD5 hash");
        }

        // Mock/Development mode check
        if (khqrConfig.isMockMode() || khqrConfig.getToken() == null || khqrConfig.getToken().isBlank()) {
            log.debug("Bakong running in mock mode for MD5 check: {}", md5Hash);
            // In mock mode, if hash starts with "MOCK_PAID_" or contains "PAID", simulate approved transaction
            if (md5Hash.contains("PAID")) {
                return BakongCheckResult.paid(md5Hash, "mock_customer@bakong", khqrConfig.getAccountId());
            }
            return BakongCheckResult.pending(md5Hash, "Transaction is pending in mock mode");
        }

        // Production / Live Bakong Open API call
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

    private static String tlv(String tag, String value) {
        if (value == null) {
            return "";
        }
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        return tag + String.format("%02d", length) + value;
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
