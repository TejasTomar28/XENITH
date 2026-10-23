package com.fraudshield.backend.authorization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.backend.authorization.ai.ModelStatus;
import com.fraudshield.backend.authorization.ai.TopFactor;
import com.fraudshield.backend.authorization.dto.AuthorizationResponse;
import com.fraudshield.backend.domain.model.TransactionDecision;
import com.fraudshield.backend.domain.model.TransactionStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AuthorizationRecordStore {
    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    AuthorizationRecordStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    Optional<StoredAuthorization> find(String transactionId) {
        List<StoredAuthorization> records = jdbc.query("""
                SELECT t.card_id,t.txn_ts,t.amount,t.currency,t.merchant_id,t.mcc,t.channel,t.txn_city,t.txn_country,
                       t.original_decision,t.original_status,t.processed_at,f.rule_risk_score,
                       p.fraud_score,p.top_factors::text,p.model_status,a.id AS alert_id
                  FROM transactions t
             LEFT JOIN transaction_features f ON f.transaction_id=t.id
             LEFT JOIN model_predictions p ON p.transaction_id=t.id
             LEFT JOIN fraud_alerts a ON a.transaction_id=t.id
                 WHERE t.txn_id=?
                """, (rs, row) -> {
            List<String> reasons = jdbc.queryForList("""
                    SELECT reason_code FROM transaction_reason_codes
                     WHERE transaction_id=(SELECT id FROM transactions WHERE txn_id=?) ORDER BY sequence_no
                    """, String.class, transactionId);
            List<TopFactor> factors;
            try {
                String json = rs.getString("top_factors");
                factors = json == null ? List.of() : objectMapper.readValue(json, new TypeReference<>() {});
            } catch (Exception exception) {
                throw new IllegalStateException("Stored model factors are malformed", exception);
            }
            OffsetDateTime processedAt = rs.getObject("processed_at", OffsetDateTime.class).withOffsetSameInstant(IST);
            String modelStatus = rs.getString("model_status");
            AuthorizationResponse response = new AuthorizationResponse(
                    transactionId,
                    TransactionDecision.valueOf(rs.getString("original_decision")),
                    reasons,
                    (Integer) rs.getObject("rule_risk_score"),
                    TransactionStatus.valueOf(rs.getString("original_status")),
                    rs.getObject("alert_id", java.util.UUID.class),
                    processedAt,
                    rs.getBigDecimal("fraud_score"),
                    factors,
                    modelStatus == null ? ModelStatus.NOT_EVALUATED : ModelStatus.valueOf(modelStatus));
            return new StoredAuthorization(
                    rs.getString("card_id"), rs.getObject("txn_ts", OffsetDateTime.class), rs.getBigDecimal("amount"),
                    rs.getString("currency"), rs.getString("merchant_id"), rs.getString("mcc"), rs.getString("channel"),
                    rs.getString("txn_city"), rs.getString("txn_country"), response);
        }, transactionId);
        return records.stream().findFirst();
    }

    void persist(AuthorizationPersistenceCommand command) {
        var request = command.request();
        var rule = command.ruleResult();
        var features = rule.features();
        String featureJson = json(features);
        String aiFeatureJson = json(features.aiFeatures() == null ? objectMapper.createObjectNode() : features.aiFeatures());
        String factorsJson = json(command.modelEvaluation().topFactors());

        jdbc.update("""
                INSERT INTO transactions(id,txn_id,card_id,txn_ts,amount,currency,merchant_id,mcc,channel,txn_city,txn_country,
                    status,rule_decision,original_decision,original_status,transaction_source,resettable,processed_at,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'LIVE',false,?,0)
                """, command.transactionId(), request.transactionId(), request.cardId(), request.transactionTimestamp(),
                request.amount(), request.currency(), request.merchantId(), request.mcc(), request.channel().name(),
                request.transactionCity(), request.transactionCountry(), command.finalStatus().name(), rule.decision().name(),
                command.finalDecision().name(), command.finalStatus().name(), command.processedAt());

        jdbc.update("""
                INSERT INTO transaction_features(transaction_id,velocity_10m,velocity_24h,approved_today,avg_amount_30d,
                    speed_kmph,new_merchant,feature_snapshot,configuration_version,rule_risk_score)
                VALUES (?,?,?,?,?,?,?,?::jsonb,?,?)
                """, command.transactionId(), features.velocity10Minutes(),
                features.aiFeatures() == null ? null : features.aiFeatures().velocity24Hours(), features.approvedToday(),
                features.averageAmount30Days(), features.speedKilometresPerHour(), features.newMerchant(), featureJson,
                rule.configurationVersion(), rule.ruleRiskScore());

        for (int index = 0; index < command.finalReasonCodes().size(); index++) {
            jdbc.update("INSERT INTO transaction_reason_codes(transaction_id,sequence_no,reason_code) VALUES (?,?,?)",
                    command.transactionId(), index + 1, command.finalReasonCodes().get(index));
        }

        jdbc.update("""
                INSERT INTO model_predictions(id,transaction_id,model_version,model_status,fraud_score,top_factors,
                    feature_snapshot,created_at)
                VALUES (?,?,?,?,?,?::jsonb,?::jsonb,?)
                """, java.util.UUID.randomUUID(), command.transactionId(), command.modelEvaluation().modelVersion(),
                command.modelEvaluation().status().name(), command.modelEvaluation().fraudScore(), factorsJson, aiFeatureJson,
                command.processedAt());

        jdbc.update("""
                INSERT INTO audit_events(entity_type,entity_id,actor_id,actor_role,occurred_at,action,from_status,to_status,
                    after_value,resettable)
                VALUES ('TRANSACTION',?,'transaction-gateway','GATEWAY',?,'AUTHORIZATION_DECIDED','RECEIVED',?,?::jsonb,false)
                """, request.transactionId(), command.processedAt(), command.finalStatus().name(), json(java.util.Map.of(
                        "rule_decision", rule.decision().name(),
                        "decision", command.finalDecision().name(),
                        "reason_codes", command.finalReasonCodes(),
                        "model_status", command.modelEvaluation().status().name())));

        if (command.alertId() != null) {
            jdbc.update("""
                    INSERT INTO fraud_alerts(id,transaction_id,priority,status,sla_due_at,created_at,version)
                    VALUES (?,? ,?,'OPEN',?,?,0)
                    """, command.alertId(), command.transactionId(), command.alertPriority(), command.alertDueAt(),
                    command.processedAt());
            jdbc.update("""
                    INSERT INTO audit_events(entity_type,entity_id,actor_id,actor_role,occurred_at,action,to_status,resettable)
                    VALUES ('ALERT',?,'transaction-gateway','GATEWAY',?,'ALERT_CREATED','OPEN',false)
                    """, command.alertId().toString(), command.processedAt());
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize authorization evidence", exception);
        }
    }
}
