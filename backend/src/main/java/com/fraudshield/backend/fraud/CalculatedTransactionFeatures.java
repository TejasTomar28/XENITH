package com.fraudshield.backend.fraud;

import java.math.BigDecimal;

public record CalculatedTransactionFeatures(
        Integer velocity10Minutes,
        BigDecimal approvedToday,
        BigDecimal averageAmount30Days,
        Double speedKilometresPerHour,
        Boolean newMerchant,
        AiFeatureVector aiFeatures) {

    public static CalculatedTransactionFeatures empty() {
        return new CalculatedTransactionFeatures(null, null, null, null, null, null);
    }
}
