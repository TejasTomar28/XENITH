package com.fraudshield.backend.fraud;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fraudshield.backend.domain.model.CardType;
import com.fraudshield.backend.domain.model.TransactionChannel;
import java.math.BigDecimal;

public record AiFeatureVector(
        @JsonProperty("card_type")
        CardType cardType,
        TransactionChannel channel,
        String mcc,
        @JsonProperty("mcc_category")
        String mccCategory,
        @JsonProperty("merchant_risk")
        String merchantRisk,
        BigDecimal amount,
        @JsonProperty("local_hour")
        int localHour,
        @JsonProperty("is_international")
        boolean international,
        @JsonProperty("country_risk_high")
        boolean countryRiskHigh,
        @JsonProperty("distance_from_home_km")
        double distanceFromHomeKm,
        @JsonProperty("velocity_10m")
        int velocity10Minutes,
        @JsonProperty("velocity_24h")
        int velocity24Hours,
        @JsonProperty("amount_to_avg30_ratio")
        BigDecimal amountToAverage30DayRatio,
        @JsonProperty("new_merchant_flag")
        boolean newMerchant,
        @JsonProperty("card_age_days")
        long cardAgeDays) {}
