# Cinema Movie Booking System — Database & System Flow

## 1. Core Database Tables

```text
movie_booking_system
│
├── users
├── locations
├── theaters
├── screens
├── seats
├── movies
├── shows
├── bookings
├── booking_seats
├── product_categories
├── products
├── orders
├── order_items
├── payments
├── wallets
└── wallet_transactions
```

## 2. Main Relationships

### Cinema structure

```text
locations
    ↓
theaters
    ↓
screens
    ↓
seats
```

### Movie and show structure

```text
movies
    ↓
shows
    ↓
screens
```

### Booking structure

```text
users
  ↓
bookings
  ↓
booking_seats
  ↓
seats
```

### Food and beverage structure

```text
product_categories
        ↓
     products
        ↓
   order_items
        ↓
      orders
```

Products can include popcorn, Coca-Cola, Pepsi, water, nachos, hot dogs, snacks, and combos.

## 3. Order Design

An order can be connected to a movie booking:

```text
Customer
   ↓
Booking
   ├── Seats
   └── Order
         ├── Popcorn
         └── Coke
```

In this case:

```text
orders.booking_id = booking.id
```

A customer can also order food without a movie booking:

```text
Customer
   ↓
Order
   ├── Popcorn
   └── Coke
```

In this case:

```text
orders.booking_id = NULL
```

Therefore, `booking_id` in `orders` should be nullable if food-only orders are supported.

## 4. Customer Booking Flow

```text
Customer
   ↓
Open Cinema Website
   ↓
Browse Movies
   ↓
Select Movie
   ↓
Select Date
   ↓
Select Show
   ↓
View Seat Layout
   ↓
Select Seats
   ↓
Login / Register
   ↓
Add Food & Drinks?
   ├── No ─────────────────┐
   │                       │
   └── Yes                 │
        ↓                  │
   Select Products         │
        ↓                  │
   Add to Order            │
        └────────┬─────────┘
                 ↓
           Order Summary
                 ↓
              Payment
                 ↓
        ┌────────┴────────┐
        │                 │
     Success            Failed
        │                 │
        ↓                 ↓
 Booking Confirmed    Retry Payment
        ↓
 Booking Confirmation
```

## 5. Admin / Theater Manager Flow

```text
Login
  ↓
Dashboard
  ├── Locations
  │     └── Create / Update Location
  │
  ├── Theaters
  │     └── Create / Update Theater
  │
  ├── Screens
  │     └── Create / Update Screen
  │
  ├── Seats
  │     └── Create / Update Seats
  │
  ├── Movies
  │     └── Create / Update Movie
  │
  └── Shows
        ├── Select Movie
        ├── Select Screen
        ├── Start Time
        ├── End Time
        └── Ticket Price
```

## 6. Database Flow for a Booking

Example:

```text
Movie: Avengers
Show: 7:30 PM
Screen: Screen 2
Seats: A1, A2
Food: 1 Popcorn
Drink: 2 Coca-Cola
```

Flow:

```text
movies
   ↓
shows
   ↓
bookings
   ↓
booking_seats
   ├── A1
   └── A2

products
   ├── Popcorn
   └── Coca-Cola
        ↓
   order_items
        ↓
      orders
        ↓
     payments
```

## 7. Backend Booking Transaction

Recommended Spring Boot flow:

```text
POST /api/bookings
        ↓
Validate Customer
        ↓
Validate Show
        ↓
Check Seats
        ├── Already booked → ERROR
        ↓
Create Booking
        ↓
Create Booking Seats
        ↓
Calculate Ticket Total
        ↓
Add Food/Drink Order
        ↓
Calculate Order Total
        ↓
Calculate Grand Total
        ↓
Create Payment
        ↓
Payment Success?
   ┌────┴────┐
   │         │
  YES        NO
   │         │
   ↓         ↓
Confirm    Cancel /
Booking    Payment Failed
```

## 8. Payment and Wallet Flow

```text
                    PAYMENT
                       │
              ┌────────┴────────┐
              │                 │
           WALLET            ONLINE
              │                 │
              ↓                 ↓
           Wallet            Gateway
              │                 │
              └────────┬────────┘
                       ↓
                 Payment Result
                       │
                ┌──────┴──────┐
                │             │
             SUCCESS        FAILED
                │             │
                ↓             ↓
             Confirm         Retry
```

Wallet:

```text
Customer
   ↓
Wallet
   ├── Check Balance
   ├── Add Money
   │     ↓
   │   Payment
   │     ↓
   │   Wallet Transaction
   │
   └── Pay Booking/Order
         ↓
     Check Balance
         ↓
    ┌────┴────┐
    │         │
 Enough    Not Enough
    │         │
    ↓         ↓
 Deduct     Reject
 Balance
    ↓
Wallet Transaction
```

## 9. Full Database Relationship Flow

