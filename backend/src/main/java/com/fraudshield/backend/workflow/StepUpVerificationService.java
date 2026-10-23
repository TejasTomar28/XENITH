package com.fraudshield.backend.workflow;

import com.fraudshield.backend.authorization.SemanticValidationException;
import com.fraudshield.backend.authorization.StepUpWorkflowHandoff;
import com.fraudshield.backend.common.exception.ConflictException;
import com.fraudshield.backend.common.exception.ResourceNotFoundException;
import com.fraudshield.backend.fraud.PersistedRuleConfigurationProvider;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class StepUpVerificationService implements StepUpWorkflowHandoff {
    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5,30);
    private final JdbcTemplate jdbc; private final PasswordEncoder encoder; private final OtpNotification notification;
    private final PersistedRuleConfigurationProvider configs; private final WorkflowStore store; private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public StepUpVerificationService(JdbcTemplate jdbc, PasswordEncoder encoder, OtpNotification notification,
            PersistedRuleConfigurationProvider configs, WorkflowStore store, Clock clock) {
        this.jdbc=jdbc; this.encoder=encoder; this.notification=notification; this.configs=configs; this.store=store; this.clock=clock;
    }

    @Override
    public void transactionPending(UUID transactionId, String externalTransactionId) {
        String customerId = jdbc.queryForObject("SELECT c.customer_id FROM transactions t JOIN cards c ON c.card_id=t.card_id WHERE t.id=?", String.class, transactionId);
        String otp = "%06d".formatted(random.nextInt(1_000_000));
        UUID stepUpId = UUID.randomUUID();
        OffsetDateTime now = now();
        jdbc.update("""
                INSERT INTO step_up_requests(id,transaction_id,customer_id,otp_hash,expires_at,attempt_count,status,created_at,version)
                VALUES (?,?,?,?,?,0,'PENDING',?,0)
                """, stepUpId, transactionId, customerId, encoder.encode(otp), now.plusMinutes(configs.current().stepUpExpiryMinutes()), now);
        store.audit("STEP_UP", stepUpId.toString(), "transaction-gateway", "GATEWAY", now,
                "STEP_UP_CREATED", null, "PENDING", externalTransactionId, null, null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { notification.send(stepUpId, externalTransactionId, otp); }
        });
    }

    @Transactional(noRollbackFor = {SemanticValidationException.class, ConflictException.class})
    public StepUpView respond(UUID stepUpId, UUID userId, String response, String otp) {
        StepUpRow row = lock(stepUpId);
        if (!userId.equals(row.userId())) throw new AccessDeniedException("Step-up belongs to another cardholder");
        if (!"PENDING".equals(row.status())) throw new ConflictException("Step-up request is already resolved");
        OffsetDateTime now = now();
        if (!now.isBefore(row.expiresAt())) {
            expire(row, now);
            throw new ConflictException("Step-up request has expired");
        }
        if ("NOT_ME".equals(response)) {
            jdbc.update("UPDATE step_up_requests SET status='DENIED',resolved_at=?,version=version+1 WHERE id=?", now, stepUpId);
            jdbc.update("UPDATE transactions SET status='BLOCKED',version=version+1 WHERE id=?", row.transactionId());
            store.reason(row.transactionId(), "STEP_UP_DENIED");
            store.blockCard(row.cardId(), userId.toString(), "CARDHOLDER", now, "Cardholder denied step-up transaction");
            store.createAlert(row.transactionId(), now);
            store.audit("STEP_UP", stepUpId.toString(), userId.toString(), "CARDHOLDER", now, "STEP_UP_DENIED", "PENDING", "DENIED", null, null, null);
            store.audit("TRANSACTION", row.externalTransactionId(), userId.toString(), "CARDHOLDER", now, "STEP_UP_DENIED", "STEP_UP_PENDING", "BLOCKED", null, null, null);
            return view(stepUpId);
        }
        if (!"CONFIRM".equals(response)) throw new SemanticValidationException("response", "Must be CONFIRM or NOT_ME");
        if (otp == null || !otp.matches("[0-9]{6}") || !encoder.matches(otp, row.otpHash())) {
            jdbc.update("UPDATE step_up_requests SET attempt_count=attempt_count+1,version=version+1 WHERE id=?", stepUpId);
            store.audit("STEP_UP", stepUpId.toString(), userId.toString(), "CARDHOLDER", now, "OTP_FAILED", "PENDING", "PENDING", "Invalid OTP attempt", null, null);
            throw new SemanticValidationException("otp", "OTP is invalid");
        }
        jdbc.update("UPDATE step_up_requests SET status='CONFIRMED',resolved_at=?,attempt_count=attempt_count+1,version=version+1 WHERE id=?", now, stepUpId);
        jdbc.update("UPDATE transactions SET status='APPROVED',version=version+1 WHERE id=?", row.transactionId());
        store.audit("STEP_UP", stepUpId.toString(), userId.toString(), "CARDHOLDER", now, "STEP_UP_CONFIRMED", "PENDING", "CONFIRMED", null, null, null);
        store.audit("TRANSACTION", row.externalTransactionId(), userId.toString(), "CARDHOLDER", now, "STEP_UP_CONFIRMED", "STEP_UP_PENDING", "APPROVED", null, null, null);
        return view(stepUpId);
    }

    @Transactional
    public boolean expireOne(UUID stepUpId) {
        StepUpRow row = lock(stepUpId);
        OffsetDateTime now = now();
        if (!"PENDING".equals(row.status()) || now.isBefore(row.expiresAt())) return false;
        expire(row, now); return true;
    }

    public List<UUID> expiredIds() {
        return jdbc.queryForList("SELECT id FROM step_up_requests WHERE status='PENDING' AND expires_at<=? ORDER BY expires_at", UUID.class, now());
    }

    private void expire(StepUpRow row, OffsetDateTime now) {
        jdbc.update("UPDATE step_up_requests SET status='EXPIRED',resolved_at=?,version=version+1 WHERE id=?", now, row.id());
        jdbc.update("UPDATE transactions SET status='BLOCKED',version=version+1 WHERE id=?", row.transactionId());
        store.reason(row.transactionId(), "STEP_UP_TIMEOUT");
        store.createAlert(row.transactionId(), now);
        store.audit("STEP_UP", row.id().toString(), "system", "SYSTEM", now, "STEP_UP_EXPIRED", "PENDING", "EXPIRED", null, null, null);
        store.audit("TRANSACTION", row.externalTransactionId(), "system", "SYSTEM", now, "STEP_UP_TIMEOUT", "STEP_UP_PENDING", "BLOCKED", null, null, null);
    }

    private StepUpRow lock(UUID id) {
        List<StepUpRow> rows=jdbc.query("""
                SELECT s.id,s.transaction_id,s.otp_hash,s.expires_at,s.status,t.txn_id,t.card_id,c.user_id
                FROM step_up_requests s JOIN transactions t ON t.id=s.transaction_id
                JOIN cards ca ON ca.card_id=t.card_id JOIN customers c ON c.customer_id=ca.customer_id
                WHERE s.id=? FOR UPDATE OF s,t,ca
                """,(rs,n)->new StepUpRow((UUID)rs.getObject(1),(UUID)rs.getObject(2),rs.getString(3),rs.getObject(4,OffsetDateTime.class),rs.getString(5),rs.getString(6),rs.getString(7),(UUID)rs.getObject(8)),id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Step-up request not found"); return rows.get(0);
    }
    public StepUpView view(UUID id) { return jdbc.queryForObject("SELECT id,status,expires_at,resolved_at,attempt_count FROM step_up_requests WHERE id=?",(rs,n)->new StepUpView((UUID)rs.getObject(1),rs.getString(2),rs.getObject(3,OffsetDateTime.class),rs.getObject(4,OffsetDateTime.class),rs.getInt(5)),id); }
    private OffsetDateTime now(){ return OffsetDateTime.now(clock).withOffsetSameInstant(IST); }
    private record StepUpRow(UUID id,UUID transactionId,String otpHash,OffsetDateTime expiresAt,String status,String externalTransactionId,String cardId,UUID userId){}
    public record StepUpView(UUID stepUpId,String status,OffsetDateTime expiresAt,OffsetDateTime resolvedAt,int attemptCount){}
}
