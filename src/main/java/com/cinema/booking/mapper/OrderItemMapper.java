package com.cinema.booking.mapper;

import com.cinema.booking.entity.OrderItem;
import com.cinema.booking.dto.orders.OrderItemRequestDto;
import com.cinema.booking.dto.orders.OrderItemResponseDto;
import org.springframework.stereotype.Component;

@Component
public class OrderItemMapper {

    public OrderItem toEntity(OrderItemRequestDto dto) {
        OrderItem orderItem = new OrderItem();
        orderItem.setQuantity(dto.getQuantity());
        orderItem.setSubtotal(dto.getSubtotal());
        orderItem.setUnitPrice(dto.getUnitPrice());
        // TODO: FK fields (order, product) are resolved in the Service layer
        // using their respective repositories, then set on orderItem before saving.
        return orderItem;
    }

    public OrderItemResponseDto toResponseDto(OrderItem orderItem) {
        OrderItemResponseDto dto = new OrderItemResponseDto();
        dto.setId(orderItem.getId());
        dto.setQuantity(orderItem.getQuantity());
        dto.setSubtotal(orderItem.getSubtotal());
        dto.setUnitPrice(orderItem.getUnitPrice());
        dto.setOriginalUnitPrice(orderItem.getOriginalUnitPrice());
        dto.setMembershipDiscountAmount(orderItem.getMembershipDiscountAmount());
        dto.setMembershipBenefitCode(orderItem.getMembershipBenefitCode());
        dto.setOrderId(orderItem.getOrder() != null ? orderItem.getOrder().getId() : null);
        dto.setProductId(orderItem.getProduct() != null ? orderItem.getProduct().getId() : null);
        dto.setUserMembershipId(orderItem.getUserMembership() != null ? orderItem.getUserMembership().getId() : null);
        return dto;
    }
}
