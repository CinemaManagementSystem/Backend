package com.cinema.booking.security;

import com.cinema.booking.dto.auth.LoginRequestDto;
import com.cinema.booking.dto.auth.RegisterRequestDto;
import com.cinema.booking.dto.bookings.BookingRequestDto;
import com.cinema.booking.dto.bookings.BookingSeatRequestDto;
import com.cinema.booking.dto.payments.PaymentRequestDto;
import com.cinema.booking.dto.paymenttransaction.PaymentTransactionRequestDto;
import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.Location;
import com.cinema.booking.entity.Movie;
import com.cinema.booking.entity.Order;
import com.cinema.booking.entity.Payment;
import com.cinema.booking.entity.PaymentTransaction;
import com.cinema.booking.entity.Product;
import com.cinema.booking.entity.ProductCategory;
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
import com.cinema.booking.repository.OrderItemRepository;
import com.cinema.booking.repository.OrderRepository;
import com.cinema.booking.repository.PaymentRepository;
import com.cinema.booking.repository.PaymentTransactionRepository;
import com.cinema.booking.repository.ProductCategoryRepository;
import com.cinema.booking.repository.ProductRepository;
import com.cinema.booking.repository.RefreshTokenRepository;
import com.cinema.booking.repository.RevokedAccessTokenRepository;
import com.cinema.booking.repository.ScreenRepository;
import com.cinema.booking.repository.SeatRepository;
import com.cinema.booking.repository.ShowRepository;
import com.cinema.booking.repository.TheaterRepository;
import com.cinema.booking.repository.UserRepository;
import com.cinema.booking.security.ratelimit.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EndpointProtectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RevokedAccessTokenRepository revokedAccessTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private TheaterRepository theaterRepository;

    @Autowired
    private ScreenRepository screenRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ShowRepository showRepository;

    private User userA;
    private User userB;
    private User staff;
    private String userAToken;
    private String userBToken;
    private String staffToken;

    @BeforeEach
    void setUp() {
        rateLimiterService.reset();
        refreshTokenRepository.deleteAll();
        revokedAccessTokenRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        productCategoryRepository.deleteAll();
        bookingSeatRepository.deleteAll();
        bookingRepository.deleteAll();
        showRepository.deleteAll();
        seatRepository.deleteAll();
        screenRepository.deleteAll();
        movieRepository.deleteAll();
        theaterRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();

        userA = createUser("owner-a", Role.USER);
        userB = createUser("owner-b", Role.USER);
        staff = createUser("staff", Role.STAFF);
        userAToken = tokenFor(userA);
        userBToken = tokenFor(userB);
        staffToken = tokenFor(staff);
    }

    @Test
    void loginWithinLimitAllowedAndExceedingLimitReturns429() throws Exception {
        LoginRequestDto dto = LoginRequestDto.builder()
                .username(userA.getUsername())
                .password("Password123")
                .build();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void registerWithinLimitAllowedAndExceedingLimitReturns429() throws Exception {
        for (int i = 0; i < 3; i++) {
            RegisterRequestDto dto = RegisterRequestDto.builder()
                    .username(unique("registered-" + i))
                    .email(unique("registered-" + i) + "@example.com")
                    .password("Password123")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated());
        }

        RegisterRequestDto blocked = RegisterRequestDto.builder()
                .username(unique("registered-blocked"))
                .email(unique("registered-blocked") + "@example.com")
                .password("Password123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blocked)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void createPaymentWithinLimitAllowedAndExcessiveCreateReturns429() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/payments")
                            .header("Authorization", "Bearer " + userAToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cashPaymentRequest(userA))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.customerId", is(userA.getId().intValue())));
        }

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cashPaymentRequest(userA))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void paymentStatusPollingWithinLimitAllowedAndExcessivePollingReturns429() throws Exception {
        Payment payment = createPayment(userA);

        for (int i = 0; i < 30; i++) {
            mockMvc.perform(get("/api/payments/" + payment.getId() + "/status")
                            .header("Authorization", "Bearer " + userAToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(payment.getId().intValue())));
        }

        mockMvc.perform(get("/api/payments/" + payment.getId() + "/status")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void userCannotReadAnotherUsersPayment() throws Exception {
        Payment payment = createPayment(userB);

        mockMvc.perform(get("/api/payments/" + payment.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotConfirmPaymentButStaffCanConfirm() throws Exception {
        Payment userAttempt = createPayment(userA);
        mockMvc.perform(post("/api/payments/" + userAttempt.getId() + "/confirm")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isForbidden());

        Payment staffAttempt = createPayment(userA);
        mockMvc.perform(post("/api/payments/" + staffAttempt.getId() + "/confirm")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAID")))
                .andExpect(jsonPath("$.paidAt", notNullValue()));
    }

    @Test
    void userCannotCreateForgedPaymentTransactionRecord() throws Exception {
        Payment payment = createPayment(userA);
        PaymentTransactionRequestDto dto = new PaymentTransactionRequestDto();
        dto.setAmount(new BigDecimal("999.99"));
        dto.setTransactionType(PaymentMethod.CASH);
        dto.setReference("FORGED-REFERENCE");
        dto.setPaymentId(payment.getId());

        mockMvc.perform(post("/api/payment-transactions")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotViewAnotherUsersPaymentTransaction() throws Exception {
        Payment payment = createPayment(userB);
        PaymentTransaction transaction = createTransaction(payment);

        mockMvc.perform(get("/api/payment-transactions/" + transaction.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookingCreationWithinLimitAllowedAndExcessiveCreateReturns429() throws Exception {
        CinemaFixture fixture = createCinemaFixture();

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + userAToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bookingRequest(userA, fixture.show(), i))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.customerId", is(userA.getId().intValue())));
        }

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookingRequest(userA, fixture.show(), 99))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void orderCreationWithinLimitAllowedAndExcessiveCreateReturns429() throws Exception {
        CinemaFixture fixture = createCinemaFixture();
        Booking booking = createBooking(userA, fixture.show(), "ORDER-LIMIT");

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + userAToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(orderRequest(userA, booking, i))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.customerId", is(userA.getId().intValue())));
        }

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest(userA, booking, 99))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void orderItemCreationWithinLimitAllowedAndExcessiveCreateReturns429() throws Exception {
        CinemaFixture fixture = createCinemaFixture();
        Booking booking = createBooking(userA, fixture.show(), "ITEM-LIMIT");
        Order order = createOrder(userA, booking, "ITEM-LIMIT");
        Product product = createProduct();

        for (int i = 0; i < 30; i++) {
            mockMvc.perform(post("/api/order-items")
                            .header("Authorization", "Bearer " + userAToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(orderItemRequest(order, product))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId", is(order.getId().intValue())));
        }

        mockMvc.perform(post("/api/order-items")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderItemRequest(order, product))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void seatReservationAllowsMultipleSeatsButBlocksDuplicateShowSeat() throws Exception {
        CinemaFixture fixture = createCinemaFixture();
        Booking bookingA = createBooking(userA, fixture.show(), "A");
        Booking bookingB = createBooking(userB, fixture.show(), "B");

        mockMvc.perform(post("/api/booking-seats")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookingSeatRequest(bookingA, fixture.seatA()))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/booking-seats")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookingSeatRequest(bookingA, fixture.seatB()))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/booking-seats")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookingSeatRequest(bookingB, fixture.seatA()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userCannotAccessStaffMovieCategoryWriteEndpoint() throws Exception {
        mockMvc.perform(post("/api/movie-category")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/movie-category")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private User createUser(String prefix, Role role) {
        User user = new User();
        user.setUsername(unique(prefix));
        user.setEmail(unique(prefix) + "@example.com");
        user.setPassword(passwordEncoder.encode("Password123"));
        user.setName(prefix + " User");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return userRepository.save(user);
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(userDetailsService.loadUserByUsername(user.getUsername()));
    }

    private PaymentRequestDto cashPaymentRequest(User customer) {
        return new PaymentRequestDto(new BigDecimal("12.50"), PaymentMethod.CASH, customer.getId(), null, null);
    }

    private Payment createPayment(User customer) {
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("12.50"));
        payment.setPaymentMethod(PaymentMethod.CASH);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCustomer(customer);
        return paymentRepository.save(payment);
    }

    private PaymentTransaction createTransaction(Payment payment) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayment(payment);
        transaction.setAmount(payment.getAmount());
        transaction.setTransactionType(payment.getPaymentMethod());
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setReference(unique("TX-AUDIT"));
        return paymentTransactionRepository.save(transaction);
    }

    private BookingRequestDto bookingRequest(User customer, Show show, int index) {
        BookingRequestDto dto = new BookingRequestDto();
        dto.setBookedAt(LocalDateTime.now());
        dto.setBookingCode(unique("BOOK-" + index));
        dto.setTotalAmount(new BigDecimal("12.50"));
        dto.setCustomerId(customer.getId());
        dto.setShowId(show.getId());
        return dto;
    }

    private com.cinema.booking.dto.orders.OrderRequestDto orderRequest(User customer, Booking booking, int index) {
        com.cinema.booking.dto.orders.OrderRequestDto dto = new com.cinema.booking.dto.orders.OrderRequestDto();
        dto.setCompletedAt(LocalDateTime.now());
        dto.setOrderNumber(unique("ORDER-" + index));
        dto.setOrderType("FOOD");
        dto.setOrderedAt(LocalDateTime.now());
        dto.setStatus("PENDING");
        dto.setSubtotal(new BigDecimal("12.50"));
        dto.setTotalAmount(new BigDecimal("12.50"));
        dto.setBookingId(booking.getId());
        dto.setCustomerId(customer.getId());
        return dto;
    }

    private com.cinema.booking.dto.orders.OrderItemRequestDto orderItemRequest(Order order, Product product) {
        com.cinema.booking.dto.orders.OrderItemRequestDto dto = new com.cinema.booking.dto.orders.OrderItemRequestDto();
        dto.setQuantity(1);
        dto.setUnitPrice(new BigDecimal("5.00"));
        dto.setSubtotal(new BigDecimal("5.00"));
        dto.setOrderId(order.getId());
        dto.setProductId(product.getId());
        return dto;
    }

    private BookingSeatRequestDto bookingSeatRequest(Booking booking, Seat seat) {
        BookingSeatRequestDto dto = new BookingSeatRequestDto();
        dto.setBookingId(booking.getId());
        dto.setSeatId(seat.getId());
        return dto;
    }

    private Booking createBooking(User customer, Show show, String suffix) {
        Booking booking = new Booking();
        booking.setBookedAt(LocalDateTime.now());
        booking.setBookingCode(unique("BOOK-" + suffix));
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(new BigDecimal("12.50"));
        booking.setCustomer(customer);
        booking.setShow(show);
        return bookingRepository.save(booking);
    }

    private Order createOrder(User customer, Booking booking, String suffix) {
        Order order = new Order();
        order.setCompletedAt(LocalDateTime.now());
        order.setOrderNumber(unique("ORDER-" + suffix));
        order.setOrderType("FOOD");
        order.setOrderedAt(LocalDateTime.now());
        order.setStatus("PENDING");
        order.setSubtotal(new BigDecimal("12.50"));
        order.setTotalAmount(new BigDecimal("12.50"));
        order.setBooking(booking);
        order.setCustomer(customer);
        return orderRepository.save(order);
    }

    private Product createProduct() {
        ProductCategory category = new ProductCategory();
        category.setName(unique("Snacks"));
        category.setDescription("Snacks");
        category.setIsActive(true);
        category = productCategoryRepository.save(category);

        Product product = new Product();
        product.setName(unique("Popcorn"));
        product.setPrice(new BigDecimal("5.00"));
        product.setIsAvailable(true);
        product.setStockQuantity(100);
        product.setProductCategory(category);
        return productRepository.save(product);
    }

    private CinemaFixture createCinemaFixture() {
        Location location = new Location();
        location.setName(unique("Location"));
        location = locationRepository.save(location);

        Theater theater = new Theater();
        theater.setName(unique("Theater"));
        theater.setStatus("ACTIVE");
        theater.setLocation(location);
        theater = theaterRepository.save(theater);

        Screen screen = new Screen();
        screen.setName(unique("Screen"));
        screen.setStatus("ACTIVE");
        screen.setTotalSeats(2);
        screen.setTheater(theater);
        screen = screenRepository.save(screen);

        Movie movie = new Movie();
        movie.setTitle(unique("Movie"));
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

        Seat seatA = createSeat(screen, "1");
        Seat seatB = createSeat(screen, "2");

        return new CinemaFixture(show, seatA, seatB);
    }

    private Seat createSeat(Screen screen, String number) {
        Seat seat = new Seat();
        seat.setScreen(screen);
        seat.setRowName("A");
        seat.setSeatNumber(number);
        seat.setSeatType("STANDARD");
        seat.setStatus("AVAILABLE");
        seat.setPrice(new BigDecimal("12.50"));
        return seatRepository.save(seat);
    }

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    private record CinemaFixture(Show show, Seat seatA, Seat seatB) {
    }
}
