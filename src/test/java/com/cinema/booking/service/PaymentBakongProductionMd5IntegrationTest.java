package com.cinema.booking.service;

import com.cinema.booking.config.KhqrConfig;
import com.cinema.booking.dto.payments.BakongCheckResult;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class PaymentBakongProductionMd5IntegrationTest {

    private static final String PRODUCTION_MD5 = "7edc7d9c98d571817d59249d839a76a0";
    private static final String PRODUCTION_ACCOUNT_ID = "sothearith_kim1@bkrt";

    @Autowired
    private PaymentService paymentService;

    @MockBean
    private BakongService bakongService;

    @Autowired
    private KhqrConfig khqrConfig;

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

    @BeforeEach
    void setUp() {
        khqrConfig.setMockMode(false);
        khqrConfig.setAccountId(PRODUCTION_ACCOUNT_ID);
        khqrConfig.setBaseUrl("https://api-bakong.nbc.gov.kh");
    }

    @Test
    void storedProductionMd5PaidResponseConfirmsPaymentBookingSeatAndTransaction() {
        Fixture fixture = createFixture(PRODUCTION_MD5);
        when(bakongService.checkTransactionByMd5(PRODUCTION_MD5))
                .thenReturn(BakongCheckResult.paid(
                        PRODUCTION_MD5,
                        "customer@bakong",
                        PRODUCTION_ACCOUNT_ID,
                        new BigDecimal("0.30"),
                        "USD"
                ));

        paymentService.checkStatusFromSystem(fixture.payment().getId());

        verify(bakongService, atLeastOnce()).checkTransactionByMd5(PRODUCTION_MD5);
        assertEquals(PaymentStatus.PAID,
                paymentRepository.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.CONFIRMED,
                bookingRepository.findById(fixture.booking().getId()).orElseThrow().getStatus());
        assertEquals(List.of("CONFIRMED"), bookingSeatRepository.findByBookingId(fixture.booking().getId())
                .stream().map(BookingSeat::getStatus).toList());
        assertEquals(PaymentStatus.PAID,
                paymentTransactionRepository.findByPaymentId(fixture.payment().getId()).get(0).getStatus());
    }

    private Fixture createFixture(String md5) {
        String suffix = String.valueOf(System.nanoTime());

        User customer = new User();
        customer.setName("Production MD5 Customer");
        customer.setUsername("production-md5-" + suffix);
        customer.setEmail("production-md5-" + suffix + "@cinema.test");
        customer.setPassword("password");
        customer.setRole(Role.USER);
        customer.setStatus("ACTIVE");
        customer = userRepository.save(customer);

        Location location = new Location();
        location.setName("Production MD5 Location " + suffix);
        location = locationRepository.save(location);

        Theater theater = new Theater();
        theater.setName("Production MD5 Theater " + suffix);
        theater.setStatus("ACTIVE");
        theater.setLocation(location);
        theater = theaterRepository.save(theater);

        Screen screen = new Screen();
        screen.setName("Production MD5 Screen " + suffix);
        screen.setStatus("ACTIVE");
        screen.setTotalSeats(1);
        screen.setTheater(theater);
        screen = screenRepository.save(screen);

        Movie movie = new Movie();
        movie.setTitle("Production MD5 Movie " + suffix);
        movie.setDurationMinutes(120);
        movie.setStatus("ACTIVE");
        movie = movieRepository.save(movie);

        Show show = new Show();
        show.setMovie(movie);
        show.setScreen(screen);
        show.setStartTime(LocalDateTime.now().plusHours(1));
        show.setEndTime(LocalDateTime.now().plusHours(3));
        show.setTicketPrice(new BigDecimal("0.30"));
        show.setStatus("ACTIVE");
        show = showRepository.save(show);

        Seat seat = new Seat();
        seat.setScreen(screen);
        seat.setRowName("A");
        seat.setSeatNumber("1");
        seat.setSeatType("STANDARD");
        seat.setStatus("AVAILABLE");
        seat.setPrice(new BigDecimal("0.30"));
        seat = seatRepository.save(seat);

        Booking booking = new Booking();
        booking.setBookedAt(LocalDateTime.now());
        booking.setBookingCode("PROD-MD5-" + suffix);
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(new BigDecimal("0.30"));
        booking.setCustomer(customer);
        booking.setShow(show);
        booking = bookingRepository.save(booking);

        BookingSeat bookingSeat = new BookingSeat();
        bookingSeat.setBooking(booking);
        bookingSeat.setSeat(seat);
        bookingSeat.setShowId(show.getId());
        bookingSeat.setPrice(new BigDecimal("0.30"));
        bookingSeat.setStatus("HELD");
        bookingSeatRepository.save(bookingSeat);

        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("0.30"));
        payment.setPaymentMethod(PaymentMethod.KHQR);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionId("TXN-PROD-MD5-" + suffix);
        payment.setMd5Hash(md5);
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

        return new Fixture(payment, booking);
    }

    private record Fixture(Payment payment, Booking booking) {
    }
}
