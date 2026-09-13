# Cinema Booking, Seat, and Payment Usage Guide

This guide describes the current backend flow for a customer booking seats and for staff managing payment.

The normal flow is:

```text
Login -> Get showtime -> Create booking -> Create booking seats
      -> Create payment -> Bakong polling or staff confirmation
      -> Payment PAID -> Booking CONFIRMED -> BookingSeats CONFIRMED
```

## 1. Start the Backend

The Docker Compose services use these host addresses:

| Service | URL |
|---|---|
| Spring Boot API | `http://localhost:8081` |
| MySQL | `localhost:3307` |
| phpMyAdmin | `http://localhost:8083` |
| Swagger UI | `http://localhost:8081/swagger-ui/index.html` |

Configure `.env` before starting the application. For development seed data, use:

```dotenv
SPRING_PROFILES_ACTIVE=dev
APP_SEED_ENABLED=true
```

Then start the stack:

```bash
docker compose up --build
```

The development seeder creates users, movies, screens, seats, products, and active showtimes. It creates showtimes for tomorrow, so use `GET /api/shows` to obtain the current `showId` values.

Do not commit real database, Cloudinary, JWT, or Bakong credentials from `.env`.

## 2. Customer Authentication

### Register a customer

```bash
curl.exe -X POST http://localhost:8081/api/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"customer01\",\"email\":\"customer01@example.com\",\"password\":\"Password123\"}"
```

Registration always creates a `USER` account. The response contains the new customer `id`.

### Login

```bash
curl.exe -X POST http://localhost:8081/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"customer01@example.com\",\"password\":\"Password123\"}"
```

Save these values from the response:

```text
accessToken -> use as the Bearer token
user.id     -> use as customerId
```

Example placeholder used below:

```text
Authorization: Bearer CUSTOMER_ACCESS_TOKEN
```

For development only, the seeder creates:

| Role | Email | Password |
|---|---|---|
| STAFF | `staff@cinema.com` | `Staff123` |
| ADMIN | `admin@cinema.com` | `Admin123` |

## 3. Find a Showtime and Seats

Showtime browsing is public:

```bash
curl.exe http://localhost:8081/api/shows
```

A show response contains:

```json
{
  "id": 1,
  "startTime": "2026-09-14T18:00:00",
  "endTime": "2026-09-14T20:28:00",
  "status": "ACTIVE",
  "ticketPrice": 6.00,
  "movieId": 1,
  "screenId": 1
}
```

Use the authenticated customer token to inspect available seat records:

```bash
curl.exe http://localhost:8081/api/seats ^
  -H "Authorization: Bearer CUSTOMER_ACCESS_TOKEN"
```

Choose a `showId` and one or more `seatId` values. A seat is reserved for a show through a `BookingSeat` record, not by changing the global `Seat` record.

## 4. Create the Booking

Create one booking for the selected show. Use the logged-in customer's ID as `customerId`.

```bash
curl.exe -X POST http://localhost:8081/api/bookings ^
  -H "Authorization: Bearer CUSTOMER_ACCESS_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"bookedAt\":\"2026-09-14T15:00:00\",\"bookingCode\":\"BOOK-20260914-001\",\"totalAmount\":12.00,\"customerId\":CUSTOMER_ID,\"showId\":SHOW_ID}"
```

The backend creates the booking with:

```text
Booking.status = PENDING
```

Save the returned booking `id` as `BOOKING_ID`.

The booking request currently requires the client to send `totalAmount`. The backend does not calculate the ticket total from the selected seats at this point.

## 5. Add the Selected Seats to the Booking

Call this endpoint once for every selected seat:

```bash
curl.exe -X POST http://localhost:8081/api/booking-seats ^
  -H "Authorization: Bearer CUSTOMER_ACCESS_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"bookingId\":BOOKING_ID,\"seatId\":SEAT_A_ID}"

curl.exe -X POST http://localhost:8081/api/booking-seats ^
  -H "Authorization: Bearer CUSTOMER_ACCESS_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"bookingId\":BOOKING_ID,\"seatId\":SEAT_B_ID}"
```

Each new record starts as:

```text
BookingSeat.status = PENDING
```

The backend checks that the same seat is not already actively reserved for the same show. A reservation is considered active unless its status is `CANCELLED`.

If adding one of several seats fails, the booking and previously added seats are not automatically removed because each API request is a separate transaction. The client should cancel or clean up the incomplete booking before starting again.

## 6. Optional Food or Product Order

If the customer is buying products, create an order linked to the booking, then add order items.

