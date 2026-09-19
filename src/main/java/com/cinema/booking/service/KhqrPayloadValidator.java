package com.cinema.booking.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Accept SDK-generated browser payloads only for an already priced server payment. */
public final class KhqrPayloadValidator {
    private KhqrPayloadValidator() {}

    public static void validate(String referenceQr, String candidateQr, String md5, long now) {
        if (referenceQr == null || candidateQr == null || !candidateQr.matches("[\\x20-\\x7E]+")) {
            throw new IllegalArgumentException("Invalid KHQR payload");
        }
        Map<String, String> reference = fields(referenceQr);
        Map<String, String> candidate = fields(candidateQr);
        if (!"12".equals(candidate.get("01")) || !candidateQr.endsWith("6304" + candidate.get("63"))) {
            throw new IllegalArgumentException("A dynamic KHQR with a checksum is required");
        }
        String checksumInput = candidateQr.substring(0, candidateQr.length() - 4);
        int crc = 0xffff;
        for (byte value : checksumInput.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (value & 0xff) << 8;
            for (int bit = 0; bit < 8; bit++) crc = ((crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1) & 0xffff;
        }
        if (!String.format("%04X", crc).equalsIgnoreCase(candidate.get("63"))) {
            throw new IllegalArgumentException("Invalid KHQR checksum");
        }
        try {
            String actualMd5 = HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest(candidateQr.getBytes(StandardCharsets.UTF_8)));
            if (!actualMd5.equalsIgnoreCase(md5)) throw new IllegalArgumentException("Invalid KHQR MD5");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 unavailable", ex);
        }
        try {
            if (new BigDecimal(reference.remove("54")).compareTo(new BigDecimal(candidate.remove("54"))) != 0) {
                throw new IllegalArgumentException("KHQR amount does not match the payment");
            }
            Map<String, String> times = fields(candidate.remove("99"));
            Map<String, String> originalTimes = fields(reference.remove("99"));
            long expiry = Long.parseLong(times.get("01"));
            long created = Long.parseLong(times.get("00"));
            if (times.size() != 2 || created > now + 10_000 || created < now - 60_000
                    || expiry <= now || expiry > now + 300_000
                    || expiry > Long.parseLong(originalTimes.get("01"))) {
                throw new IllegalArgumentException("KHQR expiry is outside the payment session");
            }
        } catch (NullPointerException | NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid KHQR amount or timestamps");
        }
        reference.remove("63");
        candidate.remove("63");
        // Every other field (merchant account, currency, bill, etc.) must be identical.
        if (!reference.equals(candidate)) throw new IllegalArgumentException("KHQR details do not match the payment");
    }

    private static Map<String, String> fields(String payload) {
        if (payload == null) throw new IllegalArgumentException("Missing KHQR fields");
        Map<String, String> fields = new LinkedHashMap<>();
        for (int index = 0; index < payload.length();) {
            if (index + 4 > payload.length() || !payload.substring(index, index + 4).matches("[0-9]{4}")) {
                throw new IllegalArgumentException("Invalid KHQR field");
            }
            String tag = payload.substring(index, index + 2);
            int length = Integer.parseInt(payload.substring(index + 2, index + 4));
            index += 4;
            if (index + length > payload.length() || fields.putIfAbsent(tag, payload.substring(index, index + length)) != null) {
                throw new IllegalArgumentException("Duplicate or truncated KHQR field");
            }
            index += length;
        }
        return fields;
    }
}
