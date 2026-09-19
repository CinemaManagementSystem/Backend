# Cinema Booking System - Complete System Flow

This document describes the current Spring Boot implementation in this repository. The implemented payment methods are `CASH` and `KHQR`; wallet payment is not implemented in the current source.

## 1. System Overview

```mermaid
flowchart TD
    Customer[Customer] --> Auth[Register or Login]
    Auth --> Browse[Browse Movies and Shows]
    Browse --> Booking[Create Booking]
    Booking --> Seats[Create BookingSeat records]
    Seats --> Review[Optional Order and OrderItems]
    Review --> Payment[Create Payment]
    Payment --> Cash{Payment method}
    Cash -->|CASH| Staff[Staff confirms payment]
    Cash -->|KHQR| QR[Display KHQR QR code]
    QR --> Poll[Browser status check and server polling]
    Poll --> Bakong[Bakong check_transaction_by_md5]
    Bakong --> Confirm[Central transactional confirmation]
    Staff --> Confirm
    Confirm --> Paid[Payment PAID]
    Confirm --> Transaction[Existing PaymentTransaction PAID]
    Confirm --> BookingConfirmed[Booking CONFIRMED]
        Confirm --> SeatsConfirmed[All BookingSeats CONFIRMED]
```

The main application layers are:

```text
Frontend or API client
        |
        v
REST Controller
        |
        v
Service and business rules
        |
        v
Spring Data Repository
        |
        v
MySQL database
```

External integrations are handled by Cloudinary for product images and Bakong for KHQR payment verification.

## 2. Docker Runtime

Start the development stack from the backend directory:

```bash
docker compose up --build
```

| Component | Container or host address | Purpose |
|---|---|---|
| Spring Boot app | `http://localhost:8081` | REST API |
| MySQL | host `localhost:3307`, container `db:3306` | Application database |
| phpMyAdmin | `http://localhost:8083` | Database inspection |
| Swagger UI | `http://localhost:8081/swagger-ui/index.html` | API exploration |

The application container connects to MySQL using `jdbc:mysql://db:3306/cinema_db`. The host port `3307` is only for tools running outside Docker.

The app loads `.env`. For development seed data, configure:

```dotenv
SPRING_PROFILES_ACTIVE=dev
APP_SEED_ENABLED=true
```

The seeder runs only with the `dev` profile and the seed flag enabled. It creates users, locations, theaters, screens, seats, movie categories, movies, products, and active showtimes for tomorrow.

Never commit real credentials from `.env`.

## 3. Database Relationship Flow

```mermaid
erDiagram
    USER ||--o{ BOOKING : owns
    USER ||--o{ ORDER : places
    USER ||--o{ PAYMENT : makes
    LOCATION ||--o{ THEATER : contains
    THEATER ||--o{ SCREEN : contains
    SCREEN ||--o{ SEAT : has
    MOVIE ||--o{ SHOW : scheduled_as
    SCREEN ||--o{ SHOW : hosts
    SHOW ||--o{ BOOKING : selected_for
    BOOKING ||--o{ BOOKING_SEAT : reserves
    SEAT ||--o{ BOOKING_SEAT : selected
    BOOKING ||--o{ ORDER : may_have
    ORDER ||--o{ ORDER_ITEM : contains
    PRODUCT ||--o{ ORDER_ITEM : sold_as
    PRODUCT_CATEGORY ||--o{ PRODUCT : groups
    BOOKING ||--o{ PAYMENT : paid_by
    ORDER ||--o{ PAYMENT : paid_by
    PAYMENT ||--o{ PAYMENT_TRANSACTION : records
```

The important runtime chain is:

```text
Movie + Screen
      -> Show
      -> Booking
      -> BookingSeat + Seat
      -> optional Order + OrderItem + Product
      -> Payment + PaymentTransaction
```

`Seat` is the reusable seat definition. `BookingSeat` is the show-specific reservation and stores the selected seat price and reservation status.

## 4. Authentication and Roles

### Customer registration

```http
POST /api/auth/register
```

```json
{
  "username": "customer01",
  "email": "customer01@example.com",
  "password": "Password123"
}
```

Registration always creates a `USER` account.

### Login

```http
POST /api/auth/login
```

The request may use either `username` or `email`:

```json
{
  "email": "customer01@example.com",
  "password": "Password123"
}
```

Use the returned `accessToken` on protected requests:

```http
Authorization: Bearer ACCESS_TOKEN
```

The development seeder creates these accounts:

| Role | Email | Password |
|---|---|---|
| `ADMIN` | `admin@cinema.com` | `Admin123` |
| `STAFF` | `staff@cinema.com` | `Staff123` |
| `USER` | `user@cinema.com` | `User123` |

These credentials are for development only.

### Access summary

| Operation | Customer | Staff | Admin |
|---|---:|---:|---:|
| Register and login | Yes | Yes | Yes |
| Browse movies, shows, locations, theaters | Yes | Yes | Yes |
| Create own booking and booking seats | Yes | Yes | Yes |
| View own records | Yes | Yes | Yes |
| View all payments, bookings, and orders | No | Yes | Yes |
| Confirm payment | No | Yes | Yes |
| Create or update shows and catalog | No | Yes | Yes |
| Manage users | No | No | Yes |
| Delete core cinema structure | No | No | Yes |

