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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderItemServiceImpl implements OrderItemService {

    private final OrderItemRepository orderItemRepository;
    private final OrderItemMapper orderItemMapper;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final AuthorizationService authorizationService;

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
        orderItem = orderItemRepository.save(orderItem);
        return orderItemMapper.toResponseDto(orderItem);
    }

    @Override
    @Transactional
    public OrderItemResponseDto update(Long id, OrderItemRequestDto dto) {
        OrderItem existing = orderItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", id));
        authorizationService.requireOwnerOrStaff(existing.getOrder().getCustomer());
        Order order = orderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", dto.getOrderId()));
        authorizationService.requireOwnerOrStaff(order.getCustomer());

        OrderItem updated = orderItemMapper.toEntity(dto);
        updated.setId(existing.getId());
        updated.setOrder(order);
        updated.setProduct(productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", dto.getProductId())));
        updated = orderItemRepository.save(updated);
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
        orderItemRepository.delete(orderItem);
    }
}
