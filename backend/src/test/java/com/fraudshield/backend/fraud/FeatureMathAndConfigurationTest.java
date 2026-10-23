package com.fraudshield.backend.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class FeatureMathAndConfigurationTest {

    @Test
    void haversineMatchesOfficialMumbaiDelhiExample() {
        double distance = TransactionFeatureCalculator.distanceKm(19.0760, 72.8777, 28.6139, 77.2090);
        assertThat(distance).isCloseTo(1148.09, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void sameCityProducesZeroSpeed() {
        OffsetDateTime previous = OffsetDateTime.parse("2026-03-04T13:05:00+05:30");
        OffsetDateTime current = OffsetDateTime.parse("2026-03-04T14:05:00+05:30");
        assertThat(TransactionFeatureCalculator.speedKmph(0, previous, current)).isZero();
    }

    @Test
    void sameTimestampDifferentCitiesUsesDocumentedSentinel() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-03-04T14:05:00+05:30");
        assertThat(TransactionFeatureCalculator.speedKmph(100, timestamp, timestamp)).isEqualTo(99999.99);
    }

    @Test
    void completePersistedConfigurationDecodesWithoutDefaults() throws Exception {
        PersistedRuleConfigurationProvider provider = new PersistedRuleConfigurationProvider(null, new ObjectMapper());
        RuleConfiguration configuration = provider.decode("v1", new ObjectMapper().readTree(configurationJson()));
        assertThat(configuration.velocityBurstCount()).isEqualTo(5);
        assertThat(configuration.highRiskMccs()).containsExactlyInAnyOrder("4829", "5967", "6051", "7995");
        assertThat(configuration.amountSpikeMultiplier()).isEqualByComparingTo("5");
    }

    @Test
    void incompletePersistedConfigurationFailsClearly() throws Exception {
        PersistedRuleConfigurationProvider provider = new PersistedRuleConfigurationProvider(null, new ObjectMapper());
        assertThatThrownBy(() -> provider.decode("v1", new ObjectMapper().readTree("{\"velocity_window_minutes\":10}")))
                .isInstanceOf(MissingRuleConfigurationException.class)
                .hasMessageContaining("high_risk_mccs");
    }

    static String configurationJson() {
        return """
                {
                  "velocity_window_minutes": 10,
                  "velocity_24h_window_hours": 24,
                  "velocity_burst_count": 5,
                  "high_velocity_min_count": 3,
                  "high_velocity_max_count": 4,
                  "amount_average_lookback_days": 30,
                  "amount_spike_multiplier": 5,
                  "amount_spike_minimum": 10000,
                  "amount_spike_no_history_minimum": 50000,
                  "impossible_travel_threshold_kmph": 800,
                  "impossible_travel_lookback_hours": 6,
                  "high_velocity_points": 25,
                  "amount_spike_points": 30,
                  "high_risk_geo_points": 25,
                  "high_risk_mcc_points": 15,
                  "odd_hour_points": 15,
                  "new_merchant_points": 20,
                  "high_risk_mccs": ["4829", "5967", "6051", "7995"],
                  "odd_hour_start_inclusive": 0,
                  "odd_hour_end_inclusive": 4,
                  "odd_hour_minimum": 20000,
                  "new_merchant_lookback_days": 90,
                  "new_merchant_minimum": 25000,
                  "multiple_red_flags_count": 3,
                  "alert_high_amount": 50000,
                  "alert_high_sla_minutes": 30,
                  "alert_medium_sla_minutes": 240,
                  "ai_step_up_threshold": 0.70,
                  "ai_block_threshold": 0.90
                }
                """;
    }
}
