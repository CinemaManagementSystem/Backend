# Frontend API Guide — Cinema Booking System

> **API base URL:** `http://localhost:8081/api`
> **Backend root:** `http://localhost:8081`
> **Content-Type:** `application/json` (unless noted otherwise)  
> **Interactive Docs:** `http://localhost:8081/swagger-ui.html`

---

## Table of Contents

0. [Frontend Setup](#0-frontend-setup)
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

## 0. Frontend Setup

### Local development

Start the backend with Docker Compose. The frontend runs separately (usually on
`http://localhost:5173`) and calls the backend through the API base URL above.

```bash
docker compose up -d db app
```

Useful local URLs:

| Purpose | URL |
|---|---|
| API base | `http://localhost:8081/api` |
| Swagger UI | `http://localhost:8081/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8081/v3/api-docs` |
| phpMyAdmin | `http://localhost:8083` |

The frontend origin must be listed in `CORS_ALLOWED_ORIGINS`. The Compose setup
already allows `http://localhost:5173`, `http://127.0.0.1:5173`, and
`http://localhost:3000`. If the frontend uses another origin, add it to the
backend environment and restart the app.

### Frontend environment variable

Keep the `/api` suffix in the frontend variable so calls stay consistent:

```env
VITE_API_BASE_URL=http://localhost:8081/api
# or, for Create React App:
REACT_APP_API_BASE_URL=http://localhost:8081/api
```

Example request helper:

```javascript
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export async function apiFetch(path, options = {}) {
  const token = localStorage.getItem('accessToken');
  const headers = new Headers(options.headers || {});
  if (options.body && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw Object.assign(new Error(error.message || 'Request failed'), {
      status: response.status,
      details: error.details
    });
  }
  return response.status === 204 ? null : response.json();
}
```

### Backend configuration that affects the frontend

| Property / environment variable | Default | Frontend impact |
|---|---|---|
| `SERVER_PORT` | `8081` | Changes the backend root/API URL |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000` | Must include the exact frontend origin |
| `JWT_EXPIRATION` | `86400000` ms | Access-token lifetime; use refresh before expiry |
| `APP_SEED_ENABLED` | `false` | Enables development seed data when set to `true` |
| `APP_SCHEDULING_ENABLED` | `true` | Enables booking/payment expiry jobs |
| `BOOKING_HOLD_TTL_MINUTES` | `5` | Booking expires five minutes before showtime |
| `BAKONG_MOCK_MODE` | `false` | In mock mode, the test paid MD5 is `deadbeefdeadbeefdeadbeefdeadbeef` |
| `RATE_LIMIT_ENABLED` | `false` | Enables the API rate limits documented below |
| `BAKONG_POLLING_RATE_MS` | `60000` | Backend polling interval for KHQR status |
| `BOOKING_EXPIRY_RATE_MS` | `120000` | Backend interval for expiring unpaid bookings |

Do not expose database, JWT secret, Cloudinary secret, or Bakong token values in
frontend environment variables. They belong only in the backend `.env`.

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
  "refreshToken": "refresh-token-value",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": {
    "id": 1,
    "username": "john",
    "email": "john@example.com",
    "role": "ROLE_USER"
  }
}
```

Store both tokens. Send the access token as a Bearer token; keep the refresh
token in a protected client-side store and replace both tokens whenever refresh
returns successfully.

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
    "role": "ROLE_USER"
  }
}
```

> **Note:** New accounts are created with role `ROLE_USER` and status `ACTIVE`; they can log in immediately.

### Refreshing, checking, and ending a session

Refresh tokens are rotated, so always replace the stored refresh token with the
one returned by this endpoint.

```http
POST /api/auth/refresh
Content-Type: application/json
```

```json
{ "refreshToken": "refresh-token-value" }
```

`POST /api/auth/refresh` returns the same `AuthResponseDto` shape as login.
`GET /api/auth/me` returns the current authenticated user and requires the
access-token header. To log out, call `POST /api/auth/logout` with the access
token and, when available, this body:

```json
{ "refreshToken": "refresh-token-value" }
```

Logout revokes the supplied refresh token and/or access token. Clear both local
tokens after a successful logout.

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
| **Decimal format** | JSON numbers (e.g., `12.50`) for `BigDecimal` fields; use a decimal-safe type in the frontend for money |
| **Authentication** | Protected endpoints require `Authorization: Bearer <accessToken>` |
| **Role format** | Auth responses use `ROLE_USER`, `ROLE_STAFF`, or `ROLE_ADMIN` |
| **Business time zone** | `Asia/Phnom_Penh`; `LocalDateTime` values have no timezone suffix |

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

Rate limits are endpoint-specific and use a 60-second window.

| Method | Endpoint | Limit | Window | Rate-limit key | Auth | Role |
|---|---|---:|---|---|---|---|
| `POST` | `/api/auth/login` | 5 | 1 minute | IP + username/email when present | No | Public |
| `POST` | `/api/auth/register` | 3 | 1 minute | IP | No | Public |
| `POST` | `/api/auth/refresh` | 10 | 1 minute | IP + refresh-token fingerprint when present | No | Public |
| `POST` | `/api/auth/logout` | 20 | 1 minute | Authenticated user, fallback IP | Token recommended | User |
| `POST` | `/api/payments` | 5 | 1 minute | Authenticated user, fallback IP | Yes | USER/STAFF/ADMIN |
| `GET` | `/api/payments/{id}/status` | 30 | 1 minute | User + payment status path | Yes | Owner, STAFF, ADMIN |
| `POST` | `/api/payments/{id}/confirm` | 10 | 1 minute | Authenticated user, fallback IP | Yes | STAFF, ADMIN |
| `POST` | `/api/bookings` | 10 | 1 minute | Authenticated user, fallback IP | Yes | USER/STAFF/ADMIN |
| `POST` | `/api/booking-seats` | 30 | 1 minute | Authenticated user, fallback IP | Yes | USER/STAFF/ADMIN |
| `POST` | `/api/orders` | 10 | 1 minute | Authenticated user, fallback IP | Yes | USER/STAFF/ADMIN |
| `POST` | `/api/order-items` | 30 | 1 minute | Authenticated user, fallback IP | Yes | USER/STAFF/ADMIN |
| `POST` | `/api/products` | 10 | 1 minute | Authenticated user, fallback IP | Yes | STAFF, ADMIN |
| `PUT` | `/api/products/{id}` | 10 | 1 minute | Authenticated user, fallback IP | Yes | STAFF, ADMIN |
| `POST/PUT/PATCH/DELETE` | `/api/locations/**` | 30 | 1 minute | Authenticated user, fallback IP | Yes | STAFF, ADMIN or stricter route rule |
| `POST/PUT/PATCH/DELETE` | `/api/theaters/**` | 30 | 1 minute | Authenticated user, fallback IP | Yes | STAFF, ADMIN or stricter route rule |
| `POST/PUT/PATCH/DELETE` | `/api/screens/**` | 30 | 1 minute | Authenticated user, fallback IP | Yes | STAFF, ADMIN or stricter route rule |
| `POST/PUT/PATCH/DELETE` | `/api/seats/**` | 30 | 1 minute | Authenticated user, fallback IP | Yes | STAFF, ADMIN or stricter route rule |
| `POST/PUT/PATCH/DELETE` | `/api/movie-category/**` | 30 | 1 minute | Authenticated user, fallback IP | Yes | STAFF, ADMIN |
| `POST/PUT/PATCH/DELETE` | `/api/movies/**` | 30 | 1 minute | Authenticated user, fallback IP | Yes | STAFF, ADMIN or stricter route rule |
| `POST/PUT/PATCH/DELETE` | `/api/shows/**` | 30 | 1 minute | Authenticated user, fallback IP | Yes | STAFF, ADMIN |
| `POST/PUT/PATCH/DELETE` | `/api/users/**` | 30 | 1 minute | Authenticated user, fallback IP | Yes | ADMIN |
| `POST/PUT/PATCH/DELETE` | `/api/product-categories/**` | 30 | 1 minute | Authenticated user, fallback IP | Yes | STAFF, ADMIN |
| `POST/PUT/PATCH/DELETE` | `/api/payment-transactions/**` | 30 | 1 minute | Authenticated user, fallback IP | Yes | STAFF, ADMIN |
| `GET` | `/api/**` read fallback | 100 | 1 minute | IP | Depends on endpoint | Public/authenticated |
| Any | `/api/**` fallback | 100 | 1 minute | IP | Depends on endpoint | Depends on endpoint |

When rate-limited, the response includes:
```
429 Too Many Requests
Retry-After: <seconds>
Content-Type: application/json
```

The response body uses the normal API error format. Proxy headers such as `X-Forwarded-For` are not trusted for rate-limit keys unless proxy trust is explicitly enabled in backend configuration.

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
  "user": { "id": 1, "username": "john", "email": "john@example.com", "role": "ROLE_USER" }
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
  "refreshToken": "refresh-token-value",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": {
    "id": 1,
    "username": "john",
    "email": "john@example.com",
    "role": "ROLE_USER"
  }
}
```

#### `POST /api/auth/refresh`

Send `{ "refreshToken": "..." }`. The response has the same shape as login,
including a new access token and a rotated refresh token.

#### `GET /api/auth/me`

Returns the current `UserResponseDto`. Requires `Authorization: Bearer <accessToken>`.

#### `POST /api/auth/logout`

The endpoint is public at the HTTP security layer so clients can safely call it
when an access token is already expired. Send the access token when available
and optionally `{ "refreshToken": "..." }` to revoke the refresh token too.

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
| `customerId` | Long | yes | Must be the logged-in user for a customer request |
| `showId` | Long | yes | Existing show |
| `bookingCode` | String | no | Ignored on create; generated by the server |
| `bookedAt` | LocalDateTime | no | Ignored on create; set by the server |
| `totalAmount` | BigDecimal | no | Ignored on create; calculated from reserved seats and orders |

Minimal request:

```json
{ "customerId": 1, "showId": 1 }
```

**Response:** `201 Created`
```json
{
  "id": 1,
  "bookedAt": "2024-03-20T10:00:00",
  "expiresAt": "2024-03-20T13:55:00",
  "bookingCode": "BK-20240320-001",
  "status": "PENDING",
  "totalAmount": 0.00,
  "customerId": 1,
  "showId": 1
}
```

The server sets `PENDING`, generates the booking code/time, and calculates the
amount. Add booking seats before creating a payment, then use the latest
server-returned total. By default, `expiresAt` is five minutes before the show
starts (`BOOKING_HOLD_TTL_MINUTES` controls this behavior).

> **Status flow:** `PENDING` → `CONFIRMED` (after payment) → `COMPLETED` or `CANCELLED`; an unpaid hold can become `EXPIRED`.

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
  "expiresAt": "2024-03-20T13:55:00",
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
| `bookingId` | Long | no | Link to a booking when paying for a booking |
| `orderId` | Long | no | Link to an order when paying for an order |
| `merchantName` | String | no | KHQR-specific |
| `accountId` | String | no | KHQR-specific |

For a customer purchase, provide the relevant `bookingId` and/or `orderId`.
The server compares `amount` with the current server-side total; do not trust a
price calculated only in the frontend. A booking payment must target a `PENDING`
booking whose hold has not expired. Retrying an existing pending payment reuses
that payment instead of creating a duplicate.

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
  "transactionId": null,
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

#### `GET /api/payments/{id}/status?source=MANUAL|SCHEDULED|FINAL` — Authenticated

Poll this endpoint to check payment status. For KHQR payments, the server automatically checks with Bakong network.

The checkout UI schedules `SCHEDULED` checks at 60, 120, 180, and 240
seconds, then sends one `FINAL` check at expiry. `MANUAL` is limited to two
provider checks per payment. `SYSTEM` is reserved for backend jobs and is
rejected on this HTTP endpoint.

**Behavior:**
- If `PENDING` and paid is confirmed by Bakong → status changes to `PAID`
- If the payment/booking hold expires → status changes to `EXPIRED`
- If Bakong returns an authoritative not-found/unpaid result, the payment can
  remain `PENDING` until its expiry; keep polling while `expiresAt` is still in
  the future
- Transport, configuration, or invalid-response failures do not mean the user
  has paid; keep the payment pending and retry with exponential backoff
- Bakong daily-rate-limit responses remain `PENDING` locally and expose
  `lastVerificationError` plus `rateLimitedUntil`; they never become `FAILED`
- Calls made before `nextVerificationAt`, concurrent duplicate calls, and calls
  for terminal payments return the current record without contacting Bakong

Treat `PAID`, `FAILED`, and `EXPIRED` as terminal statuses. A `CASH` payment
remains pending until Staff/Admin calls the confirm endpoint. The response
fields `khqrString`, `md5Hash`, and `expiresAt` are populated for KHQR and are
`null` for CASH. Verification diagnostics include `lastVerificationAt`,
`nextVerificationAt`, `verificationAttemptCount`, `manualVerificationCount`,
`lastVerificationError`, and `rateLimitedUntil`.

#### `GET /api/payments` / `GET /api/payments/{id}`

Normal users can access only their own payment records. Staff/Admin can access operational payment records.
#### `PUT /api/payments/{id}` — Cannot update already-PAID payments
#### `DELETE /api/payments/{id}` — Admin

---

### Payment Transactions `/api/payment-transactions`

#### `GET /api/payment-transactions`

Normal users receive only their own payment transaction records. Staff/Admin can read operational transaction records.

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

Get all transaction attempts for a specific payment. Normal users can access only transactions for their own payments.

Payment transaction records are normally created by backend payment logic. Customer users cannot create manual payment audit records; the write endpoints require Staff/Admin.

#### `POST /api/payment-transactions` - Staff/Admin

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
4. Create booking    POST /api/bookings         → status: PENDING, total: 0
5. Reserve seats     POST /api/booking-seats    (one per seat; total recalculates)
6. Optional order    POST /api/orders, then POST /api/order-items
7. Create payment    POST /api/payments         → status: PENDING
   ├─ CASH:  Staff confirms   POST /api/payments/{id}/confirm
   └─ KHQR:  Customer scans QR, frontend polls GET /api/payments/{id}/status
             → Server auto-confirms when Bakong reports PAID
```

Create the order and order items before payment when concessions are part of the
same checkout. After every seat/order change, use the latest booking/order
response to obtain the amount sent to `POST /api/payments`.

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
    if (data.status === 'FAILED' || data.status === 'EXPIRED') {
      return { success: false, reason: 'expired' };
    }

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
| `BookingStatus` | `PENDING`, `CONFIRMED`, `CANCELLED`, `EXPIRED`, `COMPLETED` |
| `PaymentStatus` | `PENDING`, `PAID`, `FAILED`, `EXPIRED` |
| `PaymentMethod` | `CASH`, `KHQR` |
