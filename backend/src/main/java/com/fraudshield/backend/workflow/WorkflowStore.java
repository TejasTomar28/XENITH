package com.fraudshield.backend.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.backend.domain.model.AlertPriority;
import com.fraudshield.backend.fraud.PersistedRuleConfigurationProvider;
import com.fraudshield.backend.fraud.RuleConfiguration;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkflowStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PersistedRuleConfigurationProvider configurations;

    public WorkflowStore(JdbcTemplate jdbc, ObjectMapper json, PersistedRuleConfigurationProvider configurations) {
        this.jdbc = jdbc; this.json = json; this.configurations = configurations;
    }

    public void reason(UUID transactionId, String code) {
        Integer sequence = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sequence_no),0)+1 FROM transaction_reason_codes WHERE transaction_id=?",
                Integer.class, transactionId);
        jdbc.update("INSERT INTO transaction_reason_codes(transaction_id,sequence_no,reason_code) VALUES (?,?,?) ON CONFLICT (transaction_id,reason_code) DO NOTHING",
                transactionId, sequence, code);
    }

    public UUID createAlert(UUID transactionId, OffsetDateTime createdAt) {
        List<AlertFacts> facts = jdbc.query("""
                SELECT t.amount, COALESCE(p.fraud_score,0),
                  EXISTS(SELECT 1 FROM transaction_reason_codes r WHERE r.transaction_id=t.id AND r.reason_code IN ('IMPOSSIBLE_TRAVEL','MULTIPLE_RED_FLAGS','STEP_UP_DENIED')) high_reason
                FROM transactions t LEFT JOIN model_predictions p ON p.transaction_id=t.id WHERE t.id=?
                """, (rs,n) -> new AlertFacts(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBoolean(3)), transactionId);
        if (facts.isEmpty()) throw new IllegalStateException("Transaction does not exist");
        RuleConfiguration config = configurations.current();
        AlertFacts fact = facts.get(0);
        AlertPriority priority = fact.amount().compareTo(config.alertHighAmount()) >= 0 || fact.highReason()
                || fact.score().compareTo(config.aiBlockThreshold()) >= 0 ? AlertPriority.HIGH : AlertPriority.MEDIUM;
        UUID id = UUID.randomUUID();
        OffsetDateTime due = createdAt.plusMinutes(priority == AlertPriority.HIGH
                ? config.alertHighSlaMinutes() : config.alertMediumSlaMinutes());
        int inserted = jdbc.update("""
                INSERT INTO fraud_alerts(id,transaction_id,priority,status,sla_due_at,created_at,version)
                VALUES (?, ?, ?, 'OPEN', ?, ?, 0) ON CONFLICT (transaction_id) DO NOTHING
                """, id, transactionId, priority.name(), due, createdAt);
        if (inserted == 1) {
            String txnId = jdbc.queryForObject("SELECT txn_id FROM transactions WHERE id=?", String.class, transactionId);
            audit("ALERT", id.toString(), "system", "SYSTEM", createdAt, "ALERT_CREATED", null, "OPEN", txnId, null, null);
            return id;
        }
        return jdbc.queryForObject("SELECT id FROM fraud_alerts WHERE transaction_id=?", UUID.class, transactionId);
    }

    public void audit(String entity, String entityId, String actor, String role, OffsetDateTime at, String action,
                      String from, String to, String comment, Object before, Object after) {
        jdbc.update("""
                INSERT INTO audit_events(entity_type,entity_id,actor_id,actor_role,occurred_at,action,from_status,to_status,comment,before_value,after_value,resettable)
                VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,false)
                """, entity, entityId, actor, role, at, action, from, to, comment, encode(before), encode(after));
    }

    public void blockCard(String cardId, String actor, String role, OffsetDateTime at, String comment) {
        String status = jdbc.queryForObject("SELECT card_status FROM cards WHERE card_id=? FOR UPDATE", String.class, cardId);
        if ("ACTIVE".equals(status)) {
            jdbc.update("UPDATE cards SET card_status='BLOCKED',version=version+1 WHERE card_id=?", cardId);
            audit("CARD", cardId, actor, role, at, "CARD_BLOCKED", "ACTIVE", "BLOCKED", comment, null, null);
        }
    }

    private String encode(Object value) {
        if (value == null) return null;
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Cannot encode audit value", exception); }
    }
    private record AlertFacts(BigDecimal amount, BigDecimal score, boolean highReason) {}
}
