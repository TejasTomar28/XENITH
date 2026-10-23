package com.fraudshield.backend.fraud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PersistedRuleConfigurationProvider {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PersistedRuleConfigurationProvider(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public RuleConfiguration current() {
        return jdbc.query("SELECT source_sha256, configuration::text FROM rule_configuration_versions WHERE active",
                resultSet -> {
                    if (!resultSet.next()) throw new MissingRuleConfigurationException("No active fraud-rule configuration is available");
                    String version = resultSet.getString(1);
                    try {
                        JsonNode root = objectMapper.readTree(resultSet.getString(2));
                        RuleConfiguration configuration = decode(version, root);
                        if (resultSet.next()) throw new MissingRuleConfigurationException("Multiple active fraud-rule configurations exist");
                        return configuration;
                    } catch (MissingRuleConfigurationException exception) {
                        throw exception;
                    } catch (Exception exception) {
                        throw new MissingRuleConfigurationException("Active fraud-rule configuration is malformed", exception);
                    }
                });
    }

    RuleConfiguration decode(String version, JsonNode root) {
        Set<String> highRiskMccs = new LinkedHashSet<>();
        JsonNode mccNode = required(root, "high_risk_mccs");
        if (!mccNode.isArray() || mccNode.isEmpty()) throw missing("high_risk_mccs must be a non-empty array");
        mccNode.forEach(value -> highRiskMccs.add(value.asText()));
        return new RuleConfiguration(
                version,
                integer(root, "velocity_window_minutes"),
                integer(root, "velocity_24h_window_hours"),
                integer(root, "velocity_burst_count"),
                integer(root, "high_velocity_min_count"),
                integer(root, "high_velocity_max_count"),
                integer(root, "amount_average_lookback_days"),
                decimal(root, "amount_spike_multiplier"),
                decimal(root, "amount_spike_minimum"),
                decimal(root, "amount_spike_no_history_minimum"),
                decimal(root, "impossible_travel_threshold_kmph").doubleValue(),
                integer(root, "impossible_travel_lookback_hours"),
                integer(root, "high_velocity_points"),
                integer(root, "amount_spike_points"),
                integer(root, "high_risk_geo_points"),
                integer(root, "high_risk_mcc_points"),
                integer(root, "odd_hour_points"),
                integer(root, "new_merchant_points"),
                highRiskMccs,
                integer(root, "odd_hour_start_inclusive"),
                integer(root, "odd_hour_end_inclusive"),
                decimal(root, "odd_hour_minimum"),
                integer(root, "new_merchant_lookback_days"),
                decimal(root, "new_merchant_minimum"),
                integer(root, "multiple_red_flags_count"),
                decimal(root, "alert_high_amount"),
                integer(root, "alert_high_sla_minutes"),
                integer(root, "alert_medium_sla_minutes"),
                decimal(root, "ai_step_up_threshold"),
                decimal(root, "ai_block_threshold"));
    }

    private int integer(JsonNode root, String key) {
        JsonNode value = required(root, key);
        if (!value.isIntegralNumber()) throw missing(key + " must be an integer");
        return value.intValue();
    }

    private BigDecimal decimal(JsonNode root, String key) {
        JsonNode value = required(root, key);
        if (!value.isNumber()) throw missing(key + " must be numeric");
        return value.decimalValue();
    }

    private JsonNode required(JsonNode root, String key) {
        JsonNode value = root == null ? null : root.get(key);
        if (value == null || value.isNull()) throw missing("Missing configuration key: " + key);
        return value;
    }

    private MissingRuleConfigurationException missing(String message) {
        return new MissingRuleConfigurationException(message);
    }
}
