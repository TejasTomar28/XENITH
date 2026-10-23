package com.fraudshield.backend.fraud;

import com.fraudshield.backend.domain.model.TransactionDecision;
import java.util.List;

public record RuleEvaluationResult(
        TransactionDecision decision,
        Integer ruleRiskScore,
        List<ReasonCode> reasonCodes,
        CalculatedTransactionFeatures features,
        String configurationVersion) {
    public RuleEvaluationResult {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
