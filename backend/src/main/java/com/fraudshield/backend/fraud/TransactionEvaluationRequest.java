package com.fraudshield.backend.fraud;

import com.fraudshield.backend.domain.model.TransactionChannel;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public record TransactionEvaluationRequest(
        String transactionId,
        String cardId,
        OffsetDateTime transactionTimestamp,
        BigDecimal amount,
        String currency,
        String merchantId,
        String mcc,
        TransactionChannel channel,
        String transactionCity,
        String transactionCountry) {

    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);

    public TransactionEvaluationRequest {
        if (transactionId == null || !transactionId.matches("T[0-9]{9}")) throw new IllegalArgumentException("Invalid transactionId");
        Objects.requireNonNull(cardId, "cardId");
        Objects.requireNonNull(transactionTimestamp, "transactionTimestamp");
        if (!transactionTimestamp.getOffset().equals(IST)) throw new IllegalArgumentException("transactionTimestamp must use +05:30");
        if (amount == null || amount.signum() <= 0 || amount.compareTo(new BigDecimal("1000000")) > 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("amount must be greater than zero, at most 1000000, with at most two decimals");
        }
        if (!"INR".equals(currency)) throw new IllegalArgumentException("currency must be INR");
        Objects.requireNonNull(merchantId, "merchantId");
        if (mcc == null || !mcc.matches("[0-9]{4}")) throw new IllegalArgumentException("mcc must contain four digits");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(transactionCity, "transactionCity");
        Objects.requireNonNull(transactionCountry, "transactionCountry");
    }
}
