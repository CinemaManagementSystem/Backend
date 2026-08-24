package com.cinema.booking.mapper;

import com.cinema.booking.entity.WalletTransaction;
import com.cinema.booking.dto.transaction.WalletTransactionRequestDto;
import com.cinema.booking.dto.transaction.WalletTransactionResponseDto;

import org.springframework.stereotype.Component;

@Component
public class WalletTransactionMapper {

    public WalletTransaction toEntity(WalletTransactionRequestDto dto) {
        WalletTransaction walletTransaction = new WalletTransaction();
        walletTransaction.setAmount(dto.getAmount());
        walletTransaction.setReference(dto.getReference());
        walletTransaction.setStatus(dto.getStatus());
        walletTransaction.setTransactionType(dto.getTransactionType());
        // TODO: FK fields (booking, order, wallet) are resolved in the Service layer
        // using their respective repositories, then set on walletTransaction before saving.
        return walletTransaction;
    }

    public WalletTransactionResponseDto toResponseDto(WalletTransaction walletTransaction) {
        WalletTransactionResponseDto dto = new WalletTransactionResponseDto();
        dto.setId(walletTransaction.getId());
        dto.setAmount(walletTransaction.getAmount());
        dto.setCreatedAt(walletTransaction.getCreatedAt());
        dto.setReference(walletTransaction.getReference());
        dto.setStatus(walletTransaction.getStatus());
        dto.setTransactionType(walletTransaction.getTransactionType());
        dto.setBookingId(walletTransaction.getBooking() != null ? walletTransaction.getBooking().getId() : null);
        dto.setOrderId(walletTransaction.getOrder() != null ? walletTransaction.getOrder().getId() : null);
        dto.setWalletId(walletTransaction.getWallet() != null ? walletTransaction.getWallet().getId() : null);
        return dto;
    }
}