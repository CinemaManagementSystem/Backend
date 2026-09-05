# Frontend API Guide — Cinema Booking System

> **Base URL:** `http://localhost:8081`  
> **Content-Type:** `application/json` (unless noted otherwise)  
> **Interactive Docs:** Swagger UI at `/swagger-ui/index.html`

---

## Table of Contents

1. [Authentication](#1-authentication)
2. [Request & Response Conventions](#2-request--response-conventions)
3. [Error Handling](#3-error-handling)
4. [Rate Limiting](#4-rate-limiting)
5. [API Endpoints](#5-api-endpoints)
   - [Auth](#auth--apiauth)
   - [Users](#users--apiusers)
   - [Locations](#locations--apilocations)
   - [Theaters](#theaters--apitheaters)
   - [Screens](#screens--apiscreens)
   - [Seats](#seats--apiseats)
   - [Movie Categories](#movie-categories--apimovie-category)
   - [Movies](#movies--apimovies)
   - [Shows](#shows--apishows)
   - [Bookings & Booking Seats](#bookings--apibookings--apibooking-seats)
   - [Product Categories](#product-categories--apiproduct-categories)
   - [Products](#products--apiproducts)
   - [Orders & Order Items](#orders--apiorders--apiorder-items)
   - [Payments](#payments--apipayments)
   - [Payment Transactions](#payment-transactions--apipayment-transactions)
6. [Business Workflows](#6-business-workflows)

---

## 1. Authentication

The API uses **JWT Bearer tokens** for stateless authentication.

### Getting a Token

```
POST /api/auth/login
```

```json
// Request — supply username OR email (at least one)
{
  "username": "john",
  "password": "secret123"
}
```

```json
// Response 200 OK
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": {
    "id": 1,
    "username": "john",
    "email": "john@example.com",
    "role": "USER"
  }
}
```

### Using the Token

Include the token in every subsequent request:

```
Authorization: Bearer <accessToken>
```

### Registering a New Account

```
POST /api/auth/register
```

```json
{
  "username": "newuser",       // required, 3-50 chars
  "email": "user@example.com", // required, valid email
  "password": "secret123"      // required, 6-255 chars
}
```

```json
// Response 201 Created
{
  "message": "Registration successful",
  "user": {
    "id": 2,
    "username": "newuser",
    "email": "user@example.com",
    "role": "USER"
  }
}
```

> **Note:** New accounts are created with role `USER` and must be activated by an admin before login.

---

## 2. Request & Response Conventions

| Convention | Details |
|---|---|
| **List endpoints** | Return `200 OK` with a JSON array `[...]` |
| **Create endpoints** | Return `201 Created` with the created resource |
| **Update endpoints** | Return `200 OK` with the updated resource |
| **Delete endpoints** | Return `204 No Content` (empty body) |
| **ID fields** | All IDs are `Long` (integers) |
| **Date/Time format** | ISO-8601 (`2024-03-15T14:30:00`) for `LocalDateTime`, `2024-03-15` for `LocalDate` |
| **Decimal format** | Strings representing decimal numbers (e.g., `"12.50"`) for `BigDecimal` fields |

---

## 3. Error Handling

All errors return a consistent JSON shape:

```json
{
  "timestamp": "2024-03-15T14:30:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "Validation error",
  "path": "/api/movies",
  "details": {
    "title": ["Title is required"],
    "genre": ["Genre is required"]
  }
}
```

| Status Code | Meaning | When |
|---|---|---|
| `400` | Bad Request | Validation failure, malformed JSON |
| `401` | Unauthorized | Missing/invalid/expired JWT token |
| `403` | Forbidden | Authenticated but insufficient role |
| `404` | Not Found | Resource doesn't exist |
| `409` | Conflict | Duplicate username/email |
| `429` | Too Many Requests | Rate limit exceeded (see below) |
| `500` | Internal Server Error | Unexpected server failure |

---

## 4. Rate Limiting

| Endpoint Pattern | Limit | Window |
|---|---|---|
| `/api/auth/**` | 10 requests | per minute |
| All other `/api/**` | 100 requests | per minute |

When rate-limited, the response includes:
```
429 Too Many Requests
Retry-After: 60
```

---

## 5. API Endpoints

---

### Auth `/api/auth`

#### `POST /api/auth/register`

Register a new user account.

**Request Body:**
| Field | Type | Required | Validation |
|---|---|---|---|
| `username` | String | yes | 3-50 characters |
| `email` | String | yes | Valid email format |
| `password` | String | yes | 6-255 characters |

**Response:** `201 Created` → `RegisterResponseDto`
```json
{
  "message": "Registration successful",
  "user": { "id": 1, "username": "john", "email": "john@example.com", "role": "USER" }
}
```

#### `POST /api/auth/login`

Authenticate and receive a JWT.

**Request Body:**
| Field | Type | Required | Notes |
|---|---|---|---|
| `username` | String | no* | At least one of `username`/`email` required |
| `email` | String | no* | At least one of `username`/`email` required |
| `password` | String | yes | |

**Response:** `200 OK` → `AuthResponseDto`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": { "id": 1, "username": "john", "email": "john@example.com", "role": "USER" }
}
```

---

### Users `/api/users`

> **Access:** Admin only for all endpoints.

#### `GET /api/users`

**Response:** `200 OK` → `UserResponseDto[]`

#### `GET /api/users/{id}`

**Response:** `200 OK` → `UserResponseDto`

#### `POST /api/users`

**Request Body:**
| Field | Type | Required | Validation |
|---|---|---|---|
| `username` | String | no | 3-50 characters (defaults to email if omitted) |
| `email` | String | yes | Valid email |
| `name` | String | yes | 2-100 characters |
| `password` | String | yes | 6+ characters |
| `role` | String | yes | `USER`, `STAFF`, or `ADMIN` |
| `status` | String | yes | Account status (e.g., `ACTIVE`) |

#### `PUT /api/users/{id}`

Same fields as `POST`. Password can be omitted/blank to keep existing password.

#### `DELETE /api/users/{id}`

**Response:** `204 No Content`

---

### Locations `/api/locations`

#### `GET /api/locations`

**Response:** `200 OK` → `LocationResponseDto[]`
```json
[
  {
    "id": 1,
    "name": "Phnom Penh Central",
    "address": "123 Street 123",
    "city": "Phnom Penh",
    "googleMapsUrl": "https://maps.google.com/...",
    "latitude": 11.5564,
    "longitude": 104.9282
  }
]
```

#### `GET /api/locations/{id}`

**Response:** `200 OK` → `LocationResponseDto`

#### `POST /api/locations` — Staff/Admin

**Request Body:**
| Field | Type | Required |
|---|---|---|
| `name` | String | yes |
| `address` | String | yes |
| `city` | String | yes |
| `googleMapsUrl` | String | no |
| `latitude` | BigDecimal | yes |
| `longitude` | BigDecimal | yes |

#### `PUT /api/locations/{id}` — Staff/Admin

Same fields as `POST`.

#### `DELETE /api/locations/{id}` — Admin

---

### Theaters `/api/theaters`

#### `GET /api/theaters`

**Response:** `200 OK` → `TheaterResponseDto[]`
```json
[
  {
    "id": 1,
    "name": "Theater 1 — IMAX",
    "address": "456 Street",
    "phone": "+855 12 345 678",
    "status": "ACTIVE",
    "locationId": 1,
    "managerId": 3
  }
]
```

#### `GET /api/theaters/{id}`

**Response:** `200 OK` → `TheaterResponseDto`

#### `POST /api/theaters` — Staff/Admin

**Request Body:**
| Field | Type | Required |
|---|---|---|
| `name` | String | yes |
| `address` | String | yes |
| `phone` | String | yes |
| `status` | String | yes |
| `locationId` | Long | yes |
| `managerId` | Long | yes |

#### `PUT /api/theaters/{id}` — Staff/Admin

#### `DELETE /api/theaters/{id}` — Admin

---

### Screens `/api/screens`

#### `GET /api/screens`

**Response:** `200 OK` → `ScreenResponseDto[]`
```json
[
  {
    "id": 1,
    "name": "Screen A",
    "screenType": "IMAX",
    "status": "ACTIVE",
    "totalSeats": 200,
    "theaterId": 1
  }
]
```

#### `GET /api/screens/{id}`

#### `POST /api/screens` — Staff/Admin

**Request Body:**
| Field | Type | Required | Validation |
|---|---|---|---|
| `name` | String | yes | |
| `screenType` | String | yes | e.g., `IMAX`, `3D`, `STANDARD` |
| `status` | String | yes | |
| `totalSeats` | Integer | yes | Min 1 |
| `theaterId` | Long | yes | Must reference an existing theater |

#### `PUT /api/screens/{id}` — Staff/Admin

#### `DELETE /api/screens/{id}` — Admin

---

### Seats `/api/seats`

#### `GET /api/seats`

**Response:** `200 OK` → `SeatResponseDto[]`
```json
[
  {
    "id": 1,
    "price": 7.50,
    "rowName": "A",
    "seatNumber": "1",
    "seatType": "STANDARD",
    "status": "AVAILABLE",
    "screenId": 1
  }
]
```

#### `GET /api/seats/{id}`

#### `POST /api/seats` — Staff/Admin

**Request Body:**
| Field | Type | Required | Validation |
|---|---|---|---|
| `price` | BigDecimal | yes | Positive |
| `rowName` | String | yes | e.g., `A`, `B`, `C` |
| `seatNumber` | String | yes | e.g., `1`, `2` |
| `seatType` | String | yes | e.g., `STANDARD`, `VIP`, `COUPLE` |
| `status` | String | yes | `AVAILABLE`, `MAINTENANCE` |
| `screenId` | Long | yes | |

#### `PUT /api/seats/{id}` — Staff/Admin

#### `DELETE /api/seats/{id}` — Admin

---

### Movie Categories `/api/movie-category`

#### `GET /api/movie-category`

**Response:** `200 OK` → `CategoryResponseDto[]`
```json
[
  { "id": 1, "name": "Action", "description": "Action & adventure films", "isActive": true }
]
```

#### `GET /api/movie-category/{id}`

#### `POST /api/movie-category` — Staff/Admin

| Field | Type | Required |
|---|---|---|
| `name` | String | yes |
| `description` | String | no |
| `isActive` | Boolean | yes |

#### `PUT /api/movie-category/{id}` — Staff/Admin

#### `DELETE /api/movie-category/{id}` — Staff/Admin

---

### Movies `/api/movies`

#### `GET /api/movies`

**Response:** `200 OK` → `MovieResponseDto[]`
```json
[
  {
    "id": 1,
    "title": "The Batman",
    "genre": "Action",
    "language": "English",
    "posterUrl": "https://res.cloudinary.com/.../poster.jpg",
    "posterPublicId": "cinema/posters/abc123",
    "releaseDate": "2022-03-04",
    "durationMinutes": 176,
    "status": "NOW_SHOWING",
    "description": "Batman faces the Riddler...",
    "categoryId": 1
  }
]
```

#### `GET /api/movies/{id}`

#### `POST /api/movies` — Staff/Admin

| Field | Type | Required | Validation |
|---|---|---|---|
| `title` | String | yes | |
| `genre` | String | yes | |
| `language` | String | yes | |
| `posterUrl` | String | yes | URL to poster image |
| `releaseDate` | LocalDate | yes | Format: `YYYY-MM-DD` |
| `durationMinutes` | Integer | yes | Min 1 |
| `status` | String | yes | e.g., `NOW_SHOWING`, `COMING_SOON`, `ENDED` |
| `description` | String | no | |
| `categoryId` | Long | no | Reference to movie category |

#### `PUT /api/movies/{id}` — Staff/Admin

Same fields as `POST`.

#### `DELETE /api/movies/{id}` — Admin

---

### Shows `/api/shows`

#### `GET /api/shows`

**Response:** `200 OK` → `ShowResponseDto[]`
```json
[
  {
    "id": 1,
    "startTime": "2024-03-20T14:00:00",
    "endTime": "2024-03-20T16:30:00",
    "status": "SCHEDULED",
    "ticketPrice": 8.00,
    "movieId": 1,
    "screenId": 1
  }
]
```

#### `GET /api/shows/{id}`

#### `POST /api/shows` — Staff/Admin

| Field | Type | Required | Format |
|---|---|---|---|
| `startTime` | LocalDateTime | yes | `YYYY-MM-DDTHH:mm:ss` |
| `endTime` | LocalDateTime | yes | `YYYY-MM-DDTHH:mm:ss` |
| `status` | String | yes | `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `ticketPrice` | BigDecimal | yes | Positive number |
| `movieId` | Long | yes | |
| `screenId` | Long | yes | |

#### `PUT /api/shows/{id}` — Staff/Admin

#### `DELETE /api/shows/{id}` — Staff/Admin

---

### Bookings `/api/bookings` & Booking Seats `/api/booking-seats`

#### `POST /api/bookings` — Authenticated

| Field | Type | Required | Format |
|---|---|---|---|
| `bookingCode` | String | yes | Unique booking reference |
| `bookedAt` | LocalDateTime | yes | `YYYY-MM-DDTHH:mm:ss` |
| `totalAmount` | BigDecimal | yes | Positive |
| `customerId` | Long | yes | |
| `showId` | Long | yes | |

**Response:** `201 Created`
```json
{
  "id": 1,
  "bookedAt": "2024-03-20T10:00:00",
  "bookingCode": "BK-20240320-001",
  "status": "PENDING",
  "totalAmount": 16.00,
  "customerId": 1,
  "showId": 1
}
```

> **Status flow:** `PENDING` → `CONFIRMED` (after payment) → `COMPLETED` or `CANCELLED`

#### `POST /api/booking-seats` — Authenticated

Reserve a seat for an existing booking.

| Field | Type | Required |
|---|---|---|
| `bookingId` | Long | yes |
| `seatId` | Long | yes |

**Response:** `201 Created`
```json
{
  "id": 1,
  "price": 8.00,
  "status": "PENDING",
  "bookingId": 1,
  "seatId": 5
}
```

#### `GET /api/bookings` / `GET /api/bookings/{id}`
#### `PUT /api/bookings/{id}` / `DELETE /api/bookings/{id}`
#### `GET /api/booking-seats` / `GET /api/booking-seats/{id}`
#### `PUT /api/booking-seats/{id}` / `DELETE /api/booking-seats/{id}`

---

### Product Categories `/api/product-categories`

#### `GET /api/product-categories`

**Response:** `200 OK`
```json
[
  { "id": 1, "name": "Popcorn", "description": "Flavored popcorn varieties", "isActive": true }
]
```

#### `POST /api/product-categories` — Staff/Admin

| Field | Type | Required |
|---|---|---|
| `name` | String | yes |
| `description` | String | no |
| `isActive` | Boolean | yes |

#### Full CRUD: `GET /{id}`, `PUT /{id}`, `DELETE /{id}`

---

### Products `/api/products`

> **Important:** Products use **multipart/form-data** (not JSON) for create/update, because they support an optional image upload.

#### `GET /api/products`

**Response:** `200 OK` → `ProductResponseDto[]`
```json
[
  {
    "id": 1,
    "name": "Large Popcorn",
    "price": 5.50,
    "stockQuantity": 100,
    "isAvailable": true,
    "imageUrl": "https://res.cloudinary.com/.../popcorn.jpg",
    "imagePublicId": "cinema/products/popcorn123",
    "productCategoryId": 1,
    "createdAt": "2024-03-15T10:00:00",
    "updatedAt": "2024-03-15T10:00:00"
  }
]
```

#### `POST /api/products` — Staff/Admin (multipart/form-data)

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | String (form field) | yes | |
| `price` | BigDecimal (form field) | yes | Positive |
| `stockQuantity` | Integer (form field) | yes | Min 0 |
| `isAvailable` | Boolean (form field) | yes | |
| `productCategoryId` | Long (form field) | yes | |
| `image` | File (multipart) | no | Image file for product |

**Frontend example (fetch):**
```javascript
const formData = new FormData();
formData.append('name', 'Large Popcorn');
formData.append('price', '5.50');
formData.append('stockQuantity', '100');
formData.append('isAvailable', 'true');
formData.append('productCategoryId', '1');
formData.append('image', fileInput.files[0]); // optional

const res = await fetch('/api/products', {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${token}` },
  body: formData   // Do NOT set Content-Type — browser sets it with boundary
});
```

#### `PUT /api/products/{id}` — Staff/Admin (multipart/form-data)

Same fields as POST. Sending a new `image` replaces the old one (old image is deleted from Cloudinary).

#### `DELETE /api/products/{id}` — Staff/Admin

Also deletes the associated image from Cloudinary.

---

### Orders `/api/orders` & Order Items `/api/order-items`

#### `POST /api/orders` — Authenticated

| Field | Type | Required |
|---|---|---|
| `orderNumber` | String | yes |
| `orderType` | String | yes (e.g., `CONCESSION`, `BOOKING`) |
| `orderedAt` | LocalDateTime | yes |
| `completedAt` | LocalDateTime | yes |
| `status` | String | yes (e.g., `PENDING`, `PAID`, `COMPLETED`) |
| `subtotal` | BigDecimal | yes |
| `totalAmount` | BigDecimal | yes |
| `customerId` | Long | yes |
| `bookingId` | Long | yes |

#### `POST /api/order-items` — Authenticated

| Field | Type | Required |
|---|---|---|
| `quantity` | Integer | yes, min 1 |
| `subtotal` | BigDecimal | yes |
| `unitPrice` | BigDecimal | yes |
| `orderId` | Long | yes |
| `productId` | Long | yes |

**Response:** `201 Created`
```json
{
  "id": 1,
  "quantity": 2,
  "subtotal": 11.00,
  "unitPrice": 5.50,
  "orderId": 1,
  "productId": 1
}
```

#### Full CRUD for both resources: `GET /`, `GET /{id}`, `PUT /{id}`, `DELETE /{id}`

---

### Payments `/api/payments`

#### `POST /api/payments` — Authenticated

| Field | Type | Required | Notes |
|---|---|---|---|
| `amount` | BigDecimal | yes | Positive |
| `paymentMethod` | String | yes | `CASH` or `KHQR` |
| `customerId` | Long | yes | |
| `bookingId` | Long | no* | Provide at least one of `bookingId`/`orderId` |
| `orderId` | Long | no* | |
| `merchantName` | String | no | KHQR-specific |
| `accountId` | String | no | KHQR-specific |

**KHQR Response:** `201 Created`
```json
{
  "id": 1,
  "amount": 16.00,
  "paymentMethod": "KHQR",
  "status": "PENDING",
  "transactionId": "TXN-KHQR-A1B2C3D4E5F6",
  "paidAt": null,
  "expiresAt": "2024-03-20T14:30:00",
  "khqrString": "000201010212...",
  "md5Hash": "d41d8cd98f00b204e9800998ecf8427e",
  "bookingId": 1,
  "customerId": 1,
  "orderId": null
}
```

**CASH Response:** `201 Created`
```json
{
  "id": 2,
  "amount": 8.00,
  "paymentMethod": "CASH",
  "status": "PENDING",
  "transactionId": "TXN-CASH-F6E5D4C3B2A1",
  "paidAt": null,
  "expiresAt": null,
  "khqrString": null,
  "md5Hash": null,
  "bookingId": null,
  "customerId": 1,
  "orderId": 1
}
```

#### `POST /api/payments/{id}/confirm` — Staff/Admin

Manually confirms a payment (e.g., cash received). Transitions linked booking → `CONFIRMED`, order → `PAID`.

**Response:** `200 OK` → `PaymentResponseDto` (with `status: "PAID"`)

> This is idempotent — confirming an already-PAID payment returns the same result.

#### `GET /api/payments/{id}/status` — Authenticated

Poll this endpoint to check payment status. For KHQR payments, the server automatically checks with Bakong network.

**Behavior:**
- If `PENDING` and expired → status changes to `FAILED`
- If `PENDING` with valid KHQR → checks Bakong; auto-confirms if paid
- Otherwise returns current state

#### `GET /api/payments` / `GET /api/payments/{id}`
#### `PUT /api/payments/{id}` — Cannot update already-PAID payments
#### `DELETE /api/payments/{id}` — Admin

---

### Payment Transactions `/api/payment-transactions`

#### `GET /api/payment-transactions`

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "amount": 16.00,
    "status": "PENDING",
    "transactionType": "KHQR",
    "reference": "TXN-KHQR-A1B2C3D4E5F6",
    "createdAt": "2024-03-20T10:00:00",
    "paymentId": 1,
    "bookingId": 1,
    "orderId": null
  }
]
```

#### `GET /api/payment-transactions/by-payment/{paymentId}`

Get all transaction attempts for a specific payment (useful for KHQR retry history).

#### `POST /api/payment-transactions` — Authenticated

| Field | Type | Required |
|---|---|---|
| `amount` | BigDecimal | yes |
| `transactionType` | String | yes (`CASH` or `KHQR`) |
| `reference` | String | no |
| `paymentId` | Long | yes |
| `bookingId` | Long | no |
| `orderId` | Long | no |

#### Full CRUD: `GET /{id}`, `PUT /{id}` (Staff/Admin), `DELETE /{id}` (Admin)

---

## 6. Business Workflows

### Customer Booking Flow

```
1. Browse movies     GET /api/movies
2. View showtimes    GET /api/shows  (filter by movieId)
3. View seats        GET /api/seats  (filter by screenId)
4. Create booking    POST /api/bookings         → status: PENDING
5. Reserve seats     POST /api/booking-seats    (one per seat)
6. Create payment    POST /api/payments         → status: PENDING
   ├─ CASH:  Staff confirms   POST /api/payments/{id}/confirm
   └─ KHQR:  Customer scans QR, frontend polls GET /api/payments/{id}/status
             → Server auto-confirms when Bakong reports PAID
7. Order created     POST /api/orders           (linked to booking)
8. Add order items   POST /api/order-items      (concessions)
```

### KHQR Payment Polling (Frontend Implementation)

```javascript
async function pollPaymentStatus(paymentId, token) {
  const maxAttempts = 120; // 2 minutes at 1s intervals
  for (let i = 0; i < maxAttempts; i++) {
    const res = await fetch(`/api/payments/${paymentId}/status`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    const data = await res.json();

    if (data.status === 'PAID') return { success: true, data };
    if (data.status === 'FAILED') return { success: false, reason: 'expired' };

    await new Promise(r => setTimeout(r, 1000));
  }
  return { success: false, reason: 'timeout' };
}
```

### Role Permissions Summary

| Action | USER | STAFF | ADMIN |
|---|---|---|---|
| Browse movies, shows, seats | Yes | Yes | Yes |
| Create booking & order | Yes | Yes | Yes |
| Create payment | Yes | Yes | Yes |
| Confirm payment | No | Yes | Yes |
| Create/update movies, shows, locations, theaters, screens, seats | No | Yes | Yes |
| Delete movies, locations, theaters, screens, seats | No | No | Yes |
| Manage users | No | No | Yes |
| Delete payments, transactions | No | No | Yes |

---

## Enums Reference

| Enum | Values |
|---|---|
| `Role` | `USER`, `STAFF`, `ADMIN` |
| `BookingStatus` | `PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED` |
| `PaymentStatus` | `PENDING`, `PAID`, `FAILED` |
| `PaymentMethod` | `CASH`, `KHQR` |
