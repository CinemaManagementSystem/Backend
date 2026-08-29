# Payment Module — Full End-to-End Flow

## 1. Purpose

This document describes the **complete lifecycle of a payment** in the
cinema booking system, from the moment an `Order` needs to be paid through
to reconciliation, covering both supported methods — **KHQR** (Bakong) and
**CASH** — and the business rules enforced at each step.

---

## 2. Data Model

| Entity | Role |
|---|---|
| `Order` | The thing being paid for (booking + products), holds total amount |
| `Payment` | One record per payment attempt against an `Order` |
| `PaymentTransaction` | Append-only audit ledger — one row per state-changing event on a `Payment` |
| `PaymentRequestDto` | Inbound payload (create/confirm payment) |
| `PaymentResponseDto` | Outbound payload returned to clients |
| `PaymentMapper` | Pure translation between entity ↔ DTO, no business logic |

### Relationship

```
Order (1) ──── (1..N) Payment ──── (1..N) PaymentTransaction
```

An `Order` can have more than one `Payment` row only if a prior attempt
failed/expired and the customer retries — never more than one `SUCCESS`
`Payment` per `Order`.

### Key `Payment` fields

| Field | Notes |
|---|---|
| `id` | PK |
| `orderId` | FK to `Order` |
| `amount` | Copied from `Order` total at creation time |
| `method` | `KHQR` \| `CASH` |
| `status` | `PENDING` → `SUCCESS` \| `FAILED` \| `EXPIRED` |
| `transactionId` | **Only ever set for `KHQR`.** Always `null` for `CASH` — this was bug #1, previously it leaked a value onto cash payments |
| `confirmedAt` | Timestamp set the moment status becomes `SUCCESS` |
| `createdAt` / `updatedAt` | Standard audit columns |

---

## 3. Payment State Machine

- `PENDING` is the only state a new `Payment` can start in.
- Once a `Payment` reaches `SUCCESS`, it is **terminal and immutable** —
  `update()` rejects any further modification (bug #3 fix). Corrections go
  through a separate refund/adjustment workflow, not this endpoint.
- `FAILED` and `EXPIRED` are also terminal for that specific `Payment` row,
  but the customer may create a **new** `Payment` row against the same
  `Order` to retry.

---

## 4. API Surface (`PaymentController`)

| Endpoint | Purpose |
|---|---|
| `POST /payments` | Create a `Payment` for an `Order` (choose `KHQR` or `CASH`) |
| `GET /payments/{id}` | Fetch current status of a payment |
| `POST /payments/{id}/confirm` | Confirm a payment — called by Bakong webhook, the polling job, or staff |
| `PUT /payments/{id}` | Update payment metadata — **blocked once `SUCCESS`** |

`PaymentRequestDto` / `PaymentResponseDto` are Java records. Since record
constructors are **positional**, the field declaration order in the record
must exactly match the constructor-call order inside `toResponseDto()` in
`PaymentMapper` — a mismatch compiles cleanly but silently swaps values.
This is still the one open item to verify once those two files are shared.

---

## 5. Full End-to-End Flow

### Stage A — Pre-payment (shared by both methods)

1. Customer completes booking → `Order` created with status `PENDING_PAYMENT`.
2. Customer hits checkout and picks a payment method.
3. `POST /payments` creates a `Payment`:
   - `amount` = `Order.total`
   - `status` = `PENDING`
   - `transactionId` = `null`
   - `method` = whichever the customer chose

### Stage B1 — KHQR path

1. Service calls Bakong to generate a KHQR string/image for `Payment.amount`.
2. QR is rendered to the customer (app screen or printed receipt at a kiosk).
3. Customer opens their bank's app, scans, and pays.
4. Two independent triggers race to call `POST /payments/{id}/confirm`:
   - **Bakong webhook** — pushed the instant the bank settles the transfer.
   - **Polling job** — a scheduled task that periodically asks Bakong for
     the transaction's status, as a fallback if the webhook is delayed
     or missed.
5. Inside `confirmPayment()`:
   - **Idempotency guard**: if `Payment.status` is already `SUCCESS`,
     return immediately — do not re-process. This is what prevents the
     webhook and the poller from both writing a `SUCCESS` row to
     `PaymentTransaction` (bug #2 fix).
   - Otherwise: set `transactionId` from Bakong's response, set
     `status = SUCCESS`, set `confirmedAt = now()`.
   - Insert one new `PaymentTransaction` row recording this event.
6. `Order.status` → `CONFIRMED`. Booking seats move from held → finalized.
7. E-ticket generated and delivered to the customer.

**If the QR expires before payment**: `Payment.status` → `EXPIRED`. The
customer can trigger a brand-new `Payment` row (back to Stage A) to retry;
the expired row is left untouched as a historical record.

### Stage B2 — CASH path

1. Customer walks up to the counter with their pending `Order` reference.
2. Staff collects the exact cash amount.
3. Staff clicks "Confirm payment" in the staff console, which calls the
   same `POST /payments/{id}/confirm` endpoint.
4. Same `confirmPayment()` logic runs:
   - Idempotency guard applies identically — protects against a staff
     double-click or a duplicate console submission.
   - `transactionId` stays `null` (no gateway was involved).
   - `status = SUCCESS`, `confirmedAt = now()`.
   - One `PaymentTransaction` row inserted.
5. `Order.status` → `CONFIRMED`. Ticket is printed on the spot.

### Stage C — Post-confirmation (shared)

- The `Payment` row is now frozen: any `PUT /payments/{id}` call against
  it is rejected by the service layer, regardless of method.
- `PaymentTransaction` is append-only — nothing here is ever updated or
  deleted, only added to. This is what makes it usable for financial
  reconciliation.
- Reporting/reconciliation jobs read from `PaymentTransaction`, never from
  mutable fields on `Payment`, to guarantee they see the audit trail
  exactly as it happened.

---

## 6. Concurrency & Idempotency — Why It Matters Here Specifically

KHQR is the one path with a genuine race: the webhook and the polling job
are two separate call paths that can both reach `confirmPayment()` for the
same `Payment` within milliseconds of each other if Bakong's webhook and
poll response arrive close together. Without the guard, this would double
the `PaymentTransaction` audit trail for a single real-world payment,
corrupting reconciliation totals. The guard is a simple pre-check
(`if status == SUCCESS: return`), but it has to run **inside** the same
transactional boundary as the status update to fully close the race —
a plain check-then-act without row-level locking or a unique constraint on
`(paymentId, status=SUCCESS)` can still theoretically interleave under
heavy concurrency, so the underlying guarantee should come from the
database (unique constraint or `SELECT ... FOR UPDATE`), not just the
application-level `if` check.

---

## 7. Open Verification Item

`PaymentRequestDto` and `PaymentResponseDto` source has not yet been shared.
Once available, confirm the record's declared field order matches the
positional arguments passed in `toResponseDto()` — this class of bug is
silent at compile time and only shows up as scrambled data at runtime.
