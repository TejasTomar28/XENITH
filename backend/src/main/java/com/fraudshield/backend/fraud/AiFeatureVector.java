package com.fraudshield.backend.fraud;

import com.fraudshield.backend.domain.model.CardType;
import com.fraudshield.backend.domain.model.TransactionChannel;
import java.math.BigDecimal;

public record AiFeatureVector(
        CardType cardType,
        TransactionChannel channel,
        String mcc,
        String mccCategory,
        String merchantRisk,
        BigDecimal amount,
        int localHour,
        boolean international,
        boolean countryRiskHigh,
        double distanceFromHomeKm,
        int velocity10Minutes,
        int velocity24Hours,
        BigDecimal amountToAverage30DayRatio,
        boolean newMerchant,
        long cardAgeDays) {}
