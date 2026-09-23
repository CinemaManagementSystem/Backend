package com.cinema.booking.service;

import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.BookingSeat;
import com.cinema.booking.entity.Location;
import com.cinema.booking.entity.Movie;
import com.cinema.booking.entity.Order;
import com.cinema.booking.entity.OrderItem;
import com.cinema.booking.entity.Payment;
import com.cinema.booking.entity.PaymentTransaction;
import com.cinema.booking.entity.Product;
import com.cinema.booking.entity.Screen;
import com.cinema.booking.entity.Seat;
import com.cinema.booking.entity.Show;
import com.cinema.booking.entity.Theater;
import com.cinema.booking.entity.User;
import com.cinema.booking.dto.payments.PaymentRequestDto;
import com.cinema.booking.dto.payments.PaymentResponseDto;
import com.cinema.booking.dto.bookings.BookingRequestDto;
import com.cinema.booking.dto.bookings.BookingResponseDto;
import com.cinema.booking.enums.BookingStatus;
import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.enums.Role;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.repository.BookingSeatRepository;
import com.cinema.booking.repository.LocationRepository;
import com.cinema.booking.repository.MovieRepository;
import com.cinema.booking.repository.OrderItemRepository;
import com.cinema.booking.repository.OrderRepository;
import com.cinema.booking.repository.PaymentRepository;
import com.cinema.booking.repository.PaymentTransactionRepository;
import com.cinema.booking.repository.ProductRepository;
import com.cinema.booking.repository.ScreenRepository;
import com.cinema.booking.repository.SeatRepository;
import com.cinema.booking.repository.ShowRepository;
import com.cinema.booking.repository.TheaterRepository;
import com.cinema.booking.repository.UserRepository;
import com.cinema.booking.scheduler.PaymentExpirationScheduler;
import com.cinema.booking.config.KhqrConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class PaymentConfirmationIntegrationTest {

    private static final String MOCK_PAID_MD5 = "deadbeefdeadbeefdeadbeefdeadbeef";
    private static final String MOCK_PENDING_MD5 = "0123456789abcdef0123456789abcdef";

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingTotalService bookingTotalService;

    @Autowired
    private PaymentExpirationScheduler paymentExpirationScheduler;

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    void successfulPaymentConfirmsPaymentBookingSeatsAndTransaction() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);

        paymentService.confirmPaymentFromSystem(fixture.payment().getId());

        Payment persistedPayment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();
        assertEquals(PaymentStatus.PAID, persistedPayment.getStatus());
        assertNotNull(persistedPayment.getPaidAt());
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
    void bookingTotalIsCalculatedFromSeatsAndAddOns() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);
        Booking booking = bookingRepository.findById(fixture.booking().getId()).orElseThrow();
        booking.setTotalAmount(new BigDecimal("0.10"));
        bookingRepository.save(booking);

        Product product = new Product();
        product.setName("Payment Test Snack");
        product.setPrice(new BigDecimal("2.00"));
        product.setIsAvailable(true);
        product.setStockQuantity(10);
        product = productRepository.save(product);

        Order order = new Order();
        order.setOrderNumber("PAYMENT-ORDER-" + System.nanoTime());
        order.setOrderType("SNACK");
        order.setOrderedAt(LocalDateTime.now());
        order.setStatus("PENDING");
        order.setSubtotal(new BigDecimal("999.00"));
        order.setTotalAmount(new BigDecimal("999.00"));
        order.setBooking(booking);
        order.setCustomer(booking.getCustomer());
        order = orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("999.00"));
        item.setSubtotal(new BigDecimal("999.00"));
        item.setOrder(order);
        item.setProduct(product);
        orderItemRepository.save(item);

        BigDecimal total = bookingTotalService.recalculate(booking);

        assertEquals(new BigDecimal("12.50"), total);
        assertEquals(new BigDecimal("12.50"),
                bookingRepository.findById(booking.getId()).orElseThrow().getTotalAmount());
        // The persisted unit price is the add-on price snapshot; later product
        // price changes must not rewrite this existing order item.
        assertEquals(new BigDecimal("1998.00"), orderRepository.findById(order.getId()).orElseThrow().getTotalAmount());
    }

    @Test
    void bookingReferenceAndHoldStartAreServerOwned() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);
        User customer = fixture.booking().getCustomer();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        customer.getUsername(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        try {
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            BookingRequestDto request = new BookingRequestDto();
            request.setBookedAt(LocalDateTime.now().minusDays(1));
            request.setBookingCode("CLIENT-CONTROLLED-CODE");
            request.setTotalAmount(new BigDecimal("999.99"));
            request.setCustomerId(customer.getId());
            request.setShowId(fixture.booking().getShow().getId());

            BookingResponseDto created = bookingService.create(request);

            assertNotEquals("CLIENT-CONTROLLED-CODE", created.getBookingCode());
            assertTrue(created.getBookedAt().isAfter(before));
            Duration expiryDifference = Duration.between(
                    created.getExpiresAt(), fixture.booking().getShow().getStartTime().minusMinutes(5)).abs();
            assertTrue(expiryDifference.toMillis() < 1_000,
                    "Booking expiry should be five minutes before showtime");
            assertEquals(BigDecimal.ZERO, created.getTotalAmount());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void paymentCreationRejectsAStaleClientAmount() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);
        User customer = fixture.booking().getCustomer();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        customer.getUsername(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        try {
            PaymentRequestDto request = new PaymentRequestDto(
                    new BigDecimal("0.01"), PaymentMethod.KHQR, customer.getId(),
                    fixture.booking().getId(), null);

            assertThrows(IllegalArgumentException.class, () -> paymentService.create(request));
            assertEquals(1, paymentTransactionRepository.findByPaymentId(fixture.payment().getId()).size());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void khqrPaymentCreationCapsExpiryAtTenMinutes() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);
        Booking booking = bookingRepository.findById(fixture.booking().getId()).orElseThrow();
        booking.setExpiresAt(LocalDateTime.now().plusHours(1));
        bookingRepository.save(booking);
        User customer = fixture.booking().getCustomer();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        customer.getUsername(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        try {
            LocalDateTime before = LocalDateTime.now();
            PaymentResponseDto created = paymentService.create(new PaymentRequestDto(
                    new BigDecimal("12.50"), PaymentMethod.KHQR, customer.getId(), booking.getId(), null));

            assertTrue(created.expiresAt().isAfter(before));
            assertTrue(Duration.between(before, created.expiresAt()).compareTo(Duration.ofMinutes(10)) <= 0);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void existingKhqrAmountCannotChangeWithoutRegeneratingQr() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();
        payment.setKhqrString("000201010212-test-qr");
        paymentRepository.save(payment);
        User customer = fixture.booking().getCustomer();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        customer.getUsername(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        try {
            PaymentRequestDto request = new PaymentRequestDto(
                    new BigDecimal("12.50"), PaymentMethod.KHQR, customer.getId(),
                    fixture.booking().getId(), null);

            assertThrows(IllegalArgumentException.class,
                    () -> paymentService.update(payment.getId(), request));
            assertEquals(new BigDecimal("25.00"),
                    paymentRepository.findById(payment.getId()).orElseThrow().getAmount());
        } finally {
            SecurityContextHolder.clearContext();
        }
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
    void paidBakongResultWinsWhenLocalExpiryHasPassed() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();
        payment.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        paymentRepository.save(payment);

        paymentService.checkStatusFromSystem(payment.getId());

        assertEquals(PaymentStatus.PAID, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.CONFIRMED, bookingRepository.findById(fixture.booking().getId()).orElseThrow().getStatus());
        assertEquals(1, paymentTransactionRepository.findByPaymentId(payment.getId()).size());
        assertEquals(PaymentStatus.PAID,
                paymentTransactionRepository.findByPaymentId(payment.getId()).get(0).getStatus());
    }

    @Test
    void unpaidBakongResultKeepsPaymentPendingBeforeExpiry() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();
        payment.setMd5Hash(MOCK_PENDING_MD5);
        payment.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        paymentRepository.save(payment);

        paymentService.checkStatusFromSystem(payment.getId());

        assertEquals(PaymentStatus.PENDING, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.PENDING, bookingRepository.findById(fixture.booking().getId()).orElseThrow().getStatus());
    }

    @Test
    void schedulerPerformsBakongCheckBeforeExpiringAnExpiredPayment() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();
        payment.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        paymentRepository.save(payment);

        paymentExpirationScheduler.pollPendingKhqrPayments();

        assertEquals(PaymentStatus.PAID, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.CONFIRMED, bookingRepository.findById(fixture.booking().getId()).orElseThrow().getStatus());
    }

    @Test
    void expiredPaymentIsNeverSentBackToBakong() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);

        bookingService.expirePendingBooking(fixture.booking().getId());

        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();
        payment.setMd5Hash(MOCK_PAID_MD5);
        paymentRepository.save(payment);

        paymentService.checkStatusFromSystem(payment.getId());

        assertEquals(PaymentStatus.EXPIRED, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.EXPIRED, bookingRepository.findById(fixture.booking().getId()).orElseThrow().getStatus());
        assertEquals(List.of("CANCELLED"), bookingSeatRepository.findByBookingId(fixture.booking().getId())
                .stream().map(BookingSeat::getStatus).toList());
    }

    @Test
    void failedPaymentIsNeverSentBackToBakong() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();
        payment.setStatus(PaymentStatus.FAILED);
        payment.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        paymentRepository.save(payment);

        paymentService.checkStatusFromSystem(payment.getId());

        assertEquals(PaymentStatus.FAILED, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.PENDING, bookingRepository.findById(fixture.booking().getId()).orElseThrow().getStatus());
        assertEquals(List.of("PENDING"), bookingSeatRepository.findByBookingId(fixture.booking().getId())
                .stream().map(BookingSeat::getStatus).toList());
        assertEquals(PaymentStatus.PENDING,
                paymentTransactionRepository.findByPaymentId(payment.getId()).get(0).getStatus());
    }

    @Test
    void bakongFailureAfterExpiryDoesNotTurnPendingPaymentIntoFailed() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();
        payment.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        paymentRepository.save(payment);

        boolean oldMockMode = khqrConfig.isMockMode();
        String oldToken = khqrConfig.getToken();
        try {
            khqrConfig.setMockMode(false);
            khqrConfig.setToken("");

            paymentService.checkStatusFromSystem(payment.getId());

            assertEquals(PaymentStatus.PENDING, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
            assertEquals(BookingStatus.PENDING, bookingRepository.findById(fixture.booking().getId()).orElseThrow().getStatus());
        } finally {
            khqrConfig.setMockMode(oldMockMode);
            khqrConfig.setToken(oldToken);
        }
    }

    @Test
    void expiredPaymentUpdatesExistingTransactionWithoutCreatingDuplicate() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();
        payment.setMd5Hash(MOCK_PENDING_MD5);
        payment.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        paymentRepository.save(payment);

        paymentService.checkStatusFromSystem(payment.getId());
        paymentService.checkStatusFromSystem(payment.getId());

        List<PaymentTransaction> transactions = paymentTransactionRepository.findByPaymentId(payment.getId());
        assertEquals(PaymentStatus.EXPIRED, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.EXPIRED, bookingRepository.findById(fixture.booking().getId()).orElseThrow().getStatus());
        assertEquals(List.of("CANCELLED"), bookingSeatRepository.findByBookingId(fixture.booking().getId())
                .stream().map(BookingSeat::getStatus).toList());
        assertEquals(1, transactions.size());
        assertEquals(payment.getTransactionId(), transactions.get(0).getReference());
        assertEquals(PaymentStatus.EXPIRED, transactions.get(0).getStatus());
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
    void retryReusesPendingPaymentRowAndLogsEachAttempt() {
        Fixture fixture = createFixture(1, BookingStatus.PENDING);
        User customer = fixture.booking().getCustomer();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        customer.getUsername(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        try {
            PaymentRequestDto request = new PaymentRequestDto(
                    new BigDecimal("12.50"),
                    PaymentMethod.KHQR,
                    customer.getId(),
                    fixture.booking().getId(),
                    null);

            PaymentResponseDto firstRetry = paymentService.create(request);
            PaymentResponseDto secondRetry = paymentService.create(request);

            assertEquals(fixture.payment().getId(), firstRetry.id());
            assertEquals(firstRetry.id(), secondRetry.id());
            assertEquals(1, paymentRepository.findByBookingId(fixture.booking().getId()).stream()
                    .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                    .count());
            List<PaymentTransaction> attempts = paymentTransactionRepository.findByPaymentId(fixture.payment().getId());
            assertEquals(3, attempts.size());
            assertEquals(2, attempts.stream().filter(t -> t.getStatus() == PaymentStatus.FAILED).count());
            assertEquals(1, attempts.stream().filter(t -> t.getStatus() == PaymentStatus.PENDING).count());
        } finally {
            SecurityContextHolder.clearContext();
        }
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
            bookingSeat.setShowId(show.getId());
            bookingSeat.setPrice(seat.getPrice());
            bookingSeat.setStatus("PENDING");
            bookingSeats.add(bookingSeatRepository.save(bookingSeat));
        }

        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("25.00"));
        payment.setPaymentMethod(PaymentMethod.KHQR);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionId("TXN-PAYMENT-" + suffix);
        payment.setMd5Hash(MOCK_PAID_MD5);
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
