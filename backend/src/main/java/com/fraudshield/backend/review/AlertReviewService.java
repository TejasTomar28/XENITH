package com.fraudshield.backend.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.backend.authorization.SemanticValidationException;
import com.fraudshield.backend.auth.UserAccount;
import com.fraudshield.backend.common.exception.ConflictException;
import com.fraudshield.backend.common.exception.ResourceNotFoundException;
import com.fraudshield.backend.fraud.PersistedRuleConfigurationProvider;
import com.fraudshield.backend.workflow.WorkflowStore;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertReviewService {
    private static final ZoneOffset IST=ZoneOffset.ofHoursMinutes(5,30);
    private final JdbcTemplate jdbc; private final WorkflowStore store; private final PersistedRuleConfigurationProvider configs; private final Clock clock; private final ObjectMapper json;
    public AlertReviewService(JdbcTemplate jdbc,WorkflowStore store,PersistedRuleConfigurationProvider configs,Clock clock,ObjectMapper json){this.jdbc=jdbc;this.store=store;this.configs=configs;this.clock=clock;this.json=json;}

    public Page<Map<String,Object>> alerts(int page,int size,String status,String priority,UUID claimedBy,Boolean breached){
        validatePage(page,size);
        if(status!=null&&!Set.of("OPEN","IN_REVIEW","CONFIRMED_FRAUD","FALSE_POSITIVE","PENDING_MANAGER_APPROVAL").contains(status))throw new SemanticValidationException("status","Unsupported alert status");
        if(priority!=null&&!Set.of("HIGH","MEDIUM").contains(priority))throw new SemanticValidationException("priority","Unsupported alert priority");
        List<Object> args=new ArrayList<>(); StringBuilder where=new StringBuilder(" WHERE 1=1");
        if(status!=null){where.append(" AND a.status=?");args.add(status);}
        else where.append(" AND a.status NOT IN ('CONFIRMED_FRAUD','FALSE_POSITIVE')");
        if(priority!=null){where.append(" AND a.priority=?");args.add(priority);}
        if(claimedBy!=null){where.append(" AND a.assigned_analyst_id=?");args.add(claimedBy);}
        if(Boolean.TRUE.equals(breached)){where.append(" AND a.status NOT IN ('CONFIRMED_FRAUD','FALSE_POSITIVE') AND a.sla_due_at<?");args.add(now());}
        long total=jdbc.queryForObject("SELECT count(*) FROM fraud_alerts a"+where,Long.class,args.toArray());
        args.add(size);args.add(page*size);
        var items=jdbc.queryForList("""
            SELECT a.id alert_id,t.txn_id,t.amount,a.priority,a.status,a.assigned_analyst_id,a.sla_due_at,a.created_at,
                   (a.status NOT IN ('CONFIRMED_FRAUD','FALSE_POSITIVE') AND a.sla_due_at<CURRENT_TIMESTAMP) sla_breached
            FROM fraud_alerts a JOIN transactions t ON t.id=a.transaction_id
            """+where+" ORDER BY CASE a.priority WHEN 'HIGH' THEN 0 ELSE 1 END,a.sla_due_at,a.id LIMIT ? OFFSET ?",args.toArray());
        return new Page<>(items.stream().map(this::normalize).toList(),page,size,total);
    }

    public Map<String,Object> detail(UUID alertId){
        List<Map<String,Object>> found=jdbc.queryForList("""
            SELECT a.id alert_id,a.priority,a.status,a.assigned_analyst_id,a.sla_due_at,a.created_at,a.closed_at,a.outcome_comment,
              t.txn_id,t.txn_ts,t.amount,t.currency,t.merchant_id,t.mcc,t.channel,t.txn_city,t.txn_country,t.status transaction_status,
              f.velocity_10m,f.velocity_24h,f.approved_today,f.avg_amount_30d,f.speed_kmph,f.new_merchant,f.rule_risk_score,
              p.fraud_score,p.model_status,p.model_version,p.top_factors,
              ARRAY(SELECT reason_code FROM transaction_reason_codes r WHERE r.transaction_id=t.id ORDER BY sequence_no) reason_codes
            FROM fraud_alerts a JOIN transactions t ON t.id=a.transaction_id
            LEFT JOIN transaction_features f ON f.transaction_id=t.id LEFT JOIN model_predictions p ON p.transaction_id=t.id WHERE a.id=?
            """,alertId); if(found.isEmpty())throw new ResourceNotFoundException("Alert not found");return normalize(found.get(0));
    }

    public Page<Map<String,Object>> history(UUID alertId,int page,int size){
        validatePage(page,size); String card=jdbc.query("SELECT t.card_id FROM fraud_alerts a JOIN transactions t ON t.id=a.transaction_id WHERE a.id=?",r->r.next()?r.getString(1):null,alertId);
        if(card==null)throw new ResourceNotFoundException("Alert not found");
        long total=jdbc.queryForObject("SELECT count(*) FROM transactions WHERE card_id=?",Long.class,card);
        var items=jdbc.queryForList("""
            SELECT t.txn_id,t.txn_ts,t.amount,t.merchant_id,t.channel,t.txn_city,t.txn_country,t.status,
            ARRAY(SELECT reason_code FROM transaction_reason_codes r WHERE r.transaction_id=t.id ORDER BY sequence_no) reason_codes
            FROM transactions t WHERE t.card_id=? ORDER BY t.txn_ts DESC,t.txn_id DESC LIMIT ? OFFSET ?
            """,card,size,page*size);return new Page<>(items.stream().map(this::normalize).toList(),page,size,total);
    }

    @Transactional public Map<String,Object> claim(UUID alertId,UserAccount analyst){
        AlertRow row=lockAlert(alertId);if(!"OPEN".equals(row.status()))throw new ConflictException("Alert is no longer open");OffsetDateTime at=now();
        jdbc.update("UPDATE fraud_alerts SET status='IN_REVIEW',assigned_analyst_id=?,version=version+1 WHERE id=?",analyst.getId(),alertId);
        store.audit("ALERT",alertId.toString(),analyst.getId().toString(),"FRAUD_ANALYST",at,"ALERT_CLAIMED","OPEN","IN_REVIEW",null,null,null);return detail(alertId);
    }

    @Transactional public ResolutionResult resolve(UUID alertId,UserAccount analyst,String resolution,String comment){
        if(comment==null||comment.trim().length()<10)throw new SemanticValidationException("comment","Must contain at least 10 characters");
        if(!Set.of("CONFIRMED_FRAUD","FALSE_POSITIVE").contains(resolution))throw new SemanticValidationException("resolution","Must be CONFIRMED_FRAUD or FALSE_POSITIVE");
        AlertRow row=lockAlert(alertId);if(!"IN_REVIEW".equals(row.status()))throw new ConflictException("Alert is not in review");
        if(!analyst.getId().equals(row.assignee()))throw new AccessDeniedException("Only the assigned analyst may resolve this alert");OffsetDateTime at=now();UUID proposal=null;String target;
        if("CONFIRMED_FRAUD".equals(resolution)){target="CONFIRMED_FRAUD";close(alertId,target,comment,at);store.blockCard(row.cardId(),analyst.getId().toString(),"FRAUD_ANALYST",at,"Alert confirmed as fraud");}
        else if(row.amount().compareTo(configs.current().makerCheckerLimit())>0){target="PENDING_MANAGER_APPROVAL";proposal=UUID.randomUUID();jdbc.update("UPDATE fraud_alerts SET status=?,outcome_comment=?,version=version+1 WHERE id=?",target,comment,alertId);jdbc.update("INSERT INTO manager_approval_proposals(id,alert_id,proposed_by,proposed_at,proposal_comment,status,version) VALUES (?,?,?,?,?,'PENDING',0)",proposal,alertId,analyst.getId(),at,comment);store.audit("PROPOSAL",proposal.toString(),analyst.getId().toString(),"FRAUD_ANALYST",at,"FALSE_POSITIVE_PROPOSED",null,"PENDING",comment,null,null);}
        else{target="FALSE_POSITIVE";close(alertId,target,comment,at);notifyFalsePositive(row,at);}
        store.audit("ALERT",alertId.toString(),analyst.getId().toString(),"FRAUD_ANALYST",at,"ALERT_RESOLVED","IN_REVIEW",target,comment,null,null);
        return new ResolutionResult(detail(alertId),proposal==null?null:proposal(proposal));
    }

    private void close(UUID id,String status,String comment,OffsetDateTime at){jdbc.update("UPDATE fraud_alerts SET status=?,closed_at=?,outcome_comment=?,version=version+1 WHERE id=?",status,at,comment,id);}
    private void notifyFalsePositive(AlertRow row,OffsetDateTime at){jdbc.update("INSERT INTO notification_outbox(id,customer_id,transaction_id,notification_type,payload,created_at) VALUES (?,?,?,'FALSE_POSITIVE_RESOLVED','{}'::jsonb,?)",UUID.randomUUID(),row.customerId(),row.transactionId(),at);}
    public Map<String,Object> proposal(UUID id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT id proposal_id,alert_id,proposed_by,proposed_at,proposal_comment,status,reviewed_by,reviewed_at,review_comment FROM manager_approval_proposals WHERE id=?",id);if(rows.isEmpty())throw new ResourceNotFoundException("Proposal not found");return normalize(rows.get(0));}
    private Map<String,Object> normalize(Map<String,Object> source){
        Map<String,Object> result=new LinkedHashMap<>();source.forEach((key,value)->result.put(key,normalizeValue(value)));return result;
    }
    private Object normalizeValue(Object value){
        try{
            if(value instanceof java.sql.Array array){Object raw=array.getArray();return raw instanceof Object[] values?Arrays.asList(values):raw;}
            if(value!=null&&value.getClass().getName().equals("org.postgresql.util.PGobject")){
                String raw=(String)value.getClass().getMethod("getValue").invoke(value);return raw==null?null:json.readTree(raw);
            }
            return value;
        }catch(Exception exception){throw new IllegalStateException("Cannot normalize database evidence",exception);}
    }
    private AlertRow lockAlert(UUID id){List<AlertRow> rows=jdbc.query("""
        SELECT a.status,a.assigned_analyst_id,t.id,t.card_id,t.amount,ca.customer_id FROM fraud_alerts a JOIN transactions t ON t.id=a.transaction_id JOIN cards ca ON ca.card_id=t.card_id WHERE a.id=? FOR UPDATE OF a,t,ca
        """,(r,n)->new AlertRow(r.getString(1),(UUID)r.getObject(2),(UUID)r.getObject(3),r.getString(4),r.getBigDecimal(5),r.getString(6)),id);if(rows.isEmpty())throw new ResourceNotFoundException("Alert not found");return rows.get(0);}
    private void validatePage(int page,int size){if(page<0)throw new SemanticValidationException("page","Must be zero or greater");if(size<1||size>100)throw new SemanticValidationException("size","Must be between 1 and 100");}
    private OffsetDateTime now(){return OffsetDateTime.now(clock).withOffsetSameInstant(IST);}
    private record AlertRow(String status,UUID assignee,UUID transactionId,String cardId,java.math.BigDecimal amount,String customerId){}
    public record Page<T>(List<T> items,int page,int size,long total){}
    public record ResolutionResult(Map<String,Object>alert,Map<String,Object>proposal){}
}
