package com.cinema.booking.service;

import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.Order;
import com.cinema.booking.entity.OrderItem;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.repository.BookingSeatRepository;
import com.cinema.booking.repository.OrderItemRepository;
import com.cinema.booking.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BookingTotalService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public BigDecimal recalculate(Booking booking) {
        BigDecimal total = nullSafe(bookingSeatRepository.sumActivePricesByBookingId(booking.getId()));
        booking.setTotalAmount(total);
        bookingRepository.save(booking);

        // Keep add-on orders internally consistent, but keep their total out of
        // bookings.total_amount. The booking amount is the ticket-seat amount;
        // payment creation explicitly adds a linked order when applicable.
        orderRepository.findByBookingId(booking.getId()).forEach(this::recalculateOrder);
        return total;
    }

    @Transactional
    public BigDecimal recalculateOrder(Order order) {
        BigDecimal total = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(this::calculateLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setSubtotal(total);
        order.setTotalAmount(total);
        orderRepository.save(order);
        return total;
    }

    private BigDecimal calculateLineTotal(OrderItem item) {
        // OrderItem.unitPrice is the price snapshot captured when the item was added.
        BigDecimal unitPrice = item.getUnitPrice();
        int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
        return nullSafe(unitPrice).multiply(BigDecimal.valueOf(quantity));
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
