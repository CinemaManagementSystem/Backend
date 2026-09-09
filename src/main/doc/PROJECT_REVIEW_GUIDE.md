# 📋 Project Review Guide — Cinema Booking System Backend

This guide helps **any developer, reviewer, or new team member** understand, run, and review this project quickly.

---

## 1. What Is This Project?

A production-style REST API backend for a **Cinema Booking Platform**: users browse movies, pick showtimes and seats, order food/drinks, pay by wallet or gateway, and receive a QR ticket code.

| Concern | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.2 |
| Security | Spring Security 6 + JWT (jjwt 0.11.5), BCrypt(12) |
| Database | MySQL 8 (prod) / H2 in-memory (tests) |
| ORM | Spring Data JPA (Hibernate 6) |
| API Docs | springdoc-openapi 2.5.0 (Swagger UI) |
| Build | Maven (`mvnw` wrapper included) |
| Containers | Dockerfile + docker-compose.yml |

**Domain size:** 16 tables + Auth module → 17 controllers, 17 services, 17 repositories, 17 mappers, 34+ DTOs.

---

## 2. Run It Locally (Reviewer Setup)

### Option A: Docker Compose (easiest)

```bash
# 1. Copy env template
Copy-Item .env.example .env     # Windows PowerShell
# cp .env.example .env          # Linux/macOS

# 2. Start everything (App + MySQL 8 + phpMyAdmin)
docker compose up -d
```

| Service | URL | Notes |
|---|---|---|
| Swagger UI | http://localhost:8081/swagger-ui/index.html | Test all APIs here |
| phpMyAdmin | http://localhost:8083 | User `cinema_user` / Pass `cinema_pass` |
| MySQL | localhost:3307 | DB `cinema_db` |

### Option B: Maven + local MySQL

```bash
.\mvnw.cmd spring-boot:run       # Windows
./mvnw spring-boot:run           # Linux/macOS
```

### Seeded accounts & master data (auto-created at startup)

| Role | Username | Password | Notes |
|---|---|---|---|
| ADMIN | `admin` | `Admin123` | System administrator with default wallet balance |
| STAFF | `staff` | `Staff123` | Theater manager with default wallet balance |
| USER | `user` | `User123` | Customer with default wallet balance ($100 USD) |

*Also seeds foundational master records: Locations, Theaters, Screens, Seats, Movie Categories, Movies, Product Categories, and Products.*

Login via `POST /api/auth/login`, copy the `accessToken`, click **Authorize** in Swagger UI, paste it.

---

## 3. Suggested Code Review Path (Reading Order)

Review in this order — it follows the request lifecycle:

```
1. CinemaBookingSystemApplication.java     → entry point
2. config/SecurityConfig.java              → security filter chain, roles, CORS
3. controller/auth/AuthController.java     → login/register endpoints
4. security/JwtService.java                → token generation & validation
5. security/JwtAuthenticationFilter.java   → Bearer token pipeline
6. security/ratelimit/                     → sliding-window IP rate limiter
7. entity/User.java                        → JPA mapping style
8. dto/auth/                               → flat DTO convention (FKs as Long IDs)
9. mapper/                                 → pure scalar copying, no repo access
10. service/impl/AuthServiceImpl.java      → business logic & FK resolution
11. repository/                            → JpaRepository interfaces
12. exception/GlobalExceptionHandler.java  → centralized error formatting
13. config/DatabaseSeeder.java             → seed data on startup
14. src/test/java/.../controller/          → MockMvc integration tests
```

**One feature = one full vertical slice.** After understanding Auth, any other module (Movie, Booking, etc.) follows the identical pattern:

```
Controller → Mapper → Service → Repository → MySQL
```

---

## 4. Architecture Rules (What Reviewers Should Check)

The codebase follows strict conventions — verify them when reviewing PRs:

