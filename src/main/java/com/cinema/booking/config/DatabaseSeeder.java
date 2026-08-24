package com.cinema.booking.config;

import com.cinema.booking.entity.*;
import com.cinema.booking.enums.Role;
import com.cinema.booking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final TheaterRepository theaterRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final MovieCategoryRepository movieCategoryRepository;
    private final MovieRepository movieRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            log.info("Starting master database seeding...");

            // 1. Users
        User admin = seedUserIfNotExists("admin", "admin@cinema.com", "Admin User", "Admin123", Role.ADMIN);
        User staff = seedUserIfNotExists("staff", "staff@cinema.com", "Staff Member", "Staff123", Role.STAFF);
        User customer = seedUserIfNotExists("user", "user@cinema.com", "Regular Customer", "User123", Role.USER);

        // 2. Locations
        Location phnomPenh = seedLocationIfNotExists(
                "Phnom Penh Central",
                "#123 St 214, Daun Penh, Phnom Penh",
                "Phnom Penh",
                "https://maps.google.com/?q=11.5564,104.9282",
                BigDecimal.valueOf(11.5564),
                BigDecimal.valueOf(104.9282)
        );

        Location senSok = seedLocationIfNotExists(
                "Aeon Sen Sok City",
                "St 1003, Bayab Village, Phnom Penh",
                "Phnom Penh",
                "https://maps.google.com/?q=11.6025,104.8827",
                BigDecimal.valueOf(11.6025),
                BigDecimal.valueOf(104.8827)
        );

        Location siemReap = seedLocationIfNotExists(
                "Siem Reap Riverside",
                "Pokambor Ave, Krong Siem Reap",
                "Siem Reap",
                "https://maps.google.com/?q=13.3633,103.8564",
                BigDecimal.valueOf(13.3633),
                BigDecimal.valueOf(103.8564)
        );

        // 4. Theaters (linked to Location & Manager User)
        Theater legendCinema = seedTheaterIfNotExists(
                "Legend Cinema Central",
                "Level 3, Central Mall, Daun Penh",
                "023-888-999",
                "OPEN",
                phnomPenh,
                staff
        );

        Theater majorCineplex = seedTheaterIfNotExists(
                "Major Cineplex Sen Sok",
                "2nd Floor, Aeon Mall Sen Sok City",
                "023-777-666",
                "OPEN",
                senSok,
                staff
        );

        Theater primeSiemReap = seedTheaterIfNotExists(
                "Prime Cineplex Siem Reap",
                "Riverside Walkway, Siem Reap",
                "063-555-444",
                "OPEN",
                siemReap,
                staff
        );

        // 5. Screens (linked to Theater)
        Screen screen1 = seedScreenIfNotExists("Hall 1 - IMAX", "IMAX", "ACTIVE", 40, legendCinema);
        Screen screen2 = seedScreenIfNotExists("Hall 2 - VIP", "VIP", "ACTIVE", 30, legendCinema);
        Screen screen3 = seedScreenIfNotExists("Hall 1 - Premium", "STANDARD", "ACTIVE", 40, majorCineplex);
        Screen screen4 = seedScreenIfNotExists("Hall 1 - Deluxe", "STANDARD", "ACTIVE", 30, primeSiemReap);

        // 6. Seats (linked to Screen)
        seedSeatsForScreen(screen1, 4, 10);
        seedSeatsForScreen(screen2, 3, 10);
        seedSeatsForScreen(screen3, 4, 10);
        seedSeatsForScreen(screen4, 3, 10);

        // 7. Movie Categories
        MovieCategory sciFi = seedMovieCategoryIfNotExists("Sci-Fi & Fantasy", "Mind-bending futuristic and fantasy cinema", true);
        MovieCategory action = seedMovieCategoryIfNotExists("Action & Adventure", "High octane and thrilling blockbuster movies", true);
        MovieCategory animation = seedMovieCategoryIfNotExists("Animation", "Family friendly animated adventures", true);
        MovieCategory horror = seedMovieCategoryIfNotExists("Horror & Thriller", "Suspenseful and terrifying experiences", true);
        MovieCategory drama = seedMovieCategoryIfNotExists("Drama & Romance", "Compelling stories of life, relationships, and drama", true);

        // 8. Movies (linked to MovieCategory)
        seedMovieIfNotExists(
                "Inception",
                "A thief who steals corporate secrets through dream-sharing technology is given the inverse task of planting an idea.",
                148,
                "Sci-Fi, Thriller",
                "English",
                "https://images.unsplash.com/photo-1536440136628-849c177e76a1?auto=format&fit=crop&w=600&q=80",
                LocalDate.of(2010, 7, 16),
                "NOW_SHOWING",
                sciFi
        );

        seedMovieIfNotExists(
                "Interstellar",
                "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival.",
                169,
                "Sci-Fi, Drama",
                "English",
                "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?auto=format&fit=crop&w=600&q=80",
                LocalDate.of(2014, 11, 7),
                "NOW_SHOWING",
                sciFi
        );

        seedMovieIfNotExists(
                "Avengers: Endgame",
                "After devastating events, the Avengers assemble once more in order to reverse Thanos' actions.",
                181,
                "Action, Sci-Fi",
                "English",
                "https://images.unsplash.com/photo-1574267432553-4b4628081c31?auto=format&fit=crop&w=600&q=80",
                LocalDate.of(2019, 4, 26),
                "NOW_SHOWING",
                action
        );

        seedMovieIfNotExists(
                "Neon Nights",
                "A cybersecurity hacker gets trapped in a virtual neon underworld and must hack his way out through digital defense systems.",
                124,
                "Sci-Fi, Thriller",
                "English",
                "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=600&q=80",
                LocalDate.of(2026, 9, 1),
                "COMING_SOON",
                sciFi
        );

        // 9. Product Categories
        ProductCategory popcornCat = seedProductCategoryIfNotExists("Popcorn", "Freshly popped gourmet popcorn in sweet, salted, or cheese flavors", true);
        ProductCategory beveragesCat = seedProductCategoryIfNotExists("Beverages", "Refreshing soft drinks, mineral water, and juices", true);
        ProductCategory snacksCat = seedProductCategoryIfNotExists("Snacks", "Delicious movie snacks including nachos, hot dogs, and candy", true);
        ProductCategory combosCat = seedProductCategoryIfNotExists("Combos", "Value combos combining popcorn and beverages for the best experience", true);

        // 10. Products (linked to ProductCategory)
        seedProductIfNotExists("Caramel Popcorn (L)", BigDecimal.valueOf(4.50), "https://images.unsplash.com/photo-1585647347483-22b66260dfff?auto=format&fit=crop&w=400&q=80", true, 100, popcornCat);
        seedProductIfNotExists("Salted Popcorn (M)", BigDecimal.valueOf(3.50), "https://images.unsplash.com/photo-1585647347483-22b66260dfff?auto=format&fit=crop&w=400&q=80", true, 100, popcornCat);
        seedProductIfNotExists("Coca-Cola 500ml", BigDecimal.valueOf(2.00), "https://images.unsplash.com/photo-1622483767028-3f66f32aef97?auto=format&fit=crop&w=400&q=80", true, 200, beveragesCat);
        seedProductIfNotExists("Mineral Water 500ml", BigDecimal.valueOf(1.00), "https://images.unsplash.com/photo-1560023907-5f339617ea30?auto=format&fit=crop&w=400&q=80", true, 250, beveragesCat);
        seedProductIfNotExists("Crispy Nachos & Cheese", BigDecimal.valueOf(4.00), "https://images.unsplash.com/photo-1513456852971-30c0b8199d4d?auto=format&fit=crop&w=400&q=80", true, 80, snacksCat);
        seedProductIfNotExists("Classic Hot Dog", BigDecimal.valueOf(3.50), "https://images.unsplash.com/photo-1619740455993-9e612b1af08a?auto=format&fit=crop&w=400&q=80", true, 60, snacksCat);
        seedProductIfNotExists("Movie Night Combo (Popcorn + 2 Drinks)", BigDecimal.valueOf(7.50), "https://images.unsplash.com/photo-1572177191856-3cde618dee1f?auto=format&fit=crop&w=400&q=80", true, 50, combosCat);

        log.info("Master database seeding completed successfully.");
        } catch (Throwable t) {
            System.err.println("Database Seeder failed with exception: " + t.getMessage());
            t.printStackTrace();
            throw t;
        }
    }

    private User seedUserIfNotExists(String username, String email, String name, String rawPassword, Role role) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setName(name);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setRole(role);
            user.setStatus("ACTIVE");
            User saved = userRepository.save(user);
            log.info("Default {} user created: username='{}', email='{}'", role, username, email);
            return saved;
        });
    }


    private Location seedLocationIfNotExists(String name, String address, String city, String mapsUrl, BigDecimal lat, BigDecimal lng) {
        return locationRepository.findByName(name).orElseGet(() -> {
            Location loc = new Location();
            loc.setName(name);
            loc.setAddress(address);
            loc.setCity(city);
            loc.setGoogleMapsUrl(mapsUrl);
            loc.setLatitude(lat);
            loc.setLongitude(lng);
            Location saved = locationRepository.save(loc);
            log.info("Default location created: name='{}'", name);
            return saved;
        });
    }

    private Theater seedTheaterIfNotExists(String name, String address, String phone, String status, Location location, User manager) {
        return theaterRepository.findByName(name).orElseGet(() -> {
            Theater theater = new Theater();
            theater.setName(name);
            theater.setAddress(address);
            theater.setPhone(phone);
            theater.setStatus(status);
            theater.setLocation(location);
            theater.setManager(manager);
            Theater saved = theaterRepository.save(theater);
            log.info("Default theater created: name='{}' in location '{}'", name, location.getName());
            return saved;
        });
    }

    private Screen seedScreenIfNotExists(String name, String screenType, String status, Integer totalSeats, Theater theater) {
        return screenRepository.findByNameAndTheater(name, theater).orElseGet(() -> {
            Screen screen = new Screen();
            screen.setName(name);
            screen.setScreenType(screenType);
            screen.setStatus(status);
            screen.setTotalSeats(totalSeats);
            screen.setTheater(theater);
            Screen saved = screenRepository.save(screen);
            log.info("Default screen created: name='{}' for theater '{}'", name, theater.getName());
            return saved;
        });
    }

    private void seedSeatsForScreen(Screen screen, int totalRows, int seatsPerRow) {
        if (seatRepository.countByScreen(screen) > 0) {
            return;
        }

        List<Seat> seats = new ArrayList<>();
        char startRow = 'A';

        for (int r = 0; r < totalRows; r++) {
            String rowName = String.valueOf((char) (startRow + r));
            String seatType = (r == totalRows - 1) ? "COUPLE" : (r >= totalRows - 2 ? "VIP" : "STANDARD");
            BigDecimal price = (seatType.equals("COUPLE")) ? BigDecimal.valueOf(14.00)
                    : (seatType.equals("VIP") ? BigDecimal.valueOf(9.00) : BigDecimal.valueOf(6.00));

            for (int s = 1; s <= seatsPerRow; s++) {
                String seatNumber = rowName + s;
                Seat seat = new Seat();
                seat.setScreen(screen);
                seat.setRowName(rowName);
                seat.setSeatNumber(seatNumber);
                seat.setSeatType(seatType);
                seat.setStatus("AVAILABLE");
                seat.setPrice(price);
                seats.add(seat);
            }
        }

        seatRepository.saveAll(seats);
        log.info("Seeded {} seats for screen '{}'", seats.size(), screen.getName());
    }

    private MovieCategory seedMovieCategoryIfNotExists(String name, String description, boolean isActive) {
        return movieCategoryRepository.findByName(name).orElseGet(() -> {
            MovieCategory cat = new MovieCategory();
            cat.setName(name);
            cat.setDescription(description);
            cat.setIsActive(isActive);
            MovieCategory saved = movieCategoryRepository.save(cat);
            log.info("Default movie category created: name='{}'", name);
            return saved;
        });
    }

    private void seedMovieIfNotExists(String title, String description, Integer durationMinutes, String genre,
                                     String language, String posterUrl, LocalDate releaseDate, String status,
                                     MovieCategory category) {
        if (!movieRepository.existsByTitle(title)) {
            Movie movie = new Movie();
            movie.setTitle(title);
            movie.setDescription(description);
            movie.setDurationMinutes(durationMinutes);
            movie.setGenre(genre);
            movie.setLanguage(language);
            movie.setPosterUrl(posterUrl);
            movie.setReleaseDate(releaseDate);
            movie.setStatus(status);
            movie.setCategory(category);
            movieRepository.save(movie);
            log.info("Default movie created: title='{}'", title);
        }
    }

    private ProductCategory seedProductCategoryIfNotExists(String name, String description, boolean isActive) {
        return productCategoryRepository.findByName(name).orElseGet(() -> {
            ProductCategory category = new ProductCategory();
            category.setName(name);
            category.setDescription(description);
            category.setIsActive(isActive);
            ProductCategory saved = productCategoryRepository.save(category);
            log.info("Default product category created: name='{}'", name);
            return saved;
        });
    }

    private void seedProductIfNotExists(String name, BigDecimal price, String imageUrl, boolean isAvailable,
                                       int stockQuantity, ProductCategory category) {
        if (!productRepository.existsByName(name)) {
            Product product = new Product();
            product.setName(name);
            product.setPrice(price);
            product.setImageUrl(imageUrl);
            product.setIsAvailable(isAvailable);
            product.setStockQuantity(stockQuantity);
            product.setProductCategory(category);
            productRepository.save(product);
            log.info("Default product created: name='{}'", name);
        }
    }
}