```text
                         ┌──────────────┐
                         │    USERS     │
                         └──────┬───────┘
                                │
                  ┌─────────────┼─────────────┐
                  │             │             │
                  ↓             ↓             ↓
             THEATERS       BOOKINGS       ORDERS
                  │             │             │
                  ↓             ↓             ↓
              SCREENS     BOOKING_SEATS   ORDER_ITEMS
                  │             │             │
                  ↓             ↓             ↓
                SEATS         SEATS       PRODUCTS
                                │             │
                                │             ↓
                                │       PRODUCT_CATEGORIES
                                │
                                ↓
                              SHOWS
                                ↑
                                │
                              MOVIES

BOOKINGS ────────────────→ PAYMENTS ←──────────── ORDERS
                                │
                                ↓
                             WALLET
                                │
                                ↓
                      WALLET_TRANSACTIONS

THEATERS ───────────────→ LOCATIONS
```

## 10. Spring Boot Architecture

```text
React Frontend
      │
      │ HTTP / JSON
      ↓
┌───────────────┐
│   Controller  │
└───────┬───────┘
        ↓
┌───────────────┐
│    Service    │
│ Business Logic│
└───────┬───────┘
        ↓
┌───────────────┐
│  Repository   │
└───────┬───────┘
        ↓
┌───────────────┐
│   Database    │
│ MySQL         │
└───────────────┘
```

Booking example:

```text
React
  ↓
POST /api/bookings
  ↓
BookingController
  ↓
BookingService
  ├── Check show
  ├── Check seats
  ├── Create booking
  ├── Create booking seats
  ├── Calculate total
  └── Create payment
  ↓
BookingRepository
BookingSeatRepository
PaymentRepository
  ↓
MySQL
```

## 11. Identifier Strategy (Active: Long Auto-Increment)

> **Note:** The active backend implementation uses **`Long` auto-increment (`GenerationType.IDENTITY`)** IDs across all 16 entities. Below is an alternative UUID design comparison for reference.

For a UUID-based design:

```java
@Entity
@Table(name = "movies")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;
    private String description;
    private Integer durationMinutes;
    private String language;
}
```

Repository:

```java
public interface MovieRepository
        extends JpaRepository<Movie, UUID> {
}
```

Service:

```java
public Movie getMovie(UUID id) {
    return movieRepository.findById(id)
            .orElseThrow(() ->
                new ResourceNotFoundException("Movie not found")
            );
}
```

Controller:

```java
@GetMapping("/{id}")
public MovieResponse getMovie(
        @PathVariable UUID id
) {
    return movieService.getMovie(id);
}
```

The ID should normally remain unchanged when updating a record.

```text
Before:
UUID: 550e8400-e29b-41d4-a716-446655440000
Title: Avatar

After:
UUID: 550e8400-e29b-41d4-a716-446655440000
Title: Avatar 2
```

The UUID identifies the record; other fields can be updated.

## 12. UUID vs Auto Increment

| Area | BIGINT | UUID |
|---|---|---|
| Java type | `Long` | `UUID` |
| Entity ID | `Long id` | `UUID id` |
| Generation | `IDENTITY` | `UUID` |
| Repository | `<Entity, Long>` | `<Entity, UUID>` |
| Controller | `@PathVariable Long` | `@PathVariable UUID` |
| Database | `BIGINT` | `UUID` / `CHAR(36)` |
| Example API ID | `1` | `550e8400-e29b-...` |

UUID is an identifier, not a security mechanism. Authentication and authorization are still required.

## 13. Recommended Core Tables

Keep these 16 tables for the first version:

```text
1.  users
2.  locations
3.  theaters
4.  screens
5.  seats
6.  movies
7.  shows
8.  bookings
9.  booking_seats
10. product_categories
11. products
12. orders
13. order_items
14. payments
15. wallets
16. wallet_transactions
```

Optional later:

```text
reviews
coupons
promotions
notifications
```

## 14. Final System Flow

```text
                    CINEMA MOVIE SYSTEM
                             │
            ┌────────────────┴────────────────┐
            │                                 │
         ADMIN /                         CUSTOMER
      THEATER MANAGER                         │
            │                                 │
            ↓                                 ↓
       Manage Movie                    Browse Movies
       Manage Theater                        ↓
       Manage Screen                    Select Movie
       Manage Seats                         ↓
       Create Show                      Select Show
            │                                ↓
            │                           Select Seats
            │                                ↓
            │                         Food & Beverage
            │                                ↓
            │                           Order Summary
            │                                ↓
            └──────────────────────────────→ Payment
                                             │
                                     ┌───────┴───────┐
                                     │               │
                                  Success          Failed
                                     │               │
                                     ↓               ↓
                               Confirmation        Retry
```

## 15. Recommended Project Scope

For the first working version, implement in this order:

```text
1. Users / Authentication
2. Locations
3. Theaters
4. Screens
5. Seats
6. Movies
7. Shows
8. Movie Booking
9. Booking Seats
10. Products / Categories
11. Food & Beverage Orders
12. Payments
13. Wallet
14. Dashboard / Reports
```

This gives the project a clear progression from cinema setup → movie scheduling → customer booking → food ordering → payment.
