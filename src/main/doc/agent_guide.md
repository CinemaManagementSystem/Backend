# Agent Guide - Cinema Booking Backend

Use this guide when reviewing or changing this repository. Keep changes small, follow the existing layers, and preserve current entity and enum names unless a task explicitly requires a redesign.

## 1. Project overview

- Java 21
- Spring Boot 3.3.2
- Maven
- Spring Web, Validation, Security, Data JPA, and Scheduling
- JWT bearer authentication
- MySQL in normal environments and H2 in tests
- Lombok where it reduces boilerplate
- Springdoc OpenAPI/Swagger
- NBC Bakong KHQR SDK with backend polling

The application starts from `CinemaBookingSystemApplication`. Scheduling is enabled there with `@EnableScheduling`.

## 2. Simple architecture

Normal request flow:

```text
Controller -> Service interface -> Service implementation -> Repository -> Database
                                      |
                                      +-> Mapper
                                      +-> Other domain services when required
```

Package responsibilities under `com.cinema.booking`:

| Package | Responsibility |
|---|---|
| `controller` | HTTP routes, request validation, and response status codes |
| `dto` | Request and response models grouped by domain |
| `service` | Business interfaces |
| `service.impl` | Business rules, authorization checks, transactions, and entity coordination |
| `repository` | Spring Data JPA queries and database locks |
| `mapper` | DTO/entity conversion without repository access |
| `entity` | JPA database models |
| `enums` | Shared domain states |
| `security` | JWT authentication, authorization helpers, and rate limiting |
| `scheduler` | Background payment polling and expiration jobs |
| `config` | Typed application configuration and framework beans |
| `exception` | Application exceptions and centralized HTTP error handling |

Do not add an extra abstraction when an existing service or repository method is enough.

## 3. Coding rules

1. Controllers stay small. They validate input, call a service, and return the result.
2. Business rules belong in service implementations.
3. Use constructor injection. Existing components normally use `@RequiredArgsConstructor` and `private final` dependencies.
4. Use DTOs at API boundaries. Do not expose JPA entities directly.
5. Keep mappers free of repository calls and business decisions.
6. Throw `ResourceNotFoundException` for missing records.
7. Use `AuthorizationService` to enforce ownership and staff/admin access inside services. Controller annotations may add another security boundary.
8. Never trust client-owned totals, customer IDs, payment status, merchant accounts, or transaction identifiers.
9. Use `@Transactional` when one operation changes multiple related records.
10. Use repository locking methods such as `findByIdForUpdate` when concurrent requests could confirm, expire, or reserve the same record.
11. Keep state transitions guarded. A terminal record must not silently return to an earlier state.
12. Do not log tokens, full MD5 values, passwords, QR secrets, or sensitive account data.
13. Do not manually update the database as the permanent fix for a business-flow bug.
14. Add or update tests with every behavior change.

## 4. API pattern

Use `PaymentController` as a simple example:

```java
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponseDto> create(
            @Valid @RequestBody PaymentRequestDto dto) {
        return new ResponseEntity<>(paymentService.create(dto), HttpStatus.CREATED);
    }
}
```

The controller does not calculate totals, query repositories, generate KHQR data, or update booking states. Those operations belong in `PaymentServiceImpl` and its collaborators.

## 5. Service pattern

`PaymentServiceImpl` demonstrates the expected approach for complex operations:

- Resolve the authenticated customer through `AuthorizationService`.
- Load linked records and verify ownership.
- Recalculate booking and order totals on the server.
- Reject invalid status transitions.
- Lock records before concurrent status changes.
- Save all related state changes inside a transaction.
- Return a response DTO through `PaymentMapper`.

Keep public service methods focused. Private helper methods are appropriate for repeated validation, masking logs, state checks, and transaction lookup.

## 6. Payment and KHQR rules

This project uses Bakong polling/PULL. It does not depend on webhooks.

### Payment creation

For KHQR payments, the backend must:

1. Validate customer ownership and booking status.
2. Calculate the expected amount from current booking seats and order items.
3. Generate a unique internal transaction ID.
4. Generate dynamic KHQR using the server-configured merchant identity.
5. Save the exact QR string and MD5 returned by the KHQR SDK.
6. Save the QR expiration returned by the generation service.
7. Create or update the related `PaymentTransaction` attempt.

