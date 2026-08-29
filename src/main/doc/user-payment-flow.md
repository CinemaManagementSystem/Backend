# Cinema Booking System — End-to-End User Payment Flow

## Scope

This document traces a single user journey from account access through to a
finalized, reconciled payment, based on the current module structure:
`auth`, `movies`, `shows`, `cinemas`, `rooms`, `bookings`, `products`,
`productcategories`, `orders`, `payments`, `paymenttransaction`, `users`.

---

## 1. Actors

| Actor | Role |
|---|---|
| **Customer** | Browses, books seats, pays |
| **Counter Staff** | Confirms CASH payments at the venue |
| **Bakong Gateway** | Generates/validates KHQR transactions (NBC Cambodia standard) |
| **System (Polling Job)** | Periodically checks Bakong for KHQR payment status |

---

## 2. High-Level Flow

```
Login → Browse → Select Show/Seats → Book → Add Products (optional)
   → Create Order → Choose Payment Method → Pay → Confirm → Ticket Issued
```

---

## 3. Step-by-Step Flow

### Step 1 — Authentication (`auth`, `users`)
- Customer registers or logs in.
- System issues a session/JWT token tied to the `User` entity.

### Step 2 — Browse Catalog (`movies`, `shows`, `cinemas`, `rooms`)
- Customer browses movies, filters by cinema, and picks a show time.
- System returns available `Room` layout and seat map for the selected `Show`.

### Step 3 — Seat Selection (`bookings`)
- Customer selects seat(s).
- System creates a `Booking` in a `PENDING`/`HOLD` state, temporarily reserving
  the seats to prevent double-booking.

### Step 4 — Optional Add-ons (`products`, `productcategories`)
- Customer optionally adds concessions (popcorn, drinks, combos) organized
  under `ProductCategory`.

### Step 5 — Order Creation (`orders`)
- System aggregates the `Booking` + selected `Products` into a single `Order`.
- Order total = seat price(s) + product price(s).
- Order status starts as `PENDING_PAYMENT`.

### Step 6 — Choose Payment Method (`payments`)
Customer selects one of:
- **KHQR** (Bakong QR standard)
- **CASH** (pay at counter)

A `Payment` record is created, linked to the `Order`, with:
- `amount` = order total
- `method` = `KHQR` or `CASH`
- `status` = `PENDING`
- `transactionId` = **null** at creation (only ever populated for gateway-based
  methods, never for CASH — this was one of the corrected bugs)

---

## 4. Payment Path A — KHQR (Bakong)

1. System calls Bakong to generate a KHQR code for the `Payment.amount`.
2. QR code is displayed to the customer (in-app or on a kiosk/receipt).
3. Customer scans the QR with any Bakong-participating bank app and pays.
4. Payment confirmation can arrive via **two concurrent triggers**:
   - Bakong webhook/callback, **or**
   - A background polling job checking transaction status.
5. Whichever trigger fires first calls `confirmPayment()`:
   - **Idempotency guard** checks if the `Payment` is already `SUCCESS`.
     - If yes → no-op (prevents duplicate `SUCCESS` rows in
       `PaymentTransaction` when both triggers fire near-simultaneously).
     - If no → proceed.
   - `transactionId` is set from Bakong's response.
   - `Payment.status` → `SUCCESS`.
   - A new immutable row is appended to `PaymentTransaction` (audit ledger).
6. `Order` and `Booking` are marked `CONFIRMED`.
7. E-ticket is issued to the customer.

---

## 5. Payment Path B — CASH

1. Customer proceeds to the counter with their pending `Order`.
2. Counter Staff collects cash and manually triggers `confirmPayment()`
   from the staff console.
3. Idempotency guard applies the same way as KHQR (protects against
   double-clicks or duplicate staff actions).
4. `transactionId` remains **null** (no gateway involved).
5. `Payment.status` → `SUCCESS`; `PaymentTransaction` entry logged.
6. `Order` and `Booking` are marked `CONFIRMED`.
7. Ticket is printed/issued at the counter.

---

## 6. Post-Confirmation Rules (Reconciliation Integrity)

Once a `Payment` reaches `SUCCESS`:
- `update()` on the `Payment` is **blocked** — confirmed payments cannot be
  silently modified, protecting reconciled financial records.
- Any further correction (refund, adjustment) must go through a separate,
  explicit workflow rather than an in-place edit — not through this endpoint.

---

## 7. Failure / Edge Cases

| Scenario | Handling |
|---|---|
| KHQR expires before payment | `Payment.status` → `EXPIRED`; customer can regenerate QR |
| Bakong webhook and polling both fire | Idempotency guard ensures only one `SUCCESS` audit entry |
| Staff attempts to confirm an already-confirmed order | No-op, guarded |
| Customer abandons booking | `Booking` hold expires, seats released back to inventory |
| Attempt to edit a confirmed `Payment` | Rejected by service layer |

---

## 8. Summary Sequence (Text Diagram)

```
Customer          System(App)        Order/Booking       Payment Service     Bakong/Staff
   |                   |                    |                     |                |
   |-- Login --------->|                    |                     |                |
   |-- Browse/Select ->|-- Hold seats ----->|                     |                |
   |-- Add products -->|                    |                     |                |
   |-- Checkout ------>|-- Create Order --->|                     |                |
   |-- Pick method --->|-- Create Payment ------------------------>|                |
   |                   |                    |                     |-- KHQR ------->|
   |-- Scan & Pay -------------------------------------------------------------->|
   |                   |                    |                     |<- Confirm -----|
   |                   |                    |<-- confirmPayment() -|(idempotent)   |
   |                   |<-- Confirmed ------|                     |                |
   |<-- Ticket --------|                    |                     |                |
```

*(For CASH, "Scan & Pay" is replaced by staff collecting cash and triggering
`confirmPayment()` directly from the counter console.)*
