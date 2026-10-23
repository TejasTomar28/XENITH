package com.fraudshield.backend.cardholder;

import com.fraudshield.backend.common.exception.ConflictException;
import com.fraudshield.backend.common.exception.ResourceNotFoundException;
import com.fraudshield.backend.workflow.WorkflowStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardholderService {
    private static final ZoneOffset IST=ZoneOffset.ofHoursMinutes(5,30);
    private final JdbcTemplate jdbc; private final WorkflowStore store; private final Clock clock;
    public CardholderService(JdbcTemplate jdbc,WorkflowStore store,Clock clock){this.jdbc=jdbc;this.store=store;this.clock=clock;}

    public List<CardView> cards(UUID userId){return jdbc.query("""
            SELECT ca.card_id,ca.last_four,ca.card_type,ca.card_status,ca.expiry_date,ca.daily_limit,ca.international_enabled
            FROM cards ca JOIN customers c ON c.customer_id=ca.customer_id WHERE c.user_id=? ORDER BY ca.card_id
            """,(r,n)->new CardView(r.getString(1),"XXXXXXXXXXXX"+r.getString(2).trim(),r.getString(3),r.getString(4),r.getObject(5,LocalDate.class),r.getBigDecimal(6),r.getBoolean(7)),userId);}

    public Page<TransactionView> transactions(UUID userId,int page,int size){
        long total=jdbc.queryForObject("SELECT count(*) FROM transactions t JOIN cards ca ON ca.card_id=t.card_id JOIN customers c ON c.customer_id=ca.customer_id WHERE c.user_id=?",Long.class,userId);
        List<TransactionView> items=jdbc.query("""
            SELECT t.txn_id,t.txn_ts,t.amount,t.currency,t.merchant_id,t.channel,t.txn_city,t.txn_country,t.status
            FROM transactions t JOIN cards ca ON ca.card_id=t.card_id JOIN customers c ON c.customer_id=ca.customer_id
            WHERE c.user_id=? ORDER BY t.txn_ts DESC,t.txn_id DESC LIMIT ? OFFSET ?
            """,(r,n)->new TransactionView(r.getString(1),r.getObject(2,OffsetDateTime.class),r.getBigDecimal(3),r.getString(4).trim(),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9)),userId,size,page*size);
        return new Page<>(items,page,size,total);
    }

    public TransactionView transaction(UUID userId,String txnId){
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM transactions WHERE txn_id=?)",Boolean.class,txnId))) throw new ResourceNotFoundException("Transaction not found");
        List<TransactionView> found=jdbc.query("""
            SELECT t.txn_id,t.txn_ts,t.amount,t.currency,t.merchant_id,t.channel,t.txn_city,t.txn_country,t.status
            FROM transactions t JOIN cards ca ON ca.card_id=t.card_id JOIN customers c ON c.customer_id=ca.customer_id
            WHERE t.txn_id=? AND c.user_id=?
            """,(r,n)->new TransactionView(r.getString(1),r.getObject(2,OffsetDateTime.class),r.getBigDecimal(3),r.getString(4).trim(),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9)),txnId,userId);
        if(found.isEmpty()) throw new AccessDeniedException("Transaction belongs to another cardholder"); return found.get(0);
    }

    public List<PendingStepUp> stepUps(UUID userId){return jdbc.query("""
            SELECT s.id,t.txn_id,t.amount,t.currency,t.merchant_id,s.expires_at,s.created_at
            FROM step_up_requests s JOIN transactions t ON t.id=s.transaction_id JOIN customers c ON c.customer_id=s.customer_id
            WHERE c.user_id=? AND s.status='PENDING' ORDER BY s.created_at DESC
            """,(r,n)->new PendingStepUp((UUID)r.getObject(1),r.getString(2),r.getBigDecimal(3),r.getString(4).trim(),r.getString(5),r.getObject(6,OffsetDateTime.class),r.getObject(7,OffsetDateTime.class)),userId);}

    @Transactional public CardView block(UUID userId,String cardId){
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cards WHERE card_id=?)",Boolean.class,cardId))) throw new ResourceNotFoundException("Card not found");
        UUID owner=jdbc.queryForObject("SELECT c.user_id FROM cards ca JOIN customers c ON c.customer_id=ca.customer_id WHERE ca.card_id=? FOR UPDATE OF ca",UUID.class,cardId);
        if(!userId.equals(owner)) throw new AccessDeniedException("Card belongs to another cardholder");
        String status=jdbc.queryForObject("SELECT card_status FROM cards WHERE card_id=?",String.class,cardId);
        if("EXPIRED".equals(status)) throw new ConflictException("Expired card cannot be blocked");
        store.blockCard(cardId,userId.toString(),"CARDHOLDER",OffsetDateTime.now(clock).withOffsetSameInstant(IST),"Cardholder requested card block");
        return cards(userId).stream().filter(c->c.cardId().equals(cardId)).findFirst().orElseThrow();
    }
    public record CardView(String cardId,String maskedCardNumber,String cardType,String status,LocalDate expiryDate,BigDecimal dailyLimit,boolean internationalEnabled){}
    public record TransactionView(String transactionId,OffsetDateTime transactionTimestamp,BigDecimal amount,String currency,String merchantId,String channel,String transactionCity,String transactionCountry,String status){}
    public record PendingStepUp(UUID stepUpId,String transactionId,BigDecimal amount,String currency,String merchantId,OffsetDateTime expiresAt,OffsetDateTime createdAt){}
    public record Page<T>(List<T> items,int page,int size,long total){}
}
