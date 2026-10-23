package com.fraudshield.backend.authorization;

import com.fraudshield.backend.authorization.ai.ModelEvaluation;
import com.fraudshield.backend.authorization.dto.AuthorizationRequest;
import com.fraudshield.backend.domain.model.TransactionDecision;
import com.fraudshield.backend.domain.model.TransactionStatus;
import com.fraudshield.backend.fraud.RuleEvaluationResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record AuthorizationPersistenceCommand(
        UUID transactionId,
        AuthorizationRequest request,
        RuleEvaluationResult ruleResult,
        TransactionDecision finalDecision,
        TransactionStatus finalStatus,
        List<String> finalReasonCodes,
        ModelEvaluation modelEvaluation,
        UUID alertId,
        String alertPriority,
        OffsetDateTime alertDueAt,
        OffsetDateTime processedAt) {}
