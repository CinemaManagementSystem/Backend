package com.cinema.booking.service.impl;

import com.cinema.booking.config.KhqrConfig;
import com.cinema.booking.dto.payments.BakongCheckResult;
import com.cinema.booking.dto.payments.KhqrPayload;
import com.cinema.booking.service.BakongService;
import com.cinema.booking.service.BakongRequestBudgetService;
import jakarta.annotation.PostConstruct;
import kh.gov.nbc.bakong_khqr.BakongKHQR;
import kh.gov.nbc.bakong_khqr.model.IndividualInfo;
import kh.gov.nbc.bakong_khqr.model.KHQRCurrency;
import kh.gov.nbc.bakong_khqr.model.KHQRData;
import kh.gov.nbc.bakong_khqr.model.KHQRResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class BakongServiceImpl implements BakongService {

    private static final ZoneId PHNOM_PENH_ZONE = ZoneId.of("Asia/Phnom_Penh");
    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_MERCHANT_NAME = "Cinema Booking System";
    private static final String DEFAULT_MERCHANT_CITY = "Phnom Penh";
    private static final String DEFAULT_STORE_LABEL = "Cinema Booking";
    private static final String TERMINAL_LABEL = "POS-01";
    private static final String MOCK_PAID_MD5 = "deadbeefdeadbeefdeadbeefdeadbeef";
    private static final String GENERATION_ERROR_MESSAGE = "Failed to generate KHQR";
    private static final String CONFIGURATION_ERROR_MESSAGE = "Payment service is not configured";
    private static final String LIVE_CHECK_ERROR_MESSAGE = "Unable to verify payment at this time";
    private static final String INCOMPLETE_RESPONSE_MESSAGE = "Incomplete Bakong response";
    private static final String INVALID_MD5_MESSAGE = "Invalid KHQR MD5 hash";
    private static final int DEFAULT_EXPIRY_MINUTES = 5;
    private static final int MAX_EXPIRY_MINUTES = 10;
    private static final int EXPIRY_SAFETY_SECONDS = 1;
    private static final int MAX_BILL_NUMBER_LENGTH = 25;
    private static final int MAX_STORE_LABEL_LENGTH = 25;
    private static final int MAX_LIVE_CHECK_ATTEMPTS = 2;
    private static final String PRODUCTION_BASE_URL = "https://api-bakong.nbc.gov.kh";
    private static final String SIT_BASE_URL = "https://sit-api-bakong.nbc.gov.kh";
    /** Refresh the token this many seconds before its stated expiry to avoid
     *  clock-skew races at the boundary. */
    private static final int TOKEN_EXPIRY_BUFFER_SECONDS = 60;

    private final KhqrConfig khqrConfig;
    private final BakongRequestBudgetService requestBudgetService;
    private final RestClient restClient = RestClient.builder().build();

    @Autowired
    public BakongServiceImpl(KhqrConfig khqrConfig, BakongRequestBudgetService requestBudgetService) {
        this.khqrConfig = khqrConfig;
        this.requestBudgetService = requestBudgetService;
    }

    /** Test-friendly constructor; production always uses the database-backed budget. */
    public BakongServiceImpl(KhqrConfig khqrConfig) {
        this(khqrConfig, null);
    }

    // -----------------------------------------------------------------------
    // Token cache — refreshed automatically when stale or after a 401.
    // volatile so single-write visibility is guaranteed without a full lock.
    // -----------------------------------------------------------------------
    private volatile String cachedToken;
    private volatile long tokenExpiryEpochSeconds = 0;
    private volatile boolean bootstrapTokenInvalidated = false;

    public String getCachedToken() {
        return cachedToken;
    }

    public void setCachedToken(String cachedToken) {
        this.cachedToken = cleanToken(cachedToken);
        if (this.cachedToken != null) {
            this.bootstrapTokenInvalidated = false;
        }
    }

    public long getTokenExpiryEpochSeconds() {
        return tokenExpiryEpochSeconds;
    }

    public void setTokenExpiryEpochSeconds(long tokenExpiryEpochSeconds) {
        this.tokenExpiryEpochSeconds = tokenExpiryEpochSeconds;
    }

    // -----------------------------------------------------------------------
    // Startup validation
    // -----------------------------------------------------------------------

    @PostConstruct
    public void validateConfiguration() {
        if (khqrConfig.isMockMode()) {
            log.info("Bakong running in mock mode — live API credentials are not required");
            return;
        }
        boolean hasStaticToken = hasText(khqrConfig.getToken());
        boolean hasEmail       = hasText(khqrConfig.getEmail());

        if (!hasText(khqrConfig.getBaseUrl())) {
            log.warn("Bakong live verification is not configured: bakong.base-url is missing. "
                    + "KHQR verification will return VERIFICATION_ERROR until configuration is fixed.");
            return;
        }
        if (!hasEmail) {
            log.warn("Bakong live verification is not fully configured: bakong.email is missing. "
                    + "KHQR verification will return VERIFICATION_ERROR until configuration is fixed.");
        }
        if (!hasStaticToken) {
            if (!hasText(khqrConfig.getOrganization()) || !hasText(khqrConfig.getProject())) {
                log.warn("Bakong live verification is not fully configured: bakong.token is empty and "
                        + "bakong.organization/bakong.project are missing. KHQR verification will return "
                        + "VERIFICATION_ERROR until configuration is fixed.");
            }
            log.info("Bakong static bootstrap token is not configured; first live verification will request a token "
                    + "from POST /v1/request_token and may require POST /v1/verify");
        }
        log.info("Bakong token lifecycle uses POST /v1/request_token for initial registration, "
                + "POST /v1/verify for email code verification, and POST /v1/renew_token only for an existing token.");

        String baseUrl = khqrConfig.getBaseUrl();
        if (hasText(baseUrl)) {
            boolean isSit = baseUrl.contains("sit-api-bakong");
            log.info("Bakong environment: {} (baseUrl={})", isSit ? "SIT/sandbox" : "production", baseUrl);
            if (hasStaticToken && isEnvironmentMismatch(baseUrl, khqrConfig.getToken())) {
                log.warn("Bakong sandbox/production configuration mismatch detected: baseUrl [{}] environment "
                        + "conflicts with static token environment.", baseUrl);
            }
        }

        // Seed the in-memory cache from the static bootstrap token so the very
        // first request does not need an initial registration round-trip.
        if (hasStaticToken) {
            String clean = cleanToken(khqrConfig.getToken());
            long exp = extractJwtExpiry(clean);
            this.cachedToken = clean;
            this.tokenExpiryEpochSeconds = exp;
            log.info("Bakong bootstrap token loaded from config: tokenLength={}, expiryEpochSeconds={}",
                    clean.length(), exp > 0 ? exp : "unknown");
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    @Override
    public KhqrPayload generateDynamicKhqr(BigDecimal amount, String currency, String billNumber, String description) {
        return generateDynamicKhqr(amount, currency, billNumber, description, null, null);
    }

    @Override
    public KhqrPayload generateDynamicKhqr(BigDecimal amount, String currency, String billNumber, String description, String customAccountId, String customMerchantName) {
        return generateDynamicKhqr(amount, currency, billNumber, description,
                customAccountId, customMerchantName, null);
    }

    @Override
    public KhqrPayload generateDynamicKhqr(BigDecimal amount, String currency, String billNumber, String description,
                                           String customAccountId, String customMerchantName, LocalDateTime requestedExpiresAt) {
        validateAmount(amount);

        String accountId    = resolveAccountId(customAccountId);
        String merchantName = resolveTextWithDefault(customMerchantName, khqrConfig.getMerchantName(), DEFAULT_MERCHANT_NAME);
        String merchantCity = resolveTextWithDefault(khqrConfig.getMerchantCity(), null, DEFAULT_MERCHANT_CITY);
        String normalizedCurrency = normalizeCurrency(currency);
        KHQRCurrency khqrCurrency = "KHR".equals(normalizedCurrency) ? KHQRCurrency.KHR : KHQRCurrency.USD;
        String bill       = normalizeLabel(billNumber, "BILL-" + System.currentTimeMillis(), MAX_BILL_NUMBER_LENGTH);
        String storeLabel = normalizeLabel(description, DEFAULT_STORE_LABEL, MAX_STORE_LABEL_LENGTH);

        LocalDateTime now = LocalDateTime.now(PHNOM_PENH_ZONE);
        LocalDateTime configuredExpiry = now.plusMinutes(resolveExpiryMinutes())
                .minusSeconds(EXPIRY_SAFETY_SECONDS);
        LocalDateTime expiresAt = requestedExpiresAt != null && requestedExpiresAt.isBefore(configuredExpiry)
                ? requestedExpiresAt
                : configuredExpiry;
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("KHQR expiry must be in the future");
        }
        long expirationEpochMillis = expiresAt.atZone(PHNOM_PENH_ZONE).toInstant().toEpochMilli();

        log.info("Generating KHQR: amount={}, currency={}, billNumber={}", amount, normalizedCurrency, bill);

        IndividualInfo individualInfo = new IndividualInfo();
        individualInfo.setBakongAccountId(accountId);
        individualInfo.setMerchantName(merchantName);
        individualInfo.setMerchantCity(merchantCity);
        individualInfo.setCurrency(khqrCurrency);
        individualInfo.setAmount(amount.doubleValue());
        individualInfo.setBillNumber(bill);
        individualInfo.setStoreLabel(storeLabel);
        individualInfo.setTerminalLabel(TERMINAL_LABEL);
        individualInfo.setExpirationTimestamp(expirationEpochMillis);

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
        String md5Hash    = khqrData.getMd5();

        if (!hasText(khqrString) || !isValidMd5(md5Hash)) {
            log.error("Bakong KHQR SDK returned incomplete QR data");
            throw new IllegalStateException(GENERATION_ERROR_MESSAGE);
        }

        KHQRResponse verifyResponse = verifyGeneratedKhqr(khqrString);
        var verifyStatus = verifyResponse.getKHQRStatus();
        if (verifyStatus == null) {
            log.warn("Generated KHQR verification returned no status");
        } else if (verifyStatus.getCode() != 0) {
            log.warn("Generated KHQR failed verification: code={}, message={}",
                    verifyStatus.getCode(), safeStatusMessage(verifyStatus.getMessage()));
        }

        log.info("Generated KHQR MD5: {}", maskHash(md5Hash));
        log.info("Application payment expiry: {}", expiresAt);

        return new KhqrPayload(khqrString, md5Hash, expiresAt, amount, normalizedCurrency, bill);
    }

    @Override
    public BakongCheckResult checkTransactionByMd5(String md5Hash) {
        if (!isValidMd5(md5Hash)) {
            log.warn("Rejected Bakong MD5 check because the hash format is invalid: md5={}", maskHash(md5Hash));
            return BakongCheckResult.failed(md5Hash, INVALID_MD5_MESSAGE);
        }

        String normalizedMd5 = md5Hash;

        if (khqrConfig.isMockMode()) {
            log.debug("Bakong running in mock mode for MD5 check: {}", maskHash(normalizedMd5));
            if (MOCK_PAID_MD5.equalsIgnoreCase(normalizedMd5)) {
                return BakongCheckResult.paid(normalizedMd5, "mock_customer@bakong", khqrConfig.getAccountId());
            }
            return BakongCheckResult.pending(normalizedMd5, "Transaction is pending in mock mode");
        }

        // ------------------------------------------------------------------
        // Live Bakong Open API: resolve a valid token, then POST to verify.
        // On HTTP 401: invalidate cache → request a new token → retry once.
        // ------------------------------------------------------------------
        try {
            String token = resolveToken("initial");
            logTokenDiagnostics(token, "initial");

            if (!hasText(token)) {
                log.error("Bakong token is null or blank after resolution — cannot perform verification");
                return BakongCheckResult.verificationError(normalizedMd5, CONFIGURATION_ERROR_MESSAGE);
            }

            String url = khqrConfig.getBaseUrl() + "/v1/check_transaction_by_md5";
            log.info("Bakong verification request endpoint: {}", url);
            log.info("Bakong verification authorization scheme: Bearer");

            Map<?, ?> response = null;
            for (int attempt = 1; attempt <= MAX_LIVE_CHECK_ATTEMPTS; attempt++) {
                try {
                    if (requestBudgetService != null) {
                        BakongRequestBudgetService.Permit permit = requestBudgetService.tryAcquire();
                        if (!permit.allowed()) {
                            log.warn("Bakong verification skipped by global request budget: retryAfterSeconds={}",
                                    permit.retryAfterSeconds());
                            return BakongCheckResult.rateLimited(normalizedMd5, permit.message(),
                                    permit.retryAfterSeconds());
                        }
                    }
                    log.info("Bakong verification attempt {}/{}: md5={}", attempt, MAX_LIVE_CHECK_ATTEMPTS,
                            maskHash(normalizedMd5));
                    boolean hasAuthHeader = hasText(token);
                    log.info("Bakong outgoing request contains Authorization header: {}, scheme: Bearer", hasAuthHeader);

                    response = createRestClient().post()
                            .uri(url)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("md5", normalizedMd5))
                            .retrieve()
                            .body(Map.class);
                    log.info("Bakong verification HTTP status: 200 (attempt {})", attempt);
                    break; // success — exit retry loop

                } catch (RestClientResponseException ex) {
                    log.warn("Bakong verification HTTP status: {} (attempt {})",
                            ex.getStatusCode().value(), attempt);

                    Long retryAfterSeconds = retryAfterSeconds(ex);
                    if (ex.getStatusCode().value() == 429) {
                        log.warn("Bakong verification rate limited; retryAfterSeconds={}", retryAfterSeconds);
                        return BakongCheckResult.rateLimited(normalizedMd5,
                                "Bakong verification is temporarily unavailable. Please try again later.",
                                retryAfterSeconds);
                    }

                    if (ex.getStatusCode().value() == 401 && attempt < MAX_LIVE_CHECK_ATTEMPTS) {
                        log.warn("Bakong verification returned HTTP 401 on attempt {}; "
                                + "invalidating cached token and requesting a fresh one. md5={}",
                                attempt, maskHash(normalizedMd5));

                        // 1. Invalidate cached Bakong token
                        invalidateCachedToken();

                        // 2. Request a new Bakong token using endpoint & credentials
                        String refreshed = requestNewToken();
                        logTokenDiagnostics(refreshed, "refresh");

                        if (!hasText(refreshed)) {
                            log.warn("Bakong token refresh failed; cannot retry verification. md5={}",
                                    maskHash(normalizedMd5));
                            return BakongCheckResult.verificationError(normalizedMd5, LIVE_CHECK_ERROR_MESSAGE);
                        }

                        // 3. Store new token and expiry time (handled in requestNewToken)
                        // 4. Build a completely new verification request
                        // 5. Attach the refreshed token to the request
                        token = refreshed;
                        log.info("Bakong token refreshed successfully; retrying verification with refreshed token. md5={}",
                                maskHash(normalizedMd5));
                        continue; // 6. Retry exactly once
                    }
                    throw ex; // second 401 or non-401 error — propagate to outer catch
                }
            }

            if (response == null) {
                log.warn("Bakong API returned an empty response for MD5 {}", maskHash(normalizedMd5));
                return BakongCheckResult.verificationError(normalizedMd5, LIVE_CHECK_ERROR_MESSAGE);
            }

            return interpretResponse(response, normalizedMd5);

        } catch (RestClientResponseException ex) {
            log.warn("Bakong API returned a retryable error while checking MD5 {}: status={}, responseBodyPresent={}",
                    maskHash(normalizedMd5), ex.getStatusCode(), hasText(ex.getResponseBodyAsString()));
            Long retryAfterSeconds = retryAfterSeconds(ex);
            if (ex.getStatusCode().value() == 429) {
                return BakongCheckResult.rateLimited(normalizedMd5,
                        "Bakong verification is temporarily unavailable. Please try again later.",
                        retryAfterSeconds);
            }
            return BakongCheckResult.verificationError(normalizedMd5, LIVE_CHECK_ERROR_MESSAGE, retryAfterSeconds);
        } catch (RestClientException ex) {
            log.warn("Failed to check Bakong transaction by MD5 {} from live API: {}",
                    maskHash(normalizedMd5), ex.getMessage());
            return BakongCheckResult.verificationError(normalizedMd5, LIVE_CHECK_ERROR_MESSAGE);
        } catch (Exception ex) {
            log.warn("Unexpected failure while checking Bakong transaction by MD5 {}: {}",
                    maskHash(normalizedMd5), ex.getMessage());
            return BakongCheckResult.verificationError(normalizedMd5, LIVE_CHECK_ERROR_MESSAGE);
        }
    }

    // -----------------------------------------------------------------------
    // Token lifecycle
    // -----------------------------------------------------------------------

    /**
     * Returns a usable Bakong JWT.
     * <ol>
     *   <li>If the in-memory token is present and not about to expire → return it (source=cache).</li>
     *   <li>If no token exists, start the {@code /v1/request_token} registration flow.</li>
     *   <li>If an existing token expired, fetch a new token from {@code /v1/renew_token}.</li>
     *   <li>If refresh fails, return {@code null}; stale tokens are not reused.</li>
     * </ol>
     */
    public String resolveToken(String callerHint) {
        if (!hasText(cachedToken) && !bootstrapTokenInvalidated && hasText(khqrConfig.getToken())) {
            String clean = cleanToken(khqrConfig.getToken());
            if (hasText(clean)) {
                this.cachedToken = clean;
                this.tokenExpiryEpochSeconds = extractJwtExpiry(clean);
            }
        }

        long nowEpoch = Instant.now().getEpochSecond();
        boolean cached = hasText(cachedToken)
                && (tokenExpiryEpochSeconds == 0
                    || nowEpoch < tokenExpiryEpochSeconds - TOKEN_EXPIRY_BUFFER_SECONDS);

        if (cached) {
            log.info("Bakong token source: cache (callerHint={})", callerHint);
            return cachedToken;
        }

        if (!hasText(cachedToken)) {
            log.info("Bakong token source: initial-request (cached=false, callerHint={})", callerHint);
            return requestInitialToken();
        }

        log.info("Bakong token source: refresh (cached=true, callerHint={})", callerHint);
        String fresh = requestNewToken();
        if (hasText(fresh)) {
            return fresh;
        }

        log.warn("Bakong token refresh failed; no usable token will be returned");
        return null;
    }

    /**
     * Starts the official NBC Bakong initial token registration flow.
     * This does not call /v1/renew_token because renewal is only for already
     * registered/expired tokens.
     */
    public String requestInitialToken() {
        String email = khqrConfig.getEmail();
        String organization = khqrConfig.getOrganization();
        String project = khqrConfig.getProject();

        if (!hasText(email) || !hasText(organization) || !hasText(project)) {
            log.warn("Bakong initial token request skipped: email/organization/project must be configured.");
            return null;
        }

        String tokenUrl = khqrConfig.getBaseUrl() + "/v1/request_token";
        Map<String, String> requestBody = Map.of(
                "email", email,
                "organization", organization,
                "project", project
        );

        log.info("Bakong initial token request started: endpoint={}", tokenUrl);

        try {
            Map<?, ?> body = createRestClient().post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            log.info("Bakong initial token request HTTP status: 200");
            String token = extractAndCacheToken(body, "initial-token");
            if (hasText(token)) {
                return token;
            }

            if (body != null && isSuccessResponse(body.get("responseCode"))) {
                log.warn("Bakong initial token request accepted but no token was returned. "
                        + "Check the registered email for the 20-character verification code.");
            }

            if (hasText(khqrConfig.getVerificationCode())) {
                return verifyInitialToken();
            }
            log.warn("Bakong verification code is not configured; set BAKONG_VERIFICATION_CODE after receiving the email code.");
            return null;
        } catch (RestClientResponseException ex) {
            log.warn("Bakong initial token request HTTP status: {} — initial token request failed",
                    ex.getStatusCode().value());
            return null;
        } catch (RestClientException ex) {
            log.warn("Bakong initial token request network error: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Verifies the email code from /v1/request_token and caches the issued token.
     */
    public String verifyInitialToken() {
        String code = khqrConfig.getVerificationCode();
        if (!hasText(code)) {
            log.warn("Bakong token verification skipped: verification code is not configured.");
            return null;
        }

        String tokenUrl = khqrConfig.getBaseUrl() + "/v1/verify";
        log.info("Bakong token verification started: endpoint={}", tokenUrl);

        try {
            Map<?, ?> body = createRestClient().post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("code", code.trim()))
                    .retrieve()
                    .body(Map.class);

            log.info("Bakong token verification HTTP status: 200");
            return extractAndCacheToken(body, "verify-token");
        } catch (RestClientResponseException ex) {
            log.warn("Bakong token verification HTTP status: {} — token verification failed",
                    ex.getStatusCode().value());
            return null;
        } catch (RestClientException ex) {
            log.warn("Bakong token verification network error: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Calls official NBC Bakong {@code POST /v1/renew_token} with email for
     * an existing registered token, stores the returned JWT in cache, and returns it.
     */
    public String requestNewToken() {
        String email = khqrConfig.getEmail();

        if (!hasText(email)) {
            log.warn("Bakong token refresh skipped: email is not configured (set BAKONG_EMAIL).");
            return null;
        }

        String tokenUrl = khqrConfig.getBaseUrl() + "/v1/renew_token";
        Map<String, String> requestBody = Map.of("email", email);

        log.info("Bakong token request started: endpoint={}", tokenUrl);

        try {
            Map<?, ?> body = createRestClient().post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            log.info("Bakong token request HTTP status: 200");

            return extractAndCacheToken(body, "renew-token");

        } catch (RestClientResponseException ex) {
            log.warn("Bakong token request HTTP status: {} — token refresh failed",
                    ex.getStatusCode().value());
            return null;
        } catch (RestClientException ex) {
            log.warn("Bakong token request network error: {}", ex.getMessage());
            return null;
        } catch (Exception ex) {
            log.warn("Bakong token request unexpected error: {}", ex.getMessage());
            return null;
        }
    }

    /** Clears the cached token so the next call to resolveToken forces a refresh. */
    public void invalidateCachedToken() {
        log.info("Bakong cached token invalidated");
        this.cachedToken             = null;
        this.tokenExpiryEpochSeconds = 0;
        this.bootstrapTokenInvalidated = true;
    }

    private String extractAndCacheToken(Map<?, ?> body, String source) {
        if (body == null) {
            log.warn("Bakong {} endpoint returned null body", source);
            return null;
        }

        Object responseCode = body.get("responseCode");
        String responseMessage = asString(body.get("responseMessage"));
        log.info("Bakong {} response: responseCode={}, responseMessage={}",
                source, responseCode, responseMessage);

        if (!isSuccessResponse(responseCode)) {
            log.warn("Bakong {} returned non-zero responseCode: {}", source, responseMessage);
            return null;
        }

        String rawToken = null;
        Object data = body.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            rawToken = asString(dataMap.get("token"));
        }
        if (!hasText(rawToken)) {
            rawToken = asString(body.get("token"));
        }

        boolean tokenAvailable = hasText(rawToken);
        log.info("Bakong {} token available: {}", source, tokenAvailable);
        if (!tokenAvailable) {
            return null;
        }

        String clean = cleanToken(rawToken);
        long exp = extractJwtExpiry(clean);

        this.cachedToken = clean;
        this.tokenExpiryEpochSeconds = exp;
        this.bootstrapTokenInvalidated = false;

        log.info("Bakong token received successfully: source={}, tokenLength={}, tokenExpiryEpochSeconds={}",
                source, clean.length(), exp > 0 ? exp : "not-parsed");
        if (exp > 0) {
            LocalDateTime expiryTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(exp), PHNOM_PENH_ZONE);
            log.info("Bakong token expiry time (Asia/Phnom_Penh): {}", expiryTime);
        }

        return clean;
    }

    // -----------------------------------------------------------------------
    // Protected hooks (overridable in tests)
    // -----------------------------------------------------------------------

    protected RestClient createRestClient() {
        return restClient;
    }

    protected KHQRResponse verifyGeneratedKhqr(String khqrString) {
        return BakongKHQR.verify(khqrString);
    }

    // -----------------------------------------------------------------------
    // Diagnostic helpers
    // -----------------------------------------------------------------------

    private void logTokenDiagnostics(String token, String source) {
        boolean blank = !hasText(token);
        log.info("Bakong token diagnostics [{}]: isNullOrBlank={}, tokenLength={}, tokenExpiryEpochSeconds={}",
                source,
                blank,
                blank ? 0 : token.length(),
                tokenExpiryEpochSeconds > 0 ? tokenExpiryEpochSeconds : "unknown");
        if (tokenExpiryEpochSeconds > 0) {
            LocalDateTime expiryTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(tokenExpiryEpochSeconds), PHNOM_PENH_ZONE);
            log.info("Bakong token expiry time (Asia/Phnom_Penh): {}", expiryTime);
        }
    }

    // -----------------------------------------------------------------------
    // Response interpretation
    // -----------------------------------------------------------------------

    private BakongCheckResult interpretResponse(Map<?, ?> response, String md5) {
        Object responseCode    = response.get("responseCode");
        String responseMessage = asString(response.get("responseMessage"));
        log.debug("Bakong API response: md5={}, responseCode={}, responseMessage={}",
                maskHash(md5), responseCode, responseMessage);

        if (isSuccessResponse(responseCode)) {
            Map<?, ?> dataMap = asMap(response.get("data"));
            if (dataMap == null) {
                log.warn("Bakong API success response for MD5 {} did not include payment data",
                        maskHash(md5));
                return BakongCheckResult.pending(md5, INCOMPLETE_RESPONSE_MESSAGE);
            }

            String fromAccount = asString(dataMap.get("fromAccountId"));
            String toAccount   = asString(dataMap.get("toAccountId"));
            String hash        = asString(dataMap.get("hash"));
            BigDecimal amount  = asBigDecimal(dataMap.get("amount"));
            String currency    = asString(dataMap.get("currency"));

            log.info("Bakong transaction confirmed for MD5 {}", maskHash(md5));
            return BakongCheckResult.paid(hash, fromAccount, toAccount, amount, currency);
        }

        if (isRateLimitedResponse(responseMessage)) {
            log.warn("Bakong API rate limit reached while checking MD5 {}: {}", maskHash(md5), responseMessage);
            return BakongCheckResult.rateLimited(md5, responseMessage, null);
        }

        if (isNotFoundResponse(responseMessage)) {
            return BakongCheckResult.notFound(md5);
        }

        return BakongCheckResult.pending(md5, hasText(responseMessage) ? responseMessage : "Pending");
    }

    // -----------------------------------------------------------------------
    // Private utilities
    // -----------------------------------------------------------------------

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
    }

    private String resolveAccountId(String customAccountId) {
        if (hasText(customAccountId)) return customAccountId.trim();
        if (hasText(khqrConfig.getAccountId())) return khqrConfig.getAccountId().trim();
        throw new IllegalStateException("Bakong account ID is not configured");
    }

    private String normalizeCurrency(String currency) {
        if (!hasText(currency)) return DEFAULT_CURRENCY;
        String n = currency.trim().toUpperCase(Locale.ROOT);
        if ("USD".equals(n) || "KHR".equals(n)) return n;
        throw new IllegalArgumentException("Unsupported currency: " + n);
    }

    private int resolveExpiryMinutes() {
        int v = khqrConfig.getExpiryMinutes();
        return Math.min(v > 0 ? v : DEFAULT_EXPIRY_MINUTES, MAX_EXPIRY_MINUTES);
    }

    private String normalizeLabel(String value, String fallback, int maxLength) {
        return trimToLength(hasText(value) ? value.trim() : fallback, maxLength);
    }

    private String resolveTextWithDefault(String primary, String fallback, String defaultValue) {
        if (hasText(primary)) return primary.trim();
        if (hasText(fallback)) return fallback.trim();
        return defaultValue;
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) return null;
        String t = value.trim();
        return t.length() <= maxLength ? t : t.substring(0, maxLength);
    }

    /**
     * Removes leading/trailing whitespace, quotation marks, and a "Bearer " prefix (case-insensitive)
     * from a raw token string.
     */
    public static String cleanToken(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            if (t.length() >= 2) {
                t = t.substring(1, t.length() - 1).trim();
            }
        }
        if (t.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            t = t.substring(7).trim();
        }
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            if (t.length() >= 2) {
                t = t.substring(1, t.length() - 1).trim();
            }
        }
        return t;
    }

    public static String stripBearerPrefix(String raw) {
        return cleanToken(raw);
    }

    /**
     * Base64-decodes the JWT payload section and extracts the {@code exp} claim.
     * Returns {@code 0} if the token is not a parseable JWT.
     */
    public static long extractJwtExpiry(String token) {
        if (!hasTextStatic(token)) return 0;
        String clean = cleanToken(token);
        String[] parts = clean.split("\\.");
        if (parts.length < 2) return 0;
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            int expIdx = payload.indexOf("\"exp\"");
            if (expIdx < 0) return 0;
            int colonIdx = payload.indexOf(':', expIdx);
            if (colonIdx < 0) return 0;
            int start = colonIdx + 1;
            while (start < payload.length() && (payload.charAt(start) == ' ' || payload.charAt(start) == '\t')) start++;
            int end = start;
            while (end < payload.length() && Character.isDigit(payload.charAt(end))) end++;
            if (end == start) return 0;
            return Long.parseLong(payload.substring(start, end));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Returns a rough hint about which environment issued the JWT
     * (looks for "sit" or "prod" in the payload). Returns {@code null}
     * if the token is not parseable or contains no hint.
     */
    public static String extractJwtEnvironmentHint(String token) {
        if (!hasTextStatic(token)) return null;
        String clean = cleanToken(token);
        String[] parts = clean.split("\\.");
        if (parts.length < 2) return null;
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            String payload = new String(bytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (payload.contains("sit-api-bakong") || payload.contains("\"env\":\"sit\"") || payload.contains("\"env\": \"sit\"")) {
                return "sit";
            }
            if (payload.contains("api-bakong") || payload.contains("\"env\":\"prod\"") || payload.contains("\"env\": \"prod\"")) {
                return "production";
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks whether the base URL environment (SIT sandbox vs production)
     * mismatches the token's environment hint.
     */
    public static boolean isEnvironmentMismatch(String baseUrl, String token) {
        if (!hasTextStatic(baseUrl) || !hasTextStatic(token)) {
            return false;
        }
        boolean isSitUrl = baseUrl.toLowerCase(Locale.ROOT).contains("sit");
        String envHint = extractJwtEnvironmentHint(token);
        if (envHint == null) {
            return false;
        }
        boolean isSitToken = "sit".equals(envHint);
        return isSitUrl != isSitToken;
    }

    private static String padBase64(String s) {
        return switch (s.length() % 4) {
            case 2  -> s + "==";
            case 3  -> s + "=";
            default -> s;
        };
    }

    private boolean isSuccessResponse(Object responseCode) {
        if (responseCode instanceof Number n) return n.intValue() == 0;
        return responseCode != null && "0".equals(responseCode.toString().trim());
    }

    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> m ? m : null;
    }

    private String asString(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hasTextStatic(String value) {
        return value != null && !value.isBlank();
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isValidMd5(String value) {
        return value != null && value.matches("[0-9a-fA-F]{32}");
    }

    private boolean isNotFoundResponse(String msg) {
        if (!hasText(msg)) return false;
        String n = msg.toLowerCase(Locale.ROOT);
        return n.contains("not found") || n.contains("not exist") || n.contains("no transaction");
    }

    private boolean isRateLimitedResponse(String msg) {
        if (!hasText(msg)) return false;
        String n = msg.toLowerCase(Locale.ROOT);
        return n.contains("daily request limit") || n.contains("rate limit") || n.contains("too many request");
    }

    private Long retryAfterSeconds(RestClientResponseException ex) {
        if (ex.getResponseHeaders() == null) return null;
        String value = ex.getResponseHeaders().getFirst("Retry-After");
        if (!hasText(value)) return null;
        try {
            return Math.max(1L, Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            try {
                ZonedDateTime retryAt = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
                return Math.max(1L, retryAt.toEpochSecond() - Instant.now().getEpochSecond());
            } catch (DateTimeParseException ignoredDate) {
                log.warn("Bakong returned an invalid Retry-After header");
                return null;
            }
        }
    }

    private String safeStatusMessage(String message) {
        return hasText(message) ? message : "Unknown error during KHQR generation";
    }

    private String maskHash(String hash) {
        if (!hasText(hash)) return "<missing>";
        String n = hash.trim();
        if (n.length() <= 8) return "****";
        return n.substring(0, 4) + "..." + n.substring(n.length() - 4);
    }

    // -----------------------------------------------------------------------
    // Public static utilities (used by other layers and tests)
    // -----------------------------------------------------------------------

    public static String calculateCrc16(String input) {
        int crc = 0xFFFF;
        int polynomial = 0x1021;
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            for (int i = 0; i < 8; i++) {
                boolean bit = ((b >> (7 - i)) & 1) == 1;
                boolean c15 = ((crc >> 15) & 1) == 1;
                crc <<= 1;
                if (c15 ^ bit) crc ^= polynomial;
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
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm unavailable", e);
        }
    }
}
