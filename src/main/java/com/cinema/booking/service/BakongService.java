package com.cinema.booking.service;

import com.cinema.booking.dto.payments.BakongCheckResult;
import com.cinema.booking.dto.payments.KhqrPayload;

import java.math.BigDecimal;

public interface BakongService {

    /**
 
     * @param amount      the transaction amount
     * @param currency    currency code ("USD" or "KHR")
     * @param billNumber  unique reference / bill number
     * @param description description of transaction
     * @return KhqrPayload containing the raw KHQR string, MD5 hash, and expiry time
     */
    KhqrPayload generateDynamicKhqr(BigDecimal amount, String currency, String billNumber, String description);

    /**
     * Checks the transaction status on Bakong network by its MD5 hash.
     *
     * @param md5Hash the 32-character hex MD5 hash of the KHQR string
     * @return BakongCheckResult containing status and confirmation details
     */
    BakongCheckResult checkTransactionByMd5(String md5Hash);
}