## 5. Administration and Seeded Catalog Flow

The cinema structure is created in this order:

```text
Location
   -> Theater
      -> Screen
         -> Seat
```

The movie schedule is created in this order:

```text
Movie Category -> Movie -> Show
                              |
                              +-> Screen
```

Staff or admins can create a show with:

```http
POST /api/shows
```

```json
{
  "startTime": "2026-09-14T18:00:00",
  "endTime": "2026-09-14T20:28:00",
  "status": "ACTIVE",
  "ticketPrice": 6.00,
  "movieId": MOVIE_ID,
  "screenId": SCREEN_ID
}
```

Customers can browse showtimes with:

```http
GET /api/movies
GET /api/shows
```

The show response contains `movieId` and `screenId`. The client can load the related movie and screen records separately.

## 6. Customer Booking Flow

### Step 1: Select a show

The customer selects an active `showId` from `GET /api/shows`.

Seat records can be read from:

```http
GET /api/seats
```

This request requires authentication under the current security configuration.

### Step 2: Create the booking

```http
POST /api/bookings
Authorization: Bearer CUSTOMER_ACCESS_TOKEN
```

```json
{
  "bookedAt": "2026-09-14T15:00:00",
  "bookingCode": "BOOK-20260914-001",
  "totalAmount": 12.00,
  "customerId": CUSTOMER_ID,
  "showId": SHOW_ID
}
```

The service verifies the customer and show, then creates:

```text
Booking.status = PENDING
Booking.expiresAt = show.startTime - 5 minutes
```

Save the returned booking `id` as `BOOKING_ID`.

### Step 3: Add seats to the booking

Create one `BookingSeat` per selected seat:

```http
POST /api/booking-seats
Authorization: Bearer CUSTOMER_ACCESS_TOKEN
```

```json
{
  "bookingId": BOOKING_ID,
  "seatId": SEAT_ID
}
```

Each new record starts as:

```text
BookingSeat.status = PENDING
```

The service locks the seat row and rejects an active reservation for the same seat and show. Any status other than `CANCELLED` is currently treated as active.

The booking and booking-seat requests are separate transactions. If adding a later seat fails, earlier successful seat records remain until the booking is cancelled or cleaned up.

### Step 4: Optional food order

Create an order linked to the booking:

```http
POST /api/orders
Authorization: Bearer CUSTOMER_ACCESS_TOKEN
```

```json
{
  "completedAt": "2026-09-14T15:30:00",
  "orderNumber": "ORDER-20260914-001",
  "orderType": "FOOD",
  "orderedAt": "2026-09-14T15:00:00",
  "status": "PENDING",
  "subtotal": 7.50,
  "totalAmount": 7.50,
  "bookingId": BOOKING_ID,
  "customerId": CUSTOMER_ID
}
```

Add products through:

```http
POST /api/order-items
Authorization: Bearer CUSTOMER_ACCESS_TOKEN
```

```json
{
  "quantity": 1,
  "unitPrice": 7.50,
  "subtotal": 7.50,
  "orderId": ORDER_ID,
  "productId": PRODUCT_ID
}
```

### Step 5: Create a payment

Always link the payment to `bookingId`. Include `orderId` when the payment also covers a food order.

```http
POST /api/payments
Authorization: Bearer CUSTOMER_ACCESS_TOKEN
```

KHQR example:

```json
{
  "amount": 19.50,
  "paymentMethod": "KHQR",
  "customerId": CUSTOMER_ID,
  "bookingId": BOOKING_ID,
  "orderId": ORDER_ID
}
```

Ticket-only payment:

```json
{
  "amount": 12.00,
  "paymentMethod": "KHQR",
  "customerId": CUSTOMER_ID,
  "bookingId": BOOKING_ID
}
```

The new payment and its initial transaction are both pending:

```text
Payment.status = PENDING
PaymentTransaction.status = PENDING
```

## 7. KHQR Polling Flow

This system uses Bakong polling/pull. It does not use a webhook.

```mermaid
sequenceDiagram
    participant Client as Customer frontend
    participant API as Spring Boot API
    participant DB as MySQL
    participant Scheduler as Payment scheduler
    participant Bakong as Bakong API

    Client->>API: POST /api/payments
    API->>DB: Save PENDING Payment and PaymentTransaction
    API-->>Client: khqrString, payment id, md5Hash, expiresAt
    Client->>Client: Display QR code
    loop Browser status checks
        Client->>API: GET /api/payments/{id}/status
        API->>Bakong: check_transaction_by_md5
    end
    loop Every 10 seconds
        Scheduler->>DB: Load pending KHQR payments
        Scheduler->>Bakong: check_transaction_by_md5
    end
    Bakong-->>API: PAID
    API->>DB: Central transactional confirmation
    API-->>Client: PaymentResponse status PAID
```

The browser checks:

