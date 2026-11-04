package com.cinema.booking.service;

import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.Order;

import java.math.BigDecimal;

public interface BookingTotalService {

    BigDecimal recalculate(Booking booking);

    BigDecimal recalculateOrder(Order order);
}