The normal KHQR expiry is five minutes and must never exceed ten minutes.

The internal transaction ID, such as `TXN-KHQR-...`, is not the Bakong lookup identifier. Bakong checks must use `payment.md5Hash`.

### Bakong check

The backend request is:

```http
POST {bakong.base-url}/v1/check_transaction_by_md5
Authorization: Bearer <configured-token>
Content-Type: application/json
```

```json
{
  "md5": "<saved-32-character-md5>"
}
```

`responseCode = 0` with non-null `data` is a successful Bakong lookup. Before confirmation, production code must also verify:

- Amount equals `Payment.amount`.
- Currency equals the configured KHQR currency.
- `toAccountId` equals the configured merchant account.

Missing or mismatched confirmation data must not mark the payment as paid.

### Confirmation

Confirmation must remain idempotent and transactional. The same operation synchronizes:

```text
Payment             PENDING -> PAID, paidAt set
PaymentTransaction  PENDING -> PAID
Booking             PENDING -> CONFIRMED
BookingSeat         PENDING/HELD/RESERVED -> CONFIRMED
Order               PENDING -> PAID when linked
```

Repeated polling must update the existing transaction for the same payment/reference instead of creating a duplicate confirmation record.

### Polling and expiration

- `GET /api/payments/{id}/status` lets an authorized customer poll through the backend.
- `PaymentExpirationScheduler.pollPendingKhqrPayments` checks Bakong in the background, including when the browser is closed.
- `PaymentExpirationScheduler.expireStaleBookings` expires abandoned bookings.
- A Bakong transport or configuration error is not proof that payment failed; keep the payment recoverable and retry according to the existing policy.

Expiration synchronizes related records:

```text
Payment             PENDING -> EXPIRED
PaymentTransaction  PENDING -> EXPIRED
Booking             PENDING -> EXPIRED
BookingSeat         active hold -> CANCELLED (the current released-seat representation)
```

Availability queries must treat expired/cancelled seat holds as inactive.

## 7. Configuration

Configuration belongs in `application.properties`, environment variables, or typed classes such as `KhqrConfig` and `BookingHoldConfig`.

Important Bakong settings include:

```properties
bakong.account-id=${BAKONG_ACCOUNT_ID:cinema_official@dev}
bakong.token=${BAKONG_TOKEN}
bakong.base-url=${BAKONG_BASE_URL:https://api-bakong.nbc.gov.kh}
bakong.currency=${BAKONG_CURRENCY:USD}
bakong.mock-mode=${BAKONG_MOCK_MODE:false}
booking.hold-ttl-minutes=${BOOKING_HOLD_TTL_MINUTES:5}
bakong.expiry-minutes=${booking.hold-ttl-minutes}
```

Never commit a real token or place it in frontend code. QR generation and transaction checking must use the same Bakong environment.

## 8. Testing

Tests are under `src/test/java/com/cinema/booking`.

Relevant payment tests:

- `PaymentControllerTest` - endpoint behavior
- `BakongServiceTest` - QR generation, MD5 checks, and Bakong response mapping
- `PaymentServiceImplTest` - payment confirmation validation
- `PaymentConfirmationIntegrationTest` - payment, booking, seat, transaction, scheduler, and expiration synchronization
- `EndpointProtectionIntegrationTest` - authentication and authorization boundaries

Use H2 and mock Bakong mode for automated tests. Do not call the real Bakong API from the test suite.

Commands:

```powershell
.\mvnw.cmd test-compile
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Linux/macOS equivalents use `./mvnw`.

## 9. Change checklist

Before finishing a change:

- Confirm the endpoint still delegates to the correct service.
- Confirm ownership and role checks are present.
- Confirm client-provided totals or statuses are not trusted.
- Confirm multi-table updates are transactional.
- Confirm concurrent operations use the existing locking strategy.
- Confirm payment checks use the saved MD5, not the internal transaction ID.
- Confirm terminal status handling is idempotent.
- Confirm logs and API responses do not expose secrets.
- Run focused tests, then the full test suite when practical.
- Report the exact files changed and test results.
