package com.rishivyas.fraud.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/** A card payment event as it arrives on the {@code transactions} topic. */
public record Transaction(
        @NotBlank String transactionId,
        @NotBlank String customerId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String merchantId,
        @NotBlank String merchantCategory,
        @NotBlank String deviceId,
        @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
        @NotNull Instant timestamp) {
}
