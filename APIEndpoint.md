# API Endpoints Specification

Complete REST API endpoint documentation for the Cinema Booking System, reflecting all RestControllers and Security permissions.

> [!TIP]
> Interactive OpenAPI documentation and test console are available via Swagger UI at:
> `http://localhost:8081/swagger-ui/index.html` (or port configured in `application.properties`).

---

## 1. Authentication (`/api/auth`)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register a new user account | Public |
| `POST` | `/api/auth/login` | Login and receive a JWT Bearer token | Public |

---

## 2. Movie Categories (`/api/movie-category`)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/movie-category` | List all movie categories | Authenticated |
| `GET` | `/api/movie-category/{id}` | Get movie category by ID | Authenticated |
| `POST` | `/api/movie-category` | Create movie category | Staff / Admin |
| `PUT` | `/api/movie-category/{id}` | Update movie category | Staff / Admin |
| `DELETE` | `/api/movie-category/{id}` | Delete movie category | Staff / Admin |

---

## 3. Movies (`/api/movies`)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/movies` | Get list of all movies | Authenticated |
| `GET` | `/api/movies/{id}` | Get movie details by ID | Authenticated |
| `POST` | `/api/movies` | Create a new movie | Staff / Admin |
| `PUT` | `/api/movies/{id}` | Update movie details | Staff / Admin |
| `DELETE` | `/api/movies/{id}` | Delete a movie | Admin |

---

## 4. Locations (`/api/locations`)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/locations` | List all locations | Authenticated |
| `GET` | `/api/locations/{id}` | Get location by ID | Authenticated |
| `POST` | `/api/locations` | Create a location | Staff / Admin |
| `PUT` | `/api/locations/{id}` | Update a location | Staff / Admin |
| `DELETE` | `/api/locations/{id}` | Delete a location | Admin |

---

## 5. Theaters (`/api/theaters`)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/theaters` | List all theaters | Authenticated |
| `GET` | `/api/theaters/{id}` | Get theater by ID | Authenticated |
| `POST` | `/api/theaters` | Create a theater | Staff / Admin |
| `PUT` | `/api/theaters/{id}` | Update a theater | Staff / Admin |
| `DELETE` | `/api/theaters/{id}` | Delete a theater | Admin |

---

## 6. Screens (`/api/screens`)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/screens` | List all cinema screens | Authenticated |
| `GET` | `/api/screens/{id}` | Get screen by ID | Authenticated |
| `POST` | `/api/screens` | Create a new screen | Staff / Admin |
| `PUT` | `/api/screens/{id}` | Update a screen | Staff / Admin |
| `DELETE` | `/api/screens/{id}` | Delete a screen | Admin |

---

## 7. Seats (`/api/seats`)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/seats` | List all seats | Authenticated |
| `GET` | `/api/seats/{id}` | Get seat details by ID | Authenticated |
| `POST` | `/api/seats` | Create a seat | Staff / Admin |
| `PUT` | `/api/seats/{id}` | Update a seat | Staff / Admin |
| `DELETE` | `/api/seats/{id}` | Delete a seat | Admin |

---

## 8. Shows / Showtimes (`/api/shows`)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/shows` | List all showtimes | Authenticated |
| `GET` | `/api/shows/{id}` | Get showtime by ID | Authenticated |
| `POST` | `/api/shows` | Create a showtime | Staff / Admin |
| `PUT` | `/api/shows/{id}` | Update a showtime | Staff / Admin |
| `DELETE` | `/api/shows/{id}` | Delete a showtime | Staff / Admin |

---

