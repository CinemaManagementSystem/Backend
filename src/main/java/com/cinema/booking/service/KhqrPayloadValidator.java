package com.cinema.booking.service;

public interface KhqrPayloadValidator {

    void validate(String referenceQr, String candidateQr, String md5, long now);
}
