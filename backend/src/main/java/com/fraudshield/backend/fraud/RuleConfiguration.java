package com.fraudshield.backend.fraud;

import java.math.BigDecimal;
import java.util.Set;

public record RuleConfiguration(
        String version,
        int velocityWindowMinutes,
        int velocity24HourWindowHours,
        int velocityBurstCount,
        int highVelocityMinimum,
        int highVelocityMaximum,
        int amountAverageLookbackDays,
        BigDecimal amountSpikeMultiplier,
        BigDecimal amountSpikeMinimum,
        BigDecimal amountSpikeNoHistoryMinimum,
        double impossibleTravelThresholdKmph,
        int impossibleTravelLookbackHours,
        int highVelocityPoints,
        int amountSpikePoints,
        int highRiskGeoPoints,
        int highRiskMccPoints,
        int oddHourPoints,
        int newMerchantPoints,
        Set<String> highRiskMccs,
        int oddHourStartInclusive,
        int oddHourEndInclusive,
        BigDecimal oddHourMinimum,
        int newMerchantLookbackDays,
        BigDecimal newMerchantMinimum,
        int multipleRedFlagsCount) {

    public RuleConfiguration {
        highRiskMccs = Set.copyOf(highRiskMccs);
        if (version == null || version.isBlank()) throw new IllegalArgumentException("Configuration version is required");
        if (velocityWindowMinutes <= 0 || velocity24HourWindowHours <= 0 || velocityBurstCount <= 0
                || highVelocityMinimum < 0 || highVelocityMaximum < highVelocityMinimum
                || amountAverageLookbackDays <= 0 || impossibleTravelThresholdKmph < 0
                || impossibleTravelLookbackHours <= 0 || newMerchantLookbackDays <= 0
                || multipleRedFlagsCount <= 0) throw new IllegalArgumentException("Configuration windows and thresholds are invalid");
        if (amountSpikeMultiplier.signum() <= 0 || amountSpikeMinimum.signum() < 0
                || amountSpikeNoHistoryMinimum.signum() < 0 || oddHourMinimum.signum() < 0
                || newMerchantMinimum.signum() < 0) throw new IllegalArgumentException("Configuration amount thresholds are invalid");
        if (oddHourStartInclusive < 0 || oddHourStartInclusive > 23 || oddHourEndInclusive < 0 || oddHourEndInclusive > 23) {
            throw new IllegalArgumentException("Odd-hour range is invalid");
        }
        if (highRiskMccs.stream().anyMatch(value -> !value.matches("[0-9]{4}"))) {
            throw new IllegalArgumentException("High-risk MCC values must contain four digits");
        }
        if (highVelocityPoints < 0 || amountSpikePoints < 0 || highRiskGeoPoints < 0 || highRiskMccPoints < 0
                || oddHourPoints < 0 || newMerchantPoints < 0) throw new IllegalArgumentException("Rule points cannot be negative");
    }
}
