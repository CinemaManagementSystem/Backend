package com.cinema.booking.controller;

import com.cinema.booking.dto.payments.PaymentRequestDto;
import com.cinema.booking.dto.payments.PaymentResponseDto;
import com.cinema.booking.service.PaymentService;
import com.cinema.booking.enums.PaymentVerificationSource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponseDto> create(@Valid @RequestBody PaymentRequestDto dto) {
        return new ResponseEntity<>(paymentService.create(dto), HttpStatus.CREATED);
    }

    @PostMapping("/{id}/confirm")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<PaymentResponseDto> confirmPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.confirmPayment(id));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<PaymentResponseDto> checkStatus(
            @PathVariable Long id,
            @RequestParam(defaultValue = "MANUAL") String source
    ) {
        PaymentVerificationSource requestSource;
        try {
            requestSource = PaymentVerificationSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported payment verification source");
        }
        if (requestSource == PaymentVerificationSource.SYSTEM) {
            throw new IllegalArgumentException("SYSTEM payment verification is internal only");
        }
        return ResponseEntity.ok(paymentService.checkStatus(id, requestSource));
    }

    @PostMapping("/{id}/switch-to-cash")
    public ResponseEntity<PaymentResponseDto> switchToCash(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.switchToCash(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentResponseDto> update(@PathVariable Long id, @Valid @RequestBody PaymentRequestDto dto) {
        return ResponseEntity.ok(paymentService.update(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponseDto>> getAll() {
        return ResponseEntity.ok(paymentService.getAll());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        paymentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