```bash
curl.exe -X POST http://localhost:8081/api/orders ^
  -H "Authorization: Bearer CUSTOMER_ACCESS_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"completedAt\":\"2026-09-14T15:30:00\",\"orderNumber\":\"ORDER-20260914-001\",\"orderType\":\"FOOD\",\"orderedAt\":\"2026-09-14T15:00:00\",\"status\":\"PENDING\",\"subtotal\":7.50,\"totalAmount\":7.50,\"bookingId\":BOOKING_ID,\"customerId\":CUSTOMER_ID}"
```

Then add an item for each product:

```bash
curl.exe -X POST http://localhost:8081/api/order-items ^
  -H "Authorization: Bearer CUSTOMER_ACCESS_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"quantity\":1,\"unitPrice\":7.50,\"subtotal\":7.50,\"orderId\":ORDER_ID,\"productId\":PRODUCT_ID}"
```

The payment can include `orderId`, but it is optional for a ticket-only booking. The current backend accepts the totals supplied in the order request, so the client must calculate them consistently.

## 7. KHQR Payment

Create a KHQR payment linked to the booking:

```bash
curl.exe -X POST http://localhost:8081/api/payments ^
  -H "Authorization: Bearer CUSTOMER_ACCESS_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":12.00,\"paymentMethod\":\"KHQR\",\"customerId\":CUSTOMER_ID,\"bookingId\":BOOKING_ID}"
```

The response contains:

```text
id            -> PAYMENT_ID
status        -> PENDING
khqrString    -> display as a QR code
md5Hash       -> stored server-side for Bakong polling
transactionId -> gateway reference
expiresAt     -> payment expiration time
```

The frontend should display `khqrString` and read the status endpoint:

```bash
curl.exe http://localhost:8081/api/payments/PAYMENT_ID/status ^
  -H "Authorization: Bearer CUSTOMER_ACCESS_TOKEN"
```

Bakong uses polling in this system. The server scheduler also polls pending KHQR payments automatically. When Bakong reports `PAID`, one transactional confirmation updates all related records:

```text
Payment          -> PAID
PaymentTransaction -> PAID
Booking          -> CONFIRMED
all BookingSeats -> CONFIRMED
```

Repeated paid responses are idempotent and do not create another transaction row for the same reference.

## 8. Cash Payment Managed by Staff

Create a cash payment linked to the booking:

```bash
curl.exe -X POST http://localhost:8081/api/payments ^
  -H "Authorization: Bearer CUSTOMER_ACCESS_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":12.00,\"paymentMethod\":\"CASH\",\"customerId\":CUSTOMER_ID,\"bookingId\":BOOKING_ID}"
```

The payment starts as `PENDING`. A staff member or admin can view all payments:

```bash
curl.exe http://localhost:8081/api/payments ^
  -H "Authorization: Bearer STAFF_ACCESS_TOKEN"
```

After receiving the cash, staff confirms the payment:

```bash
curl.exe -X POST http://localhost:8081/api/payments/PAYMENT_ID/confirm ^
  -H "Authorization: Bearer STAFF_ACCESS_TOKEN"
```

The confirmation response should contain `status: PAID`. Staff can verify the related records:

```bash
curl.exe http://localhost:8081/api/bookings/BOOKING_ID ^
  -H "Authorization: Bearer STAFF_ACCESS_TOKEN"

curl.exe http://localhost:8081/api/booking-seats ^
  -H "Authorization: Bearer STAFF_ACCESS_TOKEN"

curl.exe http://localhost:8081/api/payment-transactions/by-payment/PAYMENT_ID ^
  -H "Authorization: Bearer STAFF_ACCESS_TOKEN"
```

Regular customers cannot call the confirmation endpoint. They receive `403 Forbidden`.

## 9. Statuses

| Record | Initial status | Successful payment status |
|---|---|---|
| Payment | `PENDING` | `PAID` |
| PaymentTransaction | `PENDING` | `PAID` |
| Booking | `PENDING` | `CONFIRMED` |
| BookingSeat | `PENDING` | `CONFIRMED` |
| Order | `PENDING` | `PAID` when linked to the payment |

Payment can also become `FAILED` after expiration. Booking status values are `PENDING`, `CONFIRMED`, `CANCELLED`, and `COMPLETED`.

## 10. Important Rules and Current Limitations

- Customers can create and view their own bookings, booking seats, orders, and payments.
- Staff and admins can view all payments and confirm payments.
- Only staff/admin should use payment confirmation. Do not manually create a `PaymentTransaction` to mark a payment as paid.
- A booking and its booking seats are created through separate requests.
- Pending seats remain active until cancellation or cleanup. The current backend does not automatically expire abandoned seat holds.
- Always link payment to `bookingId`. Although the DTO allows it to be omitted, an unlinked payment cannot confirm a booking.
- Use `GET /api/payments/{id}/status` for customer-side status display. Backend state, not frontend text, is authoritative.
