# Project Architecture & Directory Structure

## 1. Directory Tree

```
cinema-booking-system/
├── .github/                      # GitHub configurations and workflows
├── .idea/                        # IDE workspace settings
├── src/
│   ├── main/
│   │   ├── java/com/cinema/booking/
│   │   │   ├── config/           # SecurityConfig, RoleHierarchy, DatabaseSeeder, OpenApiConfig
│   │   │   ├── controller/       # REST API endpoints and validation triggers
│   │   │   │   └── auth/         # Authentication endpoints (login, register)
│   │   │   ├── dto/              # Feature-based Data Transfer Objects
│   │   │   │   ├── auth/         # LoginRequestDto, RegisterRequestDto, AuthResponseDto, RegisterResponseDto
│   │   │   │   ├── bookings/     # BookingRequestDto, BookingResponseDto, BookingSeat...
│   │   │   │   ├── cinemas/      # TheaterRequestDto, TheaterResponseDto, Location...
│   │   │   │   ├── movies/       # MovieRequestDto, MovieResponseDto
│   │   │   │   ├── orders/       # OrderRequestDto, OrderResponseDto, OrderItem...
│   │   │   │   ├── payments/     # PaymentRequestDto, PaymentResponseDto
│   │   │   │   ├── products/     # ProductRequestDto, ProductResponseDto, Category...
│   │   │   │   ├── rooms/        # ScreenRequestDto, ScreenResponseDto, Seat...
│   │   │   │   ├── shows/        # ShowRequestDto, ShowResponseDto
│   │   │   │   ├── users/        # UserRequestDto, UserResponseDto
│   │   │   │   └── wallets/      # WalletRequestDto, WalletResponseDto, WalletTransaction...
│   │   │   ├── entity/           # JPA Entities (mapped 1:1 with MySQL tables)
│   │   │   ├── enums/            # Domain enums (Role: USER, STAFF, ADMIN; BookingStatus; PaymentStatus)
│   │   │   ├── exception/        # GlobalExceptionHandler, RateLimitExceededException, ResourceNotFoundException...
│   │   │   ├── mapper/           # Pure scalar DTO ⇄ Entity conversion mappers
│   │   │   ├── repository/       # Spring Data JPA Repository interfaces
│   │   │   ├── security/         # JwtService, JwtAuthenticationFilter, CustomUserDetailsService
│   │   │   │   └── ratelimit/    # RateLimiterService, RateLimitingFilter (Sliding Window IP Limiter)
│   │   │   ├── service/          # Service layer interfaces
│   │   │   │   └── impl/         # Transactional service implementations & business logic
│   │   │   ├── util/             # Application constants (AppConstants) and security utilities (SecurityUtil)
│   │   │   └── CinemaBookingSystemApplication.java
│   │   └── resources/
│   │       ├── application.properties # Spring Boot, MySQL, JWT, CORS, and Rate Limiting config
│   │       ├── static/
│   │       └── templates/
│   └── test/
│       ├── java/com/cinema/booking/
│       │   └── controller/       # AuthControllerTest, MovieControllerTest integration tests
│       └── resources/            # Test-specific properties
├── Dockerfile                    # Containerization configuration
├── docker-compose.yml            # Local container orchestration (App, MySQL 8, phpMyAdmin)
├── pom.xml                       # Maven dependencies & build plugins
├── mvnw / mvnw.cmd               # Cross-platform Maven wrappers
├── README.md                     # System documentation & setup guide
├── agent_guide.md                # Agent handbook & architectural constraints
├── APIEndpoint.md                # REST API endpoints reference & role matrix
├── Structure.md                  # Project layout description
├── CinemaSystemstructure.md      # Domain architectural diagram
└── SystemFlow.md                 # Booking & system workflow diagrams
```

---

## 2. Layer Responsibilities

| Package / Layer | Primary Responsibility |
|---|---|
| **`config`** | Spring Security filter chain, Role Hierarchy (`ROLE_ADMIN > ROLE_STAFF > ROLE_USER`), CORS policies, Swagger/OpenAPI specs, DatabaseSeeder, and BCrypt(12) password encoder. |
| **`controller`** | HTTP request routing, input validation (`@Valid`), role access enforcement, HTTP status codes (`200`, `201`, `204`, `400`, `401`, `403`, `404`, `409`, `429`). |
| **`dto`** | Feature-grouped input and output contracts separating database entities from client APIs. |
| **`entity`** | Hibernate/JPA object relational mapping to database tables (16 relational entities). |
| **`mapper`** | Pure scalar property conversion between DTOs and Entities without database dependencies. |
| **`repository`** | Data access layer extending `JpaRepository` with standard CRUD, uniqueness checks, and query derivation. |
| **`security`** | JWT generation/parsing, Bearer token filtering, `UserDetailsService`, and thread-safe sliding-window Rate Limiting (`security/ratelimit`). |
| **`service`** | Business rules, BCrypt password hashing, foreign key resolution, transactional management, and entity orchestration. |
| **`util`** | Cross-cutting helper functions, constant definitions (`AppConstants`), and SecurityContext utilities (`SecurityUtil`). |
| **`exception`** | Centralized error formatting via `@RestControllerAdvice` and domain exceptions (`RateLimitExceededException`, `ResourceNotFoundException`, `UserAlreadyExistsException`). |
