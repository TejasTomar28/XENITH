package com.fraudshield.backend.authorization;

import com.fraudshield.backend.authorization.dto.AuthorizationRequest;
import com.fraudshield.backend.authorization.dto.AuthorizationResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

record StoredAuthorization(
        String cardId,
        OffsetDateTime transactionTimestamp,
        BigDecimal amount,
        String currency,
        String merchantId,
        String mcc,
        String channel,
        String transactionCity,
        String transactionCountry,
        AuthorizationResponse response) {

    boolean matches(AuthorizationRequest request) {
        return cardId.equals(request.cardId())
                && transactionTimestamp.toInstant().equals(request.transactionTimestamp().toInstant())
                && amount.compareTo(request.amount()) == 0
                && currency.trim().equals(request.currency())
                && merchantId.equals(request.merchantId())
                && mcc.trim().equals(request.mcc())
                && channel.equals(request.channel().name())
                && transactionCity.equals(request.transactionCity())
                && transactionCountry.equals(request.transactionCountry());
    }
}
