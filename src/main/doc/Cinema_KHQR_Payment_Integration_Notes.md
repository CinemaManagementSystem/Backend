# Cinema Booking System — CASH + KHQR Payment Integration

## 1. Payment Methods

The cinema payment system has two payment choices:

1. CASH
2. KHQR

The system does **not** need `WalletService` for this payment flow.

Recommended architecture:

```text
                 PaymentController
                       │
                       ↓
                 PaymentService
                       │
              paymentMethod?
                 ┌─────┴─────┐
                 ↓           ↓
               CASH         KHQR
                 │           │
                 │           ↓
                 │     BakongService
                 │           │
                 │      Generate QR
                 │           │
                 │           ↓
                 │      Customer pays
                 │           │
                 │           ↓
                 │      Check MD5
                 │           │
                 └─────┬─────┘
                       ↓
                Payment SUCCESS
                       ↓
               Booking CONFIRMED
```

---

## 2. Why Not `WalletService`?

`WalletService` should be used for an internal wallet/balance system.

For this cinema system:

- `CASH` = customer pays cash at the cinema
- `KHQR` = customer pays through Bakong/KHQR

Therefore, keep the responsibilities separate:

```text
PaymentService
│
├── CASH
│
└── KHQR → BakongService
```

Do not put KHQR-specific logic inside `WalletService`.

---

## 3. Payment Method Enum

The payment method should be:

```java
public enum PaymentMethod {
    CASH,
    KHQR
}
```

Payment status can remain:

```java
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED
}
```

---

## 4. Payment Entity

The `Payment` entity needs KHQR-specific fields.

Example:

```java
@Entity
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private BigDecimal amount;

    private String khqrString;

    private String md5Hash;

    private LocalDateTime expiresAt;

    // booking relationship...
}
```

### CASH Payment

```text
paymentMethod = CASH
khqrString = null
md5Hash = null
expiresAt = null
status = PENDING
```

### KHQR Payment

```text
paymentMethod = KHQR
khqrString = "000201..."
md5Hash = "abc123..."
expiresAt = 2026-08-24T11:30
status = PENDING
```

The existing KHQR design specifies storing `khqrString`, `md5Hash`, and `expiresAt` for KHQR payments.

---

## 5. CASH Payment Flow

Cash payment cannot automatically be verified by Bakong.

The flow is:

```text
Customer chooses CASH
        ↓
Booking created
        ↓
Payment = PENDING
        ↓
Customer pays cash at cinema
        ↓
Staff confirms payment
        ↓
Payment = SUCCESS
        ↓
Booking = CONFIRMED
```

A possible staff endpoint is:

```text
POST /api/payments/{id}/confirm
```

The backend then changes:

```text
Payment: PENDING → SUCCESS
Booking: PENDING → CONFIRMED
```

---

## 6. KHQR Payment Flow

KHQR is different because Bakong can be queried to verify the payment.

```text
Customer chooses KHQR
        ↓
POST /api/payments
        ↓
PaymentService
        ↓
BakongService
        ↓
Generate dynamic KHQR
        ↓
Generate MD5
        ↓
Save Payment as PENDING
        ↓
Return QR data to React
        ↓
React displays QR
        ↓
Customer scans QR
        ↓
Customer pays through Bakong-linked bank app
        ↓
Backend checks transaction by MD5
        ↓
Payment SUCCESS
        ↓
Booking CONFIRMED
```

The existing integration design uses dynamic KHQR, stores the QR string and MD5, and checks the transaction using `check-transaction-by-md5`.

---

## 7. Recommended Service Responsibilities

### `PaymentService`

Main payment business logic.

Responsibilities:

```text
createPayment()
createCashPayment()
createKhqrPayment()
checkPaymentStatus()
confirmCashPayment()
```

It decides which payment method is being used.

Example:

```java
public PaymentResponse createPayment(PaymentRequest request) {

    if (request.getPaymentMethod() == PaymentMethod.CASH) {
        return createCashPayment(request);
    }

    if (request.getPaymentMethod() == PaymentMethod.KHQR) {
        return createKhqrPayment(request);
    }

    throw new IllegalArgumentException("Unsupported payment method");
}
```

### `BakongService`

Only responsible for Bakong/KHQR operations:

```text
generateQr()
generateMd5()
checkTransactionByMd5()
```

Keep Bakong-specific code inside this service.

---

## 8. Final Architecture

```text
PaymentController
       │
       ↓
PaymentService
       │
       ├───────────────┐
       ↓               ↓
     CASH             KHQR
       │               │
       │               ↓
       │        BakongService
       │               │
       │        ┌──────┴──────┐
       │        ↓             ↓
       │   Generate QR   Check MD5
       │        │             │
       │        ↓             │
       │   Customer pays      │
       │        │             │
       │        └──────┬──────┘
       │               ↓
       └────────→ Payment SUCCESS
                       ↓
                Booking CONFIRMED
```

---

## 9. Implementation Plan — One Step at a Time

We should implement this gradually instead of writing the entire KHQR integration at once.

### Step 1 — Payment Entity

Check and update:

- `Payment.java`
- `PaymentMethod`
- `PaymentStatus`

### Step 2 — Bakong Configuration

Prepare:

- Bakong Account ID
- Bakong Developer Token
- Currency
- Bank/FI information
- Merchant information

Never expose the real Developer Token in source code or React.

### Step 3 — Add KHQR SDK

Add the appropriate KHQR/Bakong SDK dependency to the Spring Boot project.

### Step 4 — Create `BakongService`

Implement:

```java
generateQr()
checkTransactionByMd5()
```

### Step 5 — Generate Dynamic KHQR

Generate one QR for each KHQR payment.

The QR should contain the transaction amount and a unique bill/reference number.

### Step 6 — Save Payment

Store:

```text
paymentMethod = KHQR
status = PENDING
khqrString
md5Hash
expiresAt
```

### Step 7 — Return QR to React

Return the QR data from:

```text
POST /api/payments
```

React displays the QR to the customer.

### Step 8 — Customer Pays

Customer scans the QR using a Bakong-linked bank application.

### Step 9 — Check Payment

Backend calls Bakong:

```text
check_transaction_by_md5
```

The backend determines whether the transaction has been paid.

### Step 10 — Confirm Booking

When payment is successful:

```text
Payment PENDING → SUCCESS
Booking PENDING → CONFIRMED
```

### Step 11 — Expiration

If the payment is still unpaid after the QR expiration time:

```text
Payment PENDING → FAILED
```

---

## 10. Important Design Rule

Do not make:

```text
WalletService → KHQR
```

Instead use:

```text
PaymentService
    ├── CASH
    └── KHQR → BakongService
```

This keeps the code clean and makes it easier to add another payment method later.

---

## 11. Next Step

Start with **Step 1: the `Payment` entity and enums**.

Before changing anything, inspect the current project code:

1. `Payment.java`
2. `PaymentMethod` enum, if it exists
3. `PaymentStatus` enum
4. `PaymentServiceImpl`

Then modify only what is necessary for:

```text
CASH + KHQR
```

Do not implement the Bakong API yet. First make the payment domain correct.
