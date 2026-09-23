package com.cinema.booking.controller;

import com.cinema.booking.dto.paymenttransaction.PaymentTransactionRequestDto;
import com.cinema.booking.dto.paymenttransaction.PaymentTransactionResponseDto;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/payment-transactions")
@RequiredArgsConstructor
public class PaymentTransactionController {

    private final PaymentTransactionService paymentTransactionService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentTransactionResponseDto> create(@Valid @RequestBody PaymentTransactionRequestDto dto) {
        return new ResponseEntity<>(paymentTransactionService.create(dto), HttpStatus.CREATED);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaymentTransactionResponseDto> createWithImage(
            @Valid @ModelAttribute PaymentTransactionRequestDto dto,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        return new ResponseEntity<>(paymentTransactionService.create(dto, image), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentTransactionResponseDto> update(@PathVariable Long id, @Valid @RequestBody PaymentTransactionRequestDto dto) {
        return ResponseEntity.ok(paymentTransactionService.update(id, dto));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaymentTransactionResponseDto> updateWithImage(
            @PathVariable Long id,
            @Valid @ModelAttribute PaymentTransactionRequestDto dto,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(paymentTransactionService.update(id, dto, image));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentTransactionResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentTransactionService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<PaymentTransactionResponseDto>> getAll() {
        return ResponseEntity.ok(paymentTransactionService.getAll());
    }

    @GetMapping("/page")
    public ResponseEntity<Page<PaymentTransactionResponseDto>> getPage(
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(paymentTransactionService.getPage(status, pageable));
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
