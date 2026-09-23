package com.cinema.booking.service;

import com.cinema.booking.dto.bookings.BookingSeatRequestDto;
import com.cinema.booking.dto.bookings.BookingRequestDto;
import com.cinema.booking.dto.bookings.BookingResponseDto;
import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.Location;
import com.cinema.booking.entity.Movie;
import com.cinema.booking.entity.Screen;
import com.cinema.booking.entity.Seat;
import com.cinema.booking.entity.Show;
import com.cinema.booking.entity.Theater;
import com.cinema.booking.entity.User;
import com.cinema.booking.enums.BookingStatus;
import com.cinema.booking.enums.Role;
import com.cinema.booking.exception.SeatUnavailableException;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.repository.BookingSeatRepository;
import com.cinema.booking.repository.LocationRepository;
import com.cinema.booking.repository.MovieRepository;
import com.cinema.booking.repository.ScreenRepository;
import com.cinema.booking.repository.SeatRepository;
import com.cinema.booking.repository.ShowRepository;
import com.cinema.booking.repository.TheaterRepository;
import com.cinema.booking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class BookingSeatConcurrencyIntegrationTest {

    @Autowired private BookingSeatService bookingSeatService;
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingSeatRepository bookingSeatRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private TheaterRepository theaterRepository;
    @Autowired private ScreenRepository screenRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private ShowRepository showRepository;
    @Autowired private SeatRepository seatRepository;

    @Test
    void onlyOneConcurrentCustomerCanHoldTheSameSeatForTheSameShow() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> attemptHold(
                    fixture.firstCustomer(), fixture.firstBooking(), fixture.seat(), ready, start));
            Future<Boolean> second = executor.submit(() -> attemptHold(
                    fixture.secondCustomer(), fixture.secondBooking(), fixture.seat(), ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both clients should be ready before the race starts");
            start.countDown();

            int successfulHolds = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, successfulHolds);
            long activeHolds = bookingSeatRepository.findAll().stream()
                    .filter(bookingSeat -> fixture.show().getId().equals(bookingSeat.getShowId()))
                    .filter(bookingSeat -> fixture.seat().getId().equals(bookingSeat.getSeat().getId()))
                    .filter(bookingSeat -> "PENDING".equals(bookingSeat.getStatus()))
                    .count();
            assertEquals(1, activeHolds);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void repeatedBookingRequestWithTheSameIdempotencyKeyReturnsTheOriginalBooking() {
        Fixture fixture = createFixture();
        User customer = fixture.firstCustomer();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                customer.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        try {
            BookingRequestDto request = new BookingRequestDto();
            request.setCustomerId(customer.getId());
            request.setShowId(fixture.show().getId());

            BookingResponseDto first = bookingService.create(request, "seat-race-idempotency-key");
            BookingResponseDto retry = bookingService.create(request, "seat-race-idempotency-key");

            assertEquals(first.getId(), retry.getId());
            assertEquals(1, bookingRepository.findByCustomerIdAndIdempotencyKey(
                    customer.getId(), "seat-race-idempotency-key").stream().count());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean attemptHold(
            User customer,
            Booking booking,
            Seat seat,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                customer.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent booking test");
            }
            BookingSeatRequestDto request = new BookingSeatRequestDto();
            request.setBookingId(booking.getId());
            request.setSeatId(seat.getId());
            bookingSeatService.create(request);
            return true;
        } catch (SeatUnavailableException ignored) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Fixture createFixture() {
        String suffix = String.valueOf(System.nanoTime());
        User firstCustomer = saveUser("seat-race-a-" + suffix);
        User secondCustomer = saveUser("seat-race-b-" + suffix);

        Location location = new Location();
        location.setName("Seat Race Location " + suffix);
        location = locationRepository.save(location);

        Theater theater = new Theater();
        theater.setName("Seat Race Theater " + suffix);
        theater.setStatus("ACTIVE");
        theater.setLocation(location);
        theater = theaterRepository.save(theater);

        Screen screen = new Screen();
        screen.setName("Seat Race Screen " + suffix);
        screen.setStatus("ACTIVE");
        screen.setTotalSeats(1);
        screen.setTheater(theater);
        screen = screenRepository.save(screen);

        Movie movie = new Movie();
        movie.setTitle("Seat Race Movie " + suffix);
        movie.setDurationMinutes(120);
        movie.setStatus("ACTIVE");
        movie = movieRepository.save(movie);

        Show show = new Show();
        show.setMovie(movie);
        show.setScreen(screen);
        show.setStartTime(LocalDateTime.now().plusHours(2));
        show.setEndTime(LocalDateTime.now().plusHours(4));
        show.setTicketPrice(new BigDecimal("12.50"));
        show.setStatus("ACTIVE");
        show = showRepository.save(show);

        Seat seat = new Seat();
        seat.setScreen(screen);
        seat.setRowName("A");
        seat.setSeatNumber("6");
        seat.setSeatType("STANDARD");
        seat.setStatus("AVAILABLE");
        seat.setPrice(new BigDecimal("12.50"));
        seat = seatRepository.save(seat);

        return new Fixture(
                firstCustomer,
                secondCustomer,
                savePendingBooking(firstCustomer, show, suffix + "-a"),
                savePendingBooking(secondCustomer, show, suffix + "-b"),
                show,
                seat
        );
    }

    private User saveUser(String username) {
        User user = new User();
        user.setName(username);
        user.setUsername(username);
        user.setEmail(username + "@cinema.test");
        user.setPassword("password");
        user.setRole(Role.USER);
        user.setStatus("ACTIVE");
        return userRepository.save(user);
    }

    private Booking savePendingBooking(User customer, Show show, String suffix) {
        Booking booking = new Booking();
        booking.setBookedAt(LocalDateTime.now());
        booking.setExpiresAt(show.getStartTime().minusMinutes(5));
        booking.setBookingCode("SEAT-RACE-" + suffix);
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(BigDecimal.ZERO);
        booking.setCustomer(customer);
        booking.setShow(show);
        return bookingRepository.save(booking);
    }

    private record Fixture(
            User firstCustomer,
            User secondCustomer,
            Booking firstBooking,
            Booking secondBooking,
            Show show,
            Seat seat
    ) {
    }
}
