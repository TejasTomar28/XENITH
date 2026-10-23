package com.fraudshield.backend.authorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fraudshield.backend.domain.model.TransactionChannel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AuthorizationRequest(
        @JsonProperty("txn_id")
        @NotBlank @Pattern(regexp = "^T[0-9]{9}$") String transactionId,
        @JsonProperty("card_id")
        @NotBlank String cardId,
        @JsonProperty("txn_ts")
        @NotNull OffsetDateTime transactionTimestamp,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
        BigDecimal amount,
        @NotBlank @Pattern(regexp = "INR") String currency,
        @JsonProperty("merchant_id")
        @NotBlank String merchantId,
        @NotBlank @Pattern(regexp = "^[0-9]{4}$") String mcc,
        @NotNull TransactionChannel channel,
        @JsonProperty("txn_city")
        @NotBlank String transactionCity,
        @JsonProperty("txn_country")
        @NotBlank @Size(min = 2, max = 2) String transactionCountry) {}