## 9. Bookings & Booking Seats (`/api/bookings`, `/api/booking-seats`)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/bookings` | List all bookings | Authenticated |
| `GET` | `/api/bookings/{id}` | Get booking by ID | Authenticated |
| `POST` | `/api/bookings` | Create a new booking | Authenticated |
| `PUT` | `/api/bookings/{id}` | Update a booking | Authenticated |
| `DELETE` | `/api/bookings/{id}` | Cancel/delete a booking | Authenticated |
| `GET` | `/api/booking-seats` | List reserved booking seats | Authenticated |
| `GET` | `/api/booking-seats/{id}` | Get booking seat by ID | Authenticated |
| `POST` | `/api/booking-seats` | Reserve seat for booking | Authenticated |
| `PUT` | `/api/booking-seats/{id}` | Update reserved seat | Authenticated |
| `DELETE` | `/api/booking-seats/{id}` | Release reserved seat | Authenticated |

---

## 10. Concessions & Products (`/api/product-categories`, `/api/products`)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/product-categories` | List food/beverage categories | Authenticated |
| `GET` | `/api/product-categories/{id}` | Get category by ID | Authenticated |
| `POST` | `/api/product-categories` | Create product category | Staff / Admin |
| `PUT` | `/api/product-categories/{id}` | Update product category | Staff / Admin |
| `DELETE` | `/api/product-categories/{id}` | Delete product category | Staff / Admin |
| `GET` | `/api/products` | List all products | Authenticated |
| `GET` | `/api/products/{id}` | Get product by ID | Authenticated |
| `POST` | `/api/products` | Create a product (`multipart/form-data` with optional `image` file) | Staff / Admin |
| `PUT` | `/api/products/{id}` | Update a product (`multipart/form-data` with optional `image` file) | Staff / Admin |
| `DELETE` | `/api/products/{id}` | Delete a product (removes image from Cloudinary) | Staff / Admin |

---

## 11. Orders & Order Items (`/api/orders`, `/api/order-items`)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/orders` | List customer orders | Authenticated |
| `GET` | `/api/orders/{id}` | Get order by ID | Authenticated |
| `POST` | `/api/orders` | Place a new order | Authenticated |
| `PUT` | `/api/orders/{id}` | Update an order | Authenticated |
| `DELETE` | `/api/orders/{id}` | Cancel/delete an order | Authenticated |
| `GET` | `/api/order-items` | List all order items | Authenticated |
| `GET` | `/api/order-items/{id}` | Get order item by ID | Authenticated |
| `POST` | `/api/order-items` | Add item to order | Authenticated |
| `PUT` | `/api/order-items/{id}` | Update order item | Authenticated |
| `DELETE` | `/api/order-items/{id}` | Remove order item | Authenticated |

---

## 12. Payments (`/api/payments`)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/payments` | List all payments | Authenticated |
| `GET` | `/api/payments/{id}` | Get payment by ID | Authenticated |
| `POST` | `/api/payments` | Create a payment (`paymentMethod`: `CASH` or `KHQR`) | Authenticated |
| `POST` | `/api/payments/{id}/confirm` | Confirm payment (Staff/Cash confirmation & activates booking) | Staff / Admin |
| `GET` | `/api/payments/{id}/status` | Check/poll payment status (for KHQR polling) | Authenticated |
| `PUT` | `/api/payments/{id}` | Update payment record | Staff / Admin |
| `DELETE` | `/api/payments/{id}` | Delete payment record | Admin |

---

## 13. Payment Transactions (`/api/payment-transactions`)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/payment-transactions` | List all payment transaction logs | Authenticated |
| `GET` | `/api/payment-transactions/{id}` | Get payment transaction by ID | Authenticated |
| `GET` | `/api/payment-transactions/by-payment/{paymentId}` | Get all transaction attempts for a specific payment | Authenticated |
| `POST` | `/api/payment-transactions` | Create payment transaction | Authenticated |
| `PUT` | `/api/payment-transactions/{id}` | Update payment transaction | Staff / Admin |
| `DELETE` | `/api/payment-transactions/{id}` | Delete transaction record | Admin |

---

## 14. User Management (`/api/users`)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/users` | List all users | Admin |
| `GET` | `/api/users/{id}` | Get user by ID | Admin |
| `POST` | `/api/users` | Create user | Admin |
| `PUT` | `/api/users/{id}` | Update user details | Admin |
| `DELETE` | `/api/users/{id}` | Delete user | Admin |
