# 🎬 Cinema Booking System — Backend Platform

A production-grade REST API backend for a complete Cinema Booking and Ticketing Platform built with **Java 21**, **Spring Boot 3.3.2**, **Spring Security 6 (JWT)**, and **MySQL**.

```
Client / Swagger UI  ──(JWT Bearer)──▶  Controller  ──▶  Service (Transactions)  ──▶  Repository (JPA)  ──▶  MySQL
```

---

## 📑 Table of Contents
- [✨ Key Features](#-key-features)
- [🛠️ Tech Stack & Prerequisites](#️-tech-stack--prerequisites)
- [🚀 Quick Start (Team Onboarding)](#-quick-start-team-onboarding)
  - [Option A: Run with Docker Compose (Recommended)](#option-a-run-with-docker-compose-recommended)
  - [Option B: Run Locally with Maven](#option-b-run-locally-with-maven)
- [🔑 Default Admin Credentials](#-default-admin-credentials)
- [📖 API Documentation & Swagger](#-api-documentation--swagger)
- [📂 Project Architecture & Package Structure](#-project-architecture--package-structure)
- [🧪 Testing](#-testing)
- [📚 Documentation Index](#-documentation-index)

---

## ✨ Key Features

- **JWT Authentication & Authorization**: Stateless security filter chain with BCrypt password hashing and role-based access (`ROLE_USER`, `ROLE_ADMIN`).
- **Complete Domain Coverage (16 Entities + Auth)**:
  - 👤 **Users & Wallets**: User registration/login, customer wallets, transactions (`/api/auth`, `/api/users`, `/api/wallets`, `/api/wallet-transactions`)
  - 🏢 **Cinemas & Rooms**: Theaters, physical locations, screens, and seat maps (`/api/locations`, `/api/theaters`, `/api/screens`, `/api/seats`)
  - 🍿 **Concessions & Products**: Categories, food & beverages, popcorn, drinks (`/api/product-categories`, `/api/products`) with Cloudinary image storage
  - 🎟️ **Movies & Shows**: Active movie catalog, scheduling showtimes (`/api/movies`, `/api/shows`) with Cloudinary movie poster storage
  - 🛒 **Ticketing & Orders**: Seat locking, booking reservations, concession orders (`/api/bookings`, `/api/booking-seats`, `/api/orders`, `/api/order-items`)
  - 💳 **Payments**: Payment processing and wallet balance deductions (`/api/payments`)
- **Validation**: Strict request payload validation using `jakarta.validation` (`@Valid`, `@NotBlank`, `@NotNull`, `@Min`, `@Email`).
- **Centralized Error Handling**: Standardized JSON responses for 400, 401, 403, 404, 409, and 500 status codes.

---

## 🛠️ Tech Stack & Prerequisites

| Concern | Technology / Version |
|---|---|
| **Language** | Java 21 (JDK 21) |
| **Framework** | Spring Boot 3.3.2 |
| **Security** | Spring Security 6 + JJWT 0.11.5 (HMAC-SHA256) |
| **Database** | MySQL 8.0+ |
| **ORM / Data Access** | Spring Data JPA (Hibernate 6) |
| **API Docs** | springdoc-openapi 2.5.0 (Swagger UI) |
| **Dev Tools** | Spring Boot DevTools (Live Auto-Reload) |
| **Build Tool** | Maven (wrappers `mvnw` / `mvnw.cmd` included) |
| **Containers** | Docker & Docker Compose |

---

## 🚀 Quick Start (Team Onboarding)

### 1. Clone the Repository
```bash
git clone https://github.com/Sothearith22/cenima_final_project.git
cd Backend
```

### 2. Environment Configuration
Copy the template `.env.example` into a local `.env` file:

```bash
# On Linux / macOS / Git Bash:
cp .env.example .env

# On Windows PowerShell:
Copy-Item .env.example .env
```

---

### Option A: Run with Docker Compose (Recommended)

Requires only **Docker Desktop** installed. Starts MySQL 8, the Spring Boot app, and phpMyAdmin with a single command:

```bash
# Start all services in the background
docker compose up -d
```

| Service | URL | Credentials / Notes |
|---|---|---|
| **Spring Boot API** | [http://localhost:8081](http://localhost:8081) | App backend port |
| **Swagger UI** | [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html) | Interactive API testing console |
| **phpMyAdmin** | [http://localhost:8083](http://localhost:8083) | User: `cinema_user` \| Password: `cinema_pass` |
| **MySQL Container** | `localhost:3307` | Database: `cinema_db` |

To stop the Docker environment:
```bash
docker compose down
```

---

### Option B: Run Locally with Maven

If you prefer running the application directly on your machine:

1. **Start a local MySQL instance** on port `3306` with database `cinema_db` (or configure your DB credentials in `.env`).
2. **Build and test the project**:
   ```bash
   # Windows:
   .\mvnw.cmd clean test

   # Linux / macOS:
   ./mvnw clean test
   ```
3. **Run the Spring Boot application**:
   ```bash
   # Windows:
   .\mvnw.cmd spring-boot:run

   # Linux / macOS:
   ./mvnw spring-boot:run
   ```

   > [!TIP]
   > **Spring Boot DevTools** is enabled. When running locally, recompiling or modifying Java source files will automatically restart the application without needing a full manual restart.

4. Access the API and Swagger UI at **`http://localhost:8081/swagger-ui/index.html`**.

---

## 🌱 Comprehensive Database Seeding

When the backend starts up in non-test profiles, `DatabaseSeeder` automatically initializes all foundation master data if not already present:

### 1. Default Accounts & Wallets
| Role | Username | Email | Password | Initial Balance | Authorities |
|---|---|---|---|---|---|
| **Admin** | `admin` | `admin@cinema.com` | `Admin123` | `$500.00 USD` | `ROLE_ADMIN` |
| **Staff** | `staff` | `staff@cinema.com` | `Staff123` | `$200.00 USD` | `ROLE_STAFF` |
| **User** | `user` | `user@cinema.com` | `User123` | `$100.00 USD` | `ROLE_USER` |

### 2. Master & Hierarchy Reference Data
- 📍 **Locations**: Phnom Penh Central, Aeon Sen Sok City, Siem Reap Riverside
- 🏢 **Theaters**: Legend Cinema Central, Major Cineplex Sen Sok, Prime Cineplex Siem Reap
- 📺 **Screens**: Hall 1 (IMAX), Hall 2 (VIP), Hall 1 (Premium), Hall 1 (Deluxe)
- 💺 **Seats**: Grid rows (A–E, 1–10) with `STANDARD`, `VIP`, and `COUPLE` seat pricing
- 🎬 **Movie Categories & Movies**:
  - Categories: Sci-Fi & Fantasy, Action & Adventure, Animation, Horror & Thriller, Drama & Romance
  - Movies: *Inception*, *Interstellar*, *Avengers: Endgame*, *Neon Nights*
- 🍿 **Concessions & Products**:
  - Categories: Popcorn, Beverages, Snacks, Combos
  - Products: Caramel Popcorn (L), Salted Popcorn (M), Coca-Cola 500ml, Mineral Water, Nachos & Cheese, Hot Dogs, Movie Night Combos

---

## 🛡️ Security Architecture & Rate Limiting

The backend implements a multi-layered security pipeline:
1. **Authentication & Flexible Login**: Supports login via username or email; validates user active status.
2. **JWT Security**: HMAC-SHA256 signed tokens containing user authorities with token validation & error resilience.
3. **Role Hierarchy**: `ROLE_ADMIN > ROLE_STAFF > ROLE_USER` via Spring Security 6 Method Security.
4. **Rate Limiting**: Thread-safe sliding-window rate limiting per IP:
   - `/api/auth/**`: 10 requests / minute (Brute-force protection).
   - `/api/**`: 100 requests / minute (DoS protection).
   - Responds with `429 Too Many Requests` and `Retry-After: 60` header.
5. **Input Validation**: Strict `@Valid` schema enforcement with clear field error reporting.
6. **CORS**: Configurable allowed origins, headers, methods, credentials, and preflight max-age.
7. **Password Hashing**: BCrypt (strength 12) across authentication, user management, and seeders.
8. **Secure Error Handling**: Centralized `GlobalExceptionHandler` with standardized JSON error payloads (400, 401, 403, 404, 409, 429, 500) preventing sensitive server stack leakages.

---

## 📖 API Documentation & Swagger

Interactive Swagger OpenAPI documentation is available once the server is running:

- **Swagger UI**: [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html) (or `/swagger-ui.html`)
- **OpenAPI JSON Spec**: `http://localhost:8081/v3/api-docs`

To authenticate in Swagger UI:
1. Call `POST /api/auth/login` with admin or customer credentials.
2. Copy the returned `accessToken`.
3. Click the **Authorize** button (top right in Swagger UI) and paste the token.

---

## 📂 Project Architecture & Package Structure

The codebase strictly follows a **layered enterprise architecture**:

```
src/main/java/com/cinema/booking/
├── config/                  # SecurityConfig, OpenApiConfig, DatabaseSeeder
├── controller/              # REST Endpoints with validation annotations
│   └── auth/                # AuthController (login, register)
├── dto/                     # Feature-based Data Transfer Objects
│   ├── auth/                # LoginRequestDto, RegisterRequestDto, AuthResponseDto, RegisterResponseDto
│   ├── bookings/            # BookingRequestDto, BookingResponseDto, BookingSeat...
│   ├── cinemas/             # TheaterRequestDto, TheaterResponseDto, Location...
│   ├── movies/              # MovieRequestDto, MovieResponseDto
│   ├── orders/              # OrderRequestDto, OrderResponseDto, OrderItem...
│   ├── payments/            # PaymentRequestDto, PaymentResponseDto
│   ├── products/            # ProductRequestDto, ProductResponseDto, Category...
│   ├── rooms/               # ScreenRequestDto, ScreenResponseDto, Seat...
│   ├── shows/               # ShowRequestDto, ShowResponseDto
│   ├── users/               # UserRequestDto, UserResponseDto
│   └── wallets/             # WalletRequestDto, WalletResponseDto, WalletTransaction...
├── entity/                  # JPA Entities (16 relational tables)
├── enums/                   # Role, BookingStatus, PaymentStatus, SeatType, ScreenType
├── exception/               # Custom Exceptions, ErrorResponse, GlobalExceptionHandler
├── mapper/                  # Pure scalar DTO ⇄ Entity mappers
├── repository/              # Spring Data JPA repositories
├── security/                # JwtService, JwtAuthenticationFilter, CustomUserDetailsService
├── service/                 # Business logic interfaces
│   └── impl/                # Transactional service implementations
└── util/                    # AppConstants, SecurityUtil
```

---

## 🧪 Testing

The repository includes a comprehensive test suite (Unit & MockMvc Integration tests) running on an in-memory H2 database (MySQL compatibility mode):

```bash
# Run all tests:
./mvnw test        # Linux / macOS
.\mvnw.cmd test    # Windows
```

---

## 📚 Documentation Index

| Document | Purpose |
|---|---|
| [**`APIEndpoint.md`**](APIEndpoint.md) | Complete REST API endpoint reference and role permissions |
| [**`Structure.md`**](Structure.md) | Comprehensive directory tree and layer responsibility guide |
| [**`SystemFlow.md`**](SystemFlow.md) | Visual workflow and sequence diagrams for Customers and Admins |
| [**`business_flow.md`**](business_flow.md) | Cinema system domain architecture & business entity tree |
| [**`agent_guide.md`**](agent_guide.md) | Developer guidelines, coding standards, and architectural rules |
| [**`cinema_project_flow.md`**](cinema_project_flow.md) | Relational schema and transaction life-cycle documentation |
| [**`src/main/doc/A_practical_workflow.md`**](src/main/doc/A_practical_workflow.md) | Practical Git branch workflow (main, dev, features) with CI/Qodana |
| [**`src/main/doc/StoreImageFlow.md`**](src/main/doc/StoreImageFlow.md) | Diagram showing the Product image storage flow (Cloudinary + DB) |
| [**`src/main/doc/MovieCRUDflow.md`**](src/main/doc/MovieCRUDflow.md) | Diagram showing the Movie poster upload flow (Cloudinary + DB) |
| [**`src/main/doc/cicd.md`**](src/main/doc/cicd.md) | CI/CD Git branch promotion and testing strategy |

