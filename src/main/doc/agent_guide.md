# Agent Guide — Cinema Booking System

This guide gives an AI agent and developers everything needed to safely navigate, extend, and debug this codebase.

## 1. Project at a Glance

- **What it is:** A REST backend for a cinema booking platform (users, authentication, movies, theaters, screens, seats, shows, bookings, concessions, orders, payments, wallets).
- **Stack:** Java 21, Spring Boot 3.3.2, Spring Security 6 (JWT Bearer Token), Spring Data JPA, Lombok, MySQL (`mysql-connector-j`), `spring-boot-starter-validation`, **springdoc-openapi 2.5.0 (Swagger UI)**.
- **Build tool:** Maven (`pom.xml`, groupId `com.cinema`, artifactId `cinema-booking-system`, wrappers `mvnw` / `mvnw.cmd`).
- **Architecture:** Strictly layered — **Controller → Mapper → Service → Repository → Database**.
- **Codebase size:** 16 domain tables + Authentication module × full layer stack = 17 controllers, 34+ DTOs, 17 mappers, 17 repositories, 17 services.

---

## 2. Package Layout (Base: `src/main/java/com/cinema/booking`)

| Layer | Package | Responsibility |
|---|---|---|
| Application | `CinemaBookingSystemApplication.java` | Standard `@SpringBootApplication` entry point |
| Config | `config/` | `SecurityConfig`, `OpenApiConfig`, and Spring Web beans |
| Controller | `controller/`, `controller/auth/` | REST endpoints, HTTP status codes, `@Valid` validation triggers |
| Service (interface) | `service/` | Business contract definitions |
| Service (impl) | `service/impl/` | Business logic, FK resolution, and transaction handling |
| Mapper | `mapper/` | Pure scalar field copying between DTO ⇄ Entity |
| Repository | `repository/` | `JpaRepository<Entity, Long>` — CRUD + paging |
| Entity | `entity/` | JPA `@Entity` classes mapped 1:1 to SQL tables/columns |
| DTO request | `dto/request/` | `XRequestDto` — API input (flat, FKs as `Long` IDs) with Jakarta Validation |
| DTO response | `dto/response/` | `XResponseDto` — API output (flat, FKs as `Long` IDs) |
| Security | `security/`, `security/ratelimit/` | `JwtService`, `JwtAuthenticationFilter`, `CustomUserDetailsService`, `RateLimiterService`, `RateLimitingFilter` |
| Util | `util/` | `AppConstants`, `SecurityUtil` helper methods |
| Exception | `exception/` | `GlobalExceptionHandler`, `RateLimitExceededException`, `ResourceNotFoundException`, and custom API exceptions |

---

## 3. The Domain Tables & Build Order

Build order follows FK dependencies:

1. User (`users`) — roles: `USER`, `STAFF`, `ADMIN`
2. Location (`locations`)
3. Category (`categories`)
4. Product (`products`)
5. Theater (`theaters`)
6. Movie (`movies`)
7. Screen (`screens`)
8. Wallet (`wallets`)
9. Seat (`seats`) — unique on `screen_id + seat_number` in DB
10. Show (`shows`)
11. Booking (`bookings`) — unique `booking_code`
12. BookingSeat (`booking_seats`) — unique on `booking_id + seat_id` in DB
13. Order (`orders`) — unique `order_number`
14. OrderItem (`order_items`)
15. Payment (`payments`)
16. WalletTransaction (`wallet_transactions`)

Naming: table names are **pluralized snake_case**; entity class names are singular PascalCase.

---

## 4. Request Flow & Security Pipeline (Canonical Example)

`POST /api/bookings`:

1. **Rate Limiting:** `RateLimitingFilter` verifies client IP request frequency under configured threshold (10 req/min for auth, 100 req/min for general). Returns `429 Too Many Requests` if exceeded.
2. **JWT Authentication:** `JwtAuthenticationFilter` validates `Bearer <token>`, resolves claims and user authorities, setting `SecurityContextHolder`.
3. **Role Authorization:** Spring Security evaluates `@EnableMethodSecurity` and `RoleHierarchy` (`ADMIN > STAFF > USER`).
4. **Input Validation:** `BookingController.create` validates `BookingRequestDto` (`@Valid @RequestBody`).
5. **Business Logic & FK Resolution:** `BookingServiceImpl.create` calls `BookingMapper.toEntity(dto)` &rarr; scalar fields only; resolves `customer` from `userRepository` and `show` from `showRepository`.
6. **Data Persistence:** `bookingRepository.save(booking)` persists to MySQL.
7. **Response Mapping:** `BookingMapper.toResponseDto(booking)` flattens FK entities back to IDs.
8. **HTTP Response:** Controller returns `201 Created` with the DTO as JSON.

---

## 5. Hard Rules & Conventions (FOLLOW THESE)

- **One `XController` per entity.** Base path `/api/{plural-kebab-or-snake}`.
- **Dependency Injection:** Constructor injection via Lombok `@RequiredArgsConstructor` + `private final` fields. **Never** use field injection or `@Autowired` on fields.
- **Lombok everywhere:** `@Data @NoArgsConstructor @AllArgsConstructor` on entities and DTOs. `@RequiredArgsConstructor` on controllers/services.
- **Entities:** `@Entity @Table(name = "pluralized_name")`, `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` on `Long id`, `@Column(name = "...", nullable = ...)`.
- **Password Security:** Always hash passwords with `PasswordEncoder` (BCrypt 12). Never store or return raw passwords.
- **Mappers are pure.** No repository access. Only copy scalar fields. FK resolution lives in the Service layer. `toResponseDto` must null-check relations (`entity.getX() != null ? entity.getX().getId() : null`).
- **DTOs are flat.** FKs are exposed as `Long` IDs, never nested objects.
- **Missing records:** Throw `new ResourceNotFoundException("<EntityName>", id)` — `GlobalExceptionHandler` converts this into a standardized 404 response.
- **Security & Error Sanitization:** Never leak internal stack traces or database errors in API responses. Use `GlobalExceptionHandler` to format standardized `ErrorResponse` objects.

---

## 6. Build & Test Commands

- **Java Version:** JDK 21
- **Compile:** `.\mvnw.cmd test-compile` (Windows) or `./mvnw test-compile` (Linux/macOS)
- **Run Tests:** `.\mvnw.cmd test`
- **Run Application:** `.\mvnw.cmd spring-boot:run`
- **Swagger UI:** `http://localhost:8081/swagger-ui/index.html` (or port configured in `application.properties`)