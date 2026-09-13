package com.cinema.booking.service;

import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.BookingSeat;
import com.cinema.booking.entity.Location;
import com.cinema.booking.entity.Movie;
import com.cinema.booking.entity.Payment;
import com.cinema.booking.entity.PaymentTransaction;
import com.cinema.booking.entity.Screen;
import com.cinema.booking.entity.Seat;
import com.cinema.booking.entity.Show;
import com.cinema.booking.entity.Theater;
import com.cinema.booking.entity.User;
import com.cinema.booking.enums.BookingStatus;
import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.enums.Role;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.repository.BookingSeatRepository;
import com.cinema.booking.repository.LocationRepository;
import com.cinema.booking.repository.MovieRepository;
import com.cinema.booking.repository.PaymentRepository;
import com.cinema.booking.repository.PaymentTransactionRepository;
import com.cinema.booking.repository.ScreenRepository;
import com.cinema.booking.repository.SeatRepository;
import com.cinema.booking.repository.ShowRepository;
import com.cinema.booking.repository.TheaterRepository;
import com.cinema.booking.repository.UserRepository;
import com.cinema.booking.scheduler.PaymentExpirationScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class PaymentConfirmationIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentExpirationScheduler paymentExpirationScheduler;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private TheaterRepository theaterRepository;

    @Autowired
    private ScreenRepository screenRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    void successfulPaymentConfirmsPaymentBookingSeatsAndTransaction() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);

        paymentService.confirmPaymentFromSystem(fixture.payment().getId());

        assertEquals(PaymentStatus.PAID, paymentRepository.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.CONFIRMED, bookingRepository.findById(fixture.booking().getId()).orElseThrow().getStatus());
        assertEquals(List.of("CONFIRMED"), bookingSeatRepository.findByBookingId(fixture.booking().getId())
                .stream().map(BookingSeat::getStatus).toList());
        assertEquals(1, paymentTransactionRepository.findByPaymentId(fixture.payment().getId()).size());
        assertEquals(PaymentStatus.PAID, paymentTransactionRepository.findByPaymentId(fixture.payment().getId()).get(0).getStatus());
    }

    @Test
    void successfulPaymentConfirmsAllBookingSeats() {
        Fixture fixture = createFixture(2, BookingStatus.PENDING);

        paymentService.confirmPaymentFromSystem(fixture.payment().getId());

        assertEquals(2, bookingSeatRepository.findByBookingId(fixture.booking().getId()).size());
        assertEquals(List.of("CONFIRMED", "CONFIRMED"), bookingSeatRepository.findByBookingId(fixture.booking().getId())
                .stream().map(BookingSeat::getStatus).toList());
    }

    @Test
    void repeatedBakongPollingIsIdempotent() {
        Fixture fixture = createFixture(2, BookingStatus.PENDING);

        paymentExpirationScheduler.pollPendingKhqrPayments();
        paymentExpirationScheduler.pollPendingKhqrPayments();

        assertEquals(PaymentStatus.PAID, paymentRepository.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.CONFIRMED, bookingRepository.findById(fixture.booking().getId()).orElseThrow().getStatus());
        assertEquals(2, bookingSeatRepository.findByBookingId(fixture.booking().getId()).size());
        assertEquals(1, paymentTransactionRepository.findByPaymentId(fixture.payment().getId()).size());
        assertEquals(PaymentStatus.PAID, paymentTransactionRepository.findByPaymentId(fixture.payment().getId()).get(0).getStatus());
    }

    @Test
    void confirmationDoesNotCreateDuplicateTransactionForRepeatedConfirmation() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);

        paymentService.confirmPaymentFromSystem(fixture.payment().getId());
        paymentService.confirmPaymentFromSystem(fixture.payment().getId());

        List<PaymentTransaction> transactions = paymentTransactionRepository.findByPaymentId(fixture.payment().getId());
        assertEquals(1, transactions.size());
        assertEquals(fixture.payment().getTransactionId(), transactions.get(0).getReference());
        assertEquals(PaymentStatus.PAID, transactions.get(0).getStatus());
    }

    @Test
    void confirmationRollsBackWhenBookingTransitionFails() {
        Fixture fixture = createFixture(2, BookingStatus.CANCELLED);

        assertThrows(IllegalStateException.class,
                () -> paymentService.confirmPaymentFromSystem(fixture.payment().getId()));

        assertEquals(PaymentStatus.PENDING, paymentRepository.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.CANCELLED, bookingRepository.findById(fixture.booking().getId()).orElseThrow().getStatus());
        assertEquals(List.of("PENDING", "PENDING"), bookingSeatRepository.findByBookingId(fixture.booking().getId())
                .stream().map(BookingSeat::getStatus).toList());
        assertEquals(1, paymentTransactionRepository.findByPaymentId(fixture.payment().getId()).size());
        assertEquals(PaymentStatus.PENDING, paymentTransactionRepository.findByPaymentId(fixture.payment().getId()).get(0).getStatus());
    }

    private Fixture createFixture(int seatCount, BookingStatus bookingStatus) {
        String suffix = String.valueOf(System.nanoTime());

        User customer = new User();
        customer.setName("Payment Test Customer");
        customer.setUsername("payment-test-" + suffix);
        customer.setEmail("payment-test-" + suffix + "@cinema.test");
        customer.setPassword("password");
        customer.setRole(Role.USER);
        customer.setStatus("ACTIVE");
        customer = userRepository.save(customer);

        Location location = new Location();
        location.setName("Payment Test Location " + suffix);
        location = locationRepository.save(location);

        Theater theater = new Theater();
        theater.setName("Payment Test Theater " + suffix);
        theater.setStatus("ACTIVE");
        theater.setLocation(location);
        theater = theaterRepository.save(theater);

        Screen screen = new Screen();
        screen.setName("Payment Test Screen " + suffix);
        screen.setStatus("ACTIVE");
        screen.setTotalSeats(seatCount);
        screen.setTheater(theater);
        screen = screenRepository.save(screen);

        Movie movie = new Movie();
        movie.setTitle("Payment Test Movie " + suffix);
        movie.setDurationMinutes(120);
        movie.setStatus("ACTIVE");
        movie = movieRepository.save(movie);

        Show show = new Show();
        show.setMovie(movie);
        show.setScreen(screen);
        show.setStartTime(LocalDateTime.now().plusHours(1));
        show.setEndTime(LocalDateTime.now().plusHours(3));
        show.setTicketPrice(new BigDecimal("12.50"));
        show.setStatus("ACTIVE");
        show = showRepository.save(show);

        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= seatCount; i++) {
            Seat seat = new Seat();
            seat.setScreen(screen);
            seat.setRowName("A");
            seat.setSeatNumber(String.valueOf(i));
            seat.setSeatType("STANDARD");
            seat.setStatus("AVAILABLE");
            seat.setPrice(new BigDecimal("12.50"));
            seats.add(seatRepository.save(seat));
        }

        Booking booking = new Booking();
        booking.setBookedAt(LocalDateTime.now());
        booking.setBookingCode("PAYMENT-BOOKING-" + suffix);
        booking.setStatus(bookingStatus);
        booking.setTotalAmount(new BigDecimal("25.00"));
        booking.setCustomer(customer);
        booking.setShow(show);
        booking = bookingRepository.save(booking);

        List<BookingSeat> bookingSeats = new ArrayList<>();
        for (Seat seat : seats) {
            BookingSeat bookingSeat = new BookingSeat();
            bookingSeat.setBooking(booking);
            bookingSeat.setSeat(seat);
            bookingSeat.setPrice(seat.getPrice());
            bookingSeat.setStatus("PENDING");
            bookingSeats.add(bookingSeatRepository.save(bookingSeat));
        }

        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("25.00"));
        payment.setPaymentMethod(PaymentMethod.KHQR);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionId("TXN-PAYMENT-" + suffix);
        payment.setMd5Hash("MOCK_PAID_" + suffix);
        payment.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        payment.setBooking(booking);
        payment.setCustomer(customer);
        payment = paymentRepository.save(payment);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayment(payment);
        transaction.setBooking(booking);
        transaction.setAmount(payment.getAmount());
        transaction.setTransactionType(payment.getPaymentMethod());
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setReference(payment.getTransactionId());
        paymentTransactionRepository.save(transaction);

        return new Fixture(payment, booking, bookingSeats);
    }

    private record Fixture(Payment payment, Booking booking, List<BookingSeat> bookingSeats) {
    }
}