- ✅ **Layering:** Controller never touches Repository directly; Mapper never touches Repository.
- ✅ **DI:** Constructor injection only (`@RequiredArgsConstructor` + `private final`). No field `@Autowired`.
- ✅ **DTOs are flat:** FKs exposed as `Long` IDs, never nested objects.
- ✅ **FK resolution lives in Services:** e.g., `BookingServiceImpl` resolves customer/show from IDs.
- ✅ **Null-safe response mapping:** `entity.getX() != null ? entity.getX().getId() : null`.
- ✅ **Errors:** throw `ResourceNotFoundException`; `GlobalExceptionHandler` formats all error JSON. No stack traces leak to clients.
- ✅ **Passwords:** always BCrypt-hashed; raw passwords never stored or returned.
- ✅ **Validation:** `@Valid` on request bodies with Jakarta annotations (`@NotBlank`, `@Email`, ...).
- ✅ **Roles:** `ADMIN > STAFF > USER` hierarchy enforced via method security.

---

## 5. Testing

Tests use **H2 in-memory DB (MySQL mode)** — no external database needed.

```bash
.\mvnw.cmd test        # Windows
./mvnw test            # Linux/macOS
```

Current status: **29 tests, all passing**

| Test class | Tests | Covers |
|---|---|---|
| `AuthControllerTest` | 19 | register, login, validation, security |
| `MovieControllerTest` | 10 | CRUD + role-based access |

Reports are generated at `target/surefire-reports/`.

---

## 6. CI/CD Status

| Stage | Status | Where |
|---|---|---|
| CI workflow | ✅ Implemented | `.github/workflows/ci.yml` |
| CD (deploy) | ⏳ Planned only | `src/main/doc/cicd.md` |

**CI pipeline runs automatically on every push / PR to `main` or `dev`:**

```
Checkout → Setup Java 21 (Temurin) → Maven cache
       → chmod +x mvnw → clean compile → mvn test
       → upload surefire reports as artifact
```

To see results: open the repo on GitHub → **Actions** tab → select the latest workflow run.

Branch strategy:
- `main` → production
- `dev` → development

---

## 7. Documentation Index

| Document | Read it for |
|---|---|
| [README.md](README.md) | Setup, features, security overview |
| [APIEndpoint.md](APIEndpoint.md) | Every endpoint + role permissions matrix |
| [structure.md](structure.md) | Directory tree & layer responsibilities |
| [SystemFlow.md](SystemFlow.md) | Sequence diagrams (customer/staff/admin flows) |
| [cinema_project_flow.md](cinema_project_flow.md) | DB schema, relationships, transaction flow |
| [agent_guide.md](agent_guide.md) | Coding standards & hard rules |
| [src/main/doc/A_practical_workflow.md](src/main/doc/A_practical_workflow.md) | Practical Git branch workflow (main, dev, features) with CI/Qodana |
| [src/main/doc/StoreImageFlow.md](src/main/doc/StoreImageFlow.md) | Product image storage flow (Cloudinary + DB) |
| [src/main/doc/MovieCRUDflow.md](src/main/doc/MovieCRUDflow.md) | Movie poster upload flow (Cloudinary + DB) |
| [src/main/doc/cicd.md](src/main/doc/cicd.md) | CI/CD design plan |


---

## 8. Quick Review Checklist

Use this when reviewing a pull request:

- [ ] Does it follow the layered pattern (no layer skipping)?
- [ ] Are DTOs flat? No entities leaked into responses?
- [ ] FK resolution done in Service, not Mapper?
- [ ] New endpoint has correct role annotation?
- [ ] Request validated with `@Valid` + Jakarta annotations?
- [ ] Errors thrown as custom exceptions (not raw `RuntimeException`)?
- [ ] No secrets/passwords logged or returned?
- [ ] Tests added/updated and passing locally (`.\mvnw.cmd test`)?
- [ ] CI green on GitHub Actions?

---

## 9. Known Limitations (Good First Contributions)

- Seat locking is not atomic yet — concurrent bookings for the same seat can race (DB unique constraint catches it, but UX could improve with pessimistic locking).
- Payment gateway is simulated — no real Stripe/ABA integration.
- No pagination on list endpoints yet.
- CD/deploy stage not implemented (see `cicd.md` plan).

---

*Last updated: 2026-08-21 · Java 21 · Spring Boot 3.3.2*
