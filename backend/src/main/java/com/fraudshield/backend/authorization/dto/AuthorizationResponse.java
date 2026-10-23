package com.fraudshield.backend.authorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fraudshield.backend.authorization.ai.ModelStatus;
import com.fraudshield.backend.authorization.ai.TopFactor;
import com.fraudshield.backend.domain.model.TransactionDecision;
import com.fraudshield.backend.domain.model.TransactionStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AuthorizationResponse(
        @JsonProperty("txn_id") String transactionId,
        TransactionDecision decision,
        @JsonProperty("reason_codes") List<String> reasonCodes,
        @JsonProperty("rule_risk_score") Integer ruleRiskScore,
        TransactionStatus status,
        @JsonProperty("alert_id") UUID alertId,
        @JsonProperty("processed_at") OffsetDateTime processedAt,
        @JsonProperty("fraud_score") BigDecimal fraudScore,
        @JsonProperty("top_factors") List<TopFactor> topFactors,
        @JsonProperty("model_status") ModelStatus modelStatus) {

    public AuthorizationResponse {
        reasonCodes = List.copyOf(reasonCodes);
        topFactors = List.copyOf(topFactors);
    }
}
