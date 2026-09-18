package com.cinema.booking.service.impl;

import com.cinema.booking.entity.OrderItem;
import com.cinema.booking.entity.Order;
import com.cinema.booking.entity.Product;
import com.cinema.booking.dto.orders.OrderItemRequestDto;
import com.cinema.booking.dto.orders.OrderItemResponseDto;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.mapper.OrderItemMapper;
import com.cinema.booking.repository.OrderItemRepository;
import com.cinema.booking.repository.OrderRepository;
import com.cinema.booking.repository.ProductRepository;
import com.cinema.booking.security.AuthorizationService;
import com.cinema.booking.service.OrderItemService;
import com.cinema.booking.service.BookingTotalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.math.BigDecimal;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderItemServiceImpl implements OrderItemService {

    private final OrderItemRepository orderItemRepository;
    private final OrderItemMapper orderItemMapper;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final AuthorizationService authorizationService;
    private final BookingTotalService bookingTotalService;

    @Override
    @Transactional
    public OrderItemResponseDto create(OrderItemRequestDto dto) {
        OrderItem orderItem = orderItemMapper.toEntity(dto);
        Order order = orderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", dto.getOrderId()));
        authorizationService.requireOwnerOrStaff(order.getCustomer());
        orderItem.setOrder(order);
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", dto.getProductId()));
        orderItem.setProduct(product);
        snapshotPrice(orderItem, product);
        orderItem = orderItemRepository.save(orderItem);
        refreshTotals(order);
        return orderItemMapper.toResponseDto(orderItem);
    }

    @Override
    @Transactional
    public OrderItemResponseDto update(Long id, OrderItemRequestDto dto) {
        OrderItem existing = orderItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", id));
        authorizationService.requireOwnerOrStaff(existing.getOrder().getCustomer());
        Order previousOrder = existing.getOrder();
        Order order = orderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", dto.getOrderId()));
        authorizationService.requireOwnerOrStaff(order.getCustomer());

        OrderItem updated = orderItemMapper.toEntity(dto);
        updated.setId(existing.getId());
        updated.setOrder(order);
        updated.setProduct(productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", dto.getProductId())));
        if (existing.getProduct() != null && existing.getProduct().getId().equals(updated.getProduct().getId())) {
            updated.setUnitPrice(existing.getUnitPrice());
        } else {
            snapshotPrice(updated, updated.getProduct());
        }
        updated = orderItemRepository.save(updated);
        refreshTotals(previousOrder);
        if (!previousOrder.getId().equals(order.getId())) {
            refreshTotals(order);
        }
        return orderItemMapper.toResponseDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderItemResponseDto getById(Long id) {
        OrderItem orderItem = orderItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", id));
        authorizationService.requireOwnerOrStaff(orderItem.getOrder().getCustomer());
        return orderItemMapper.toResponseDto(orderItem);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderItemResponseDto> getAll() {
        var currentUser = authorizationService.getCurrentUser();
        List<OrderItem> orderItems = authorizationService.isStaffOrAdmin(currentUser)
                ? orderItemRepository.findAll()
                : orderItemRepository.findByOrderCustomerId(currentUser.getId());
        return orderItems.stream()
                .map(orderItemMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        OrderItem orderItem = orderItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", id));
        authorizationService.requireOwnerOrStaff(orderItem.getOrder().getCustomer());
        Order order = orderItem.getOrder();
        orderItemRepository.delete(orderItem);
        refreshTotals(order);
    }

    private void refreshTotals(Order order) {
        if (order.getBooking() != null) {
            bookingTotalService.recalculate(order.getBooking());
        } else {
            bookingTotalService.recalculateOrder(order);
        }
    }

    private void snapshotPrice(OrderItem orderItem, Product product) {
        BigDecimal price = product.getPrice();
        int quantity = orderItem.getQuantity() != null ? orderItem.getQuantity() : 0;
        if (price == null || quantity < 1) {
            throw new IllegalArgumentException("Product price and quantity must be valid");
        }
        orderItem.setUnitPrice(price);
        orderItem.setSubtotal(price.multiply(BigDecimal.valueOf(quantity)));
    }
}
