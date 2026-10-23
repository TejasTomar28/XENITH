package com.fraudshield.backend.fraud;

import com.fraudshield.backend.domain.model.CardStatus;
import com.fraudshield.backend.domain.model.CardType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CardEvaluationProfile(
        String cardId,
        CardStatus status,
        CardType cardType,
        LocalDate issueDate,
        LocalDate expiryDate,
        String homeCity,
        String homeCountry,
        BigDecimal dailyLimit,
        boolean internationalEnabled) {}
