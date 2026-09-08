package com.cinema.booking.service.impl;

import com.cinema.booking.entity.Order;
import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.User;
import com.cinema.booking.dto.orders.OrderRequestDto;
import com.cinema.booking.dto.orders.OrderResponseDto;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.mapper.OrderMapper;
import com.cinema.booking.repository.OrderRepository;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.security.AuthorizationService;
import com.cinema.booking.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final BookingRepository bookingRepository;
    private final AuthorizationService authorizationService;

    @Override
    @Transactional
    public OrderResponseDto create(OrderRequestDto dto) {
        Order order = orderMapper.toEntity(dto);
        if (dto.getBookingId() != null) {
            Booking booking = bookingRepository.findById(dto.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.getBookingId()));
            authorizationService.requireOwnerOrStaff(booking.getCustomer());
            order.setBooking(booking);
        }
        User customer = authorizationService.resolveCustomerForAuthenticatedRequest(dto.getCustomerId());
        order.setCustomer(customer);
        order = orderRepository.save(order);
        return orderMapper.toResponseDto(order);
    }

    @Override
    @Transactional
    public OrderResponseDto update(Long id, OrderRequestDto dto) {
        Order existing = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        authorizationService.requireOwnerOrStaff(existing.getCustomer());

        Order updated = orderMapper.toEntity(dto);
        updated.setId(existing.getId());
        if (dto.getBookingId() != null) {
            Booking booking = bookingRepository.findById(dto.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.getBookingId()));
            authorizationService.requireOwnerOrStaff(booking.getCustomer());
            updated.setBooking(booking);
        }
        updated.setCustomer(authorizationService.resolveCustomerForAuthenticatedRequest(dto.getCustomerId()));
        updated = orderRepository.save(updated);
        return orderMapper.toResponseDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDto getById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        authorizationService.requireOwnerOrStaff(order.getCustomer());
        return orderMapper.toResponseDto(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDto> getAll() {
        User currentUser = authorizationService.getCurrentUser();
        List<Order> orders = authorizationService.isStaffOrAdmin(currentUser)
                ? orderRepository.findAll()
                : orderRepository.findByCustomerId(currentUser.getId());
        return orders.stream()
                .map(orderMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        authorizationService.requireOwnerOrStaff(order.getCustomer());
        orderRepository.delete(order);
    }
}
