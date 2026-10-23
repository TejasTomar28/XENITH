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

    public Page<CardView> cards(UUID userId,int page,int size){
        long total=jdbc.queryForObject("SELECT count(*) FROM cards ca JOIN customers c ON c.customer_id=ca.customer_id WHERE c.user_id=?",Long.class,userId);
        List<CardView> items=jdbc.query("""
            SELECT ca.card_id,ca.last_four,ca.card_type,ca.card_status,ca.expiry_date,ca.daily_limit,ca.international_enabled
            FROM cards ca JOIN customers c ON c.customer_id=ca.customer_id WHERE c.user_id=? ORDER BY ca.card_id LIMIT ? OFFSET ?
            """,(r,n)->new CardView(r.getString(1),"XXXXXXXXXXXX"+r.getString(2).trim(),r.getString(3),r.getString(4),r.getObject(5,LocalDate.class),r.getBigDecimal(6),r.getBoolean(7)),userId,size,page*size);
        return new Page<>(items,page,size,total);
    }

    public Page<TransactionView> transactions(UUID userId,int page,int size){
        long total=jdbc.queryForObject("SELECT count(*) FROM transactions t JOIN cards ca ON ca.card_id=t.card_id JOIN customers c ON c.customer_id=ca.customer_id WHERE c.user_id=?",Long.class,userId);
        List<TransactionView> items=jdbc.query("""
            SELECT t.txn_id,t.card_id,t.txn_ts,t.amount,t.currency,t.merchant_id,m.merchant_name,t.channel,t.txn_city,t.txn_country,t.status
            FROM transactions t JOIN cards ca ON ca.card_id=t.card_id JOIN customers c ON c.customer_id=ca.customer_id JOIN merchants m ON m.merchant_id=t.merchant_id
            WHERE c.user_id=? ORDER BY t.txn_ts DESC,t.txn_id DESC LIMIT ? OFFSET ?
            """,(r,n)->transactionView(r),userId,size,page*size);
        return new Page<>(items,page,size,total);
    }

    public TransactionView transaction(UUID userId,String txnId){
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM transactions WHERE txn_id=?)",Boolean.class,txnId))) throw new ResourceNotFoundException("Transaction not found");
        List<TransactionView> found=jdbc.query("""
            SELECT t.txn_id,t.card_id,t.txn_ts,t.amount,t.currency,t.merchant_id,m.merchant_name,t.channel,t.txn_city,t.txn_country,t.status
            FROM transactions t JOIN cards ca ON ca.card_id=t.card_id JOIN customers c ON c.customer_id=ca.customer_id JOIN merchants m ON m.merchant_id=t.merchant_id
            WHERE t.txn_id=? AND c.user_id=?
            """,(r,n)->transactionView(r),txnId,userId);
        if(found.isEmpty()) throw new AccessDeniedException("Transaction belongs to another cardholder"); return found.get(0);
    }

    public Page<PendingStepUp> stepUps(UUID userId,int page,int size){
        long total=jdbc.queryForObject("SELECT count(*) FROM step_up_requests s JOIN customers c ON c.customer_id=s.customer_id WHERE c.user_id=? AND s.status='PENDING'",Long.class,userId);
        List<PendingStepUp> items=jdbc.query("""
            SELECT s.id,t.txn_id,t.card_id,t.amount,m.merchant_name,s.created_at,s.expires_at,s.status
            FROM step_up_requests s JOIN transactions t ON t.id=s.transaction_id JOIN customers c ON c.customer_id=s.customer_id
            JOIN merchants m ON m.merchant_id=t.merchant_id
            WHERE c.user_id=? AND s.status='PENDING' ORDER BY s.created_at DESC LIMIT ? OFFSET ?
            """,(r,n)->new PendingStepUp((UUID)r.getObject(1),r.getString(2),r.getString(3),r.getBigDecimal(4),r.getString(5),r.getObject(6,OffsetDateTime.class),r.getObject(7,OffsetDateTime.class),r.getString(8)),userId,size,page*size);
        return new Page<>(items,page,size,total);
    }

    @Transactional public CardView block(UUID userId,String cardId){
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cards WHERE card_id=?)",Boolean.class,cardId))) throw new ResourceNotFoundException("Card not found");
        UUID owner=jdbc.queryForObject("SELECT c.user_id FROM cards ca JOIN customers c ON c.customer_id=ca.customer_id WHERE ca.card_id=? FOR UPDATE OF ca",UUID.class,cardId);
        if(!userId.equals(owner)) throw new AccessDeniedException("Card belongs to another cardholder");
        String status=jdbc.queryForObject("SELECT card_status FROM cards WHERE card_id=?",String.class,cardId);
        if("EXPIRED".equals(status)) throw new ConflictException("Expired card cannot be blocked");
        store.blockCard(cardId,userId.toString(),"CARDHOLDER",OffsetDateTime.now(clock).withOffsetSameInstant(IST),"Cardholder requested card block");
        return cards(userId,0,100).items().stream().filter(c->c.cardId().equals(cardId)).findFirst().orElseThrow();
    }
    private TransactionView transactionView(java.sql.ResultSet r)throws java.sql.SQLException{return new TransactionView(r.getString(1),r.getString(2),r.getObject(3,OffsetDateTime.class),r.getBigDecimal(4),r.getString(5).trim(),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11),displayReason(r.getString(11)));}
    private String displayReason(String status){return switch(status){case "APPROVED"->"Transaction approved";case "STEP_UP_PENDING"->"Verification required";case "BLOCKED"->"Transaction declined for your protection";default->"Transaction received";};}
    public record CardView(String cardId,String maskedCardNumber,String cardType,String cardStatus,LocalDate expiryDate,BigDecimal dailyLimit,boolean internationalEnabled){}
    public record TransactionView(String txnId,String cardId,OffsetDateTime txnTs,BigDecimal amount,String currency,String merchantId,String merchantName,String channel,String txnCity,String txnCountry,String status,String displayReason){}
    public record PendingStepUp(UUID stepUpId,String txnId,String cardId,BigDecimal amount,String merchantName,OffsetDateTime createdAt,OffsetDateTime expiresAt,String status){}
    public record Page<T>(List<T> items,int page,int size,long total){}
}