```http
GET /api/payments/PAYMENT_ID/status
Authorization: Bearer CUSTOMER_ACCESS_TOKEN
```

The scheduled server poller runs every 10 seconds by default. On a paid response, the central confirmation updates all related records in one transaction:

```text
Payment                  -> PAID
existing PaymentTransaction -> PAID
Booking                  -> CONFIRMED
all BookingSeats         -> CONFIRMED
linked Order             -> PAID
```

Repeated paid responses are idempotent. The payment row is locked during confirmation and an existing transaction row is updated instead of inserting another row with the same reference.

## 8. Cash Payment Flow

Create a cash payment in the same way, using `CASH`:

```json
{
  "amount": 12.00,
  "paymentMethod": "CASH",
  "customerId": CUSTOMER_ID,
  "bookingId": BOOKING_ID
}
```

The payment remains `PENDING` until staff receives the cash and confirms it:

```http
POST /api/payments/PAYMENT_ID/confirm
Authorization: Bearer STAFF_ACCESS_TOKEN
```

The confirmation endpoint accepts only `STAFF` or `ADMIN`. It uses the same central confirmation path as KHQR polling, so the booking and every booking seat become confirmed together.

Customers cannot confirm their own cash payment and receive `403 Forbidden`.

## 9. Staff Payment Management

### List payments

```http
GET /api/payments
Authorization: Bearer STAFF_ACCESS_TOKEN
```

Staff and admins receive all payments. Customers receive only their own payments.

### Inspect one payment

```http
GET /api/payments/PAYMENT_ID
Authorization: Bearer STAFF_ACCESS_TOKEN
```

### Inspect transaction history

```http
GET /api/payment-transactions/by-payment/PAYMENT_ID
Authorization: Bearer STAFF_ACCESS_TOKEN
```

The application creates and updates payment transactions as part of payment processing. Staff should use `/api/payments/{id}/confirm` for cash confirmation and should not manually create a transaction to simulate a successful payment.

### Inspect booking and seats

```http
GET /api/bookings/BOOKING_ID
Authorization: Bearer STAFF_ACCESS_TOKEN

GET /api/booking-seats
Authorization: Bearer STAFF_ACCESS_TOKEN
```

After successful payment, staff should see:

```text
Payment.status = PAID
Booking.status = CONFIRMED
BookingSeat.status = CONFIRMED for every selected seat
PaymentTransaction.status = PAID
```

## 10. Status and Failure Flow

| Record | Created as | Successful payment | Failure or expiry |
|---|---|---|---|
| `Payment` | `PENDING` | `PAID` | `EXPIRED` |
| `PaymentTransaction` | `PENDING` | `PAID` | `EXPIRED` audit record |
| `Booking` | `PENDING` | `CONFIRMED` | `EXPIRED` five minutes before showtime |
| `BookingSeat` | `PENDING` | `CONFIRMED` | `CANCELLED` when the booking expires |
| `Order` | request-supplied status | `PAID` when linked | request/service dependent |

The expiration scheduler runs every 60 seconds and expires pending bookings at their persisted `expiresAt` deadline. That deadline is five minutes before showtime by default. Expiration also marks pending payments as `EXPIRED` and releases booking seats. KHQR payments use the same booking deadline.

The confirmation operation is transactional. If a booking, seat, order, or transaction update fails, the payment changes are rolled back instead of leaving partially updated records.

## 11. Endpoint Map

| Area | Main endpoints | Purpose |
|---|---|---|
| Auth | `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` | Account and JWT lifecycle |
| Movies | `/api/movies`, `/api/movie-category` | Movie catalog |
| Cinema | `/api/locations`, `/api/theaters`, `/api/screens`, `/api/seats` | Venue and seat layout |
| Shows | `/api/shows` | Showtime schedule |
| Bookings | `/api/bookings` | Customer bookings |
| Booking seats | `/api/booking-seats` | Show-specific seat reservations |
| Products | `/api/product-categories`, `/api/products` | Food and beverage catalog |
| Orders | `/api/orders`, `/api/order-items` | Optional concession orders |
| Payments | `/api/payments` | CASH and KHQR payments |
| Transactions | `/api/payment-transactions` | Payment audit records |
| Users | `/api/users` | Admin user management |

## 12. Current Implementation Notes

- `DatabaseSeeder` runs only in `dev` with `APP_SEED_ENABLED=true`. Existing production data is not overwritten by the seeder.
- Booking creation and booking-seat creation are separate API calls; there is no single reserve-all-seats endpoint.
- Pending booking seats are treated as active until payment confirmation, cancellation, or the booking's five-minute pre-show expiry.
- Payment requests allow missing `bookingId` and `orderId`, but a payment without `bookingId` cannot confirm a cinema booking. The normal client flow should always send `bookingId`.
- Booking, order, and payment totals are accepted from request data. The current services do not recalculate all totals from seat and product records.
- The static `index.html` is a KHQR diagnostic console. It displays and polls payment status, but backend records remain the source of truth.
- Older documents that mention wallets or `/api/wallets` do not match the current implementation and should not be used as the API contract.
