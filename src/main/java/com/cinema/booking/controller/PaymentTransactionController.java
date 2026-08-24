package com.cinema.booking.controller;

import com.cinema.booking.dto.paymenttransaction.PaymentTransactionRequestDto;
import com.cinema.booking.dto.paymenttransaction.PaymentTransactionResponseDto;
import com.cinema.booking.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/payment-transactions")
@RequiredArgsConstructor
public class PaymentTransactionController {

    private final PaymentTransactionService paymentTransactionService;

    @PostMapping
    public ResponseEntity<PaymentTransactionResponseDto> create(@Valid @RequestBody PaymentTransactionRequestDto dto) {
        return new ResponseEntity<>(paymentTransactionService.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentTransactionResponseDto> update(@PathVariable Long id, @Valid @RequestBody PaymentTransactionRequestDto dto) {
        return ResponseEntity.ok(paymentTransactionService.update(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentTransactionResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentTransactionService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<PaymentTransactionResponseDto>> getAll() {
        return ResponseEntity.ok(paymentTransactionService.getAll());
    }

    @GetMapping("/by-payment/{paymentId}")
    public ResponseEntity<List<PaymentTransactionResponseDto>> getByPaymentId(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentTransactionService.getByPaymentId(paymentId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        paymentTransactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
