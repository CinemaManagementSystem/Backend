package com.cinema.booking.service.impl;

import com.cinema.booking.dto.auth.OtpResponseDto;
import com.cinema.booking.dto.auth.OtpSendRequestDto;
import com.cinema.booking.dto.auth.OtpVerifyRequestDto;
import com.cinema.booking.exception.RateLimitExceededException;
import com.cinema.booking.service.OtpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
public class OtpServiceImpl implements OtpService {

    private static final int OTP_EXPIRY_SECONDS = 300; // 5 minutes
    private static final int MIN_RESEND_INTERVAL_SECONDS = 60; // 1 minute cooldown
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, OtpRecord> otpStore = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:measm2519@gmail.com}")
    private String senderEmail;

    private record OtpRecord(
            String code,
            Instant createdAt,
            Instant expiresAt,
            int attemptCount
    ) {
        OtpRecord withIncrementedAttempt() {
            return new OtpRecord(code, createdAt, expiresAt, attemptCount + 1);
        }
    }

    @Override
    public OtpResponseDto sendOtp(OtpSendRequestDto dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();
        Instant now = Instant.now();

        OtpRecord existing = otpStore.get(normalizedEmail);
        if (existing != null && existing.expiresAt().isAfter(now)) {
            long elapsedSinceCreation = now.getEpochSecond() - existing.createdAt().getEpochSecond();
            if (elapsedSinceCreation < MIN_RESEND_INTERVAL_SECONDS) {
                long waitTime = MIN_RESEND_INTERVAL_SECONDS - elapsedSinceCreation;
                throw new RateLimitExceededException("Please wait " + waitTime + " seconds before requesting another code.");
            }
        }

        // Generate cryptographically secure 6-digit numeric OTP (100000 - 999999)
        int codeInt = 100000 + secureRandom.nextInt(900000);
        String code = String.valueOf(codeInt);

        OtpRecord newRecord = new OtpRecord(
                code,
                now,
                now.plusSeconds(OTP_EXPIRY_SECONDS),
                0
        );
        otpStore.put(normalizedEmail, newRecord);

        log.info("Generated OTP for email {}: {} (valid for {}s)", maskEmail(normalizedEmail), code, OTP_EXPIRY_SECONDS);

        // Dispatch real email via Gmail SMTP
        sendEmailOtp(normalizedEmail, code);

        return OtpResponseDto.builder()
                .message("A 6-digit verification code has been sent to your email.")
                .email(normalizedEmail)
                .devOtp(code) // Provided for dev/testing ease
                .expiresInSeconds(OTP_EXPIRY_SECONDS)
                .build();
    }

    @Override
    public boolean verifyOtp(OtpVerifyRequestDto dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();
        String candidateOtp = dto.getOtp().trim();
        Instant now = Instant.now();

        OtpRecord record = otpStore.get(normalizedEmail);
        if (record == null) {
            throw new BadCredentialsException("No verification code found for this email. Please request a new one.");
        }

        if (record.expiresAt().isBefore(now)) {
            otpStore.remove(normalizedEmail);
            throw new BadCredentialsException("Verification code has expired. Please request a new code.");
        }

        if (record.attemptCount() >= MAX_FAILED_ATTEMPTS) {
            otpStore.remove(normalizedEmail);
            throw new BadCredentialsException("Too many incorrect attempts. This code is invalidated. Please request a new one.");
        }

        if (!record.code().equals(candidateOtp)) {
            otpStore.put(normalizedEmail, record.withIncrementedAttempt());
            int remaining = MAX_FAILED_ATTEMPTS - (record.attemptCount() + 1);
            throw new BadCredentialsException("Invalid verification code. " + remaining + " attempts remaining.");
        }

        // Verification successful: consume and remove OTP to prevent replay attacks
        otpStore.remove(normalizedEmail);
        log.info("OTP verified successfully for email: {}", maskEmail(normalizedEmail));
        return true;
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredOtps() {
        Instant now = Instant.now();
        otpStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***" + email.substring(atIndex);
        return email.charAt(0) + "***" + email.substring(atIndex - 1);
    }

    private void sendEmailOtp(String toEmail, String code) {
        if (mailSender == null) {
            log.warn("JavaMailSender bean is not configured. Skipping email dispatch to {}", maskEmail(toEmail));
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail, "Cinematique");
            helper.setTo(toEmail);
            helper.setSubject("[Cinematique] Your Login Verification Code: " + code);

            String htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Login Verification Code</title>
                </head>
                <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0f1014; margin: 0; padding: 40px 20px; color: #ffffff;">
                    <div style="max-width: 500px; margin: 0 auto; background-color: #181920; border-radius: 16px; border: 1px solid #2a2d3d; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.5);">
                        <div style="background: linear-gradient(135deg, #e50914 0%, #b20710 100%); padding: 30px 20px; text-align: center;">
                            <h1 style="color: #ffffff; margin: 0; font-size: 28px; font-weight: 800; letter-spacing: 2px;">CINEMATIQUE</h1>
                            <p style="color: rgba(255, 255, 255, 0.85); margin: 6px 0 0 0; font-size: 14px;">Next-Gen Cinema Experience</p>
                        </div>
                        <div style="padding: 35px 30px; text-align: center;">
                            <h2 style="color: #ffffff; margin: 0 0 10px 0; font-size: 20px; font-weight: 600;">Sign In Verification</h2>
                            <p style="color: #9ca3af; font-size: 14px; line-height: 1.6; margin: 0 0 25px 0;">Use the 6-digit code below to securely sign into your account. This code is confidential.</p>
                            
                            <div style="background-color: #232530; border: 2px dashed #e50914; border-radius: 12px; padding: 18px 24px; display: inline-block; margin-bottom: 25px;">
                                <span style="font-family: 'Courier New', monospace; font-size: 38px; font-weight: 800; color: #ffffff; letter-spacing: 10px; display: block; padding-left: 10px;">{{OTP_CODE}}</span>
                            </div>

                            <p style="color: #ef4444; font-size: 13px; font-weight: 500; margin: 0 0 10px 0;">&#9201; Valid for 5 minutes only.</p>
                            <p style="color: #6b7280; font-size: 12px; margin: 0;">If you did not request this login code, you can safely disregard this email.</p>
                        </div>
                        <div style="background-color: #121318; padding: 18px 20px; text-align: center; border-top: 1px solid #232530;">
                            <p style="color: #6b7280; font-size: 12px; margin: 0;">&copy; 2026 Cinematique. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
                """.replace("{{OTP_CODE}}", code);

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Verification code successfully sent via email to: {}", maskEmail(toEmail));
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", maskEmail(toEmail), e.getMessage());
        }
    }

}
