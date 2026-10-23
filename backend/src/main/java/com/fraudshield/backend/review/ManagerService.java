package com.fraudshield.backend.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fraudshield.backend.authorization.SemanticValidationException;
import com.fraudshield.backend.auth.UserAccount;
import com.fraudshield.backend.common.exception.ConflictException;
import com.fraudshield.backend.common.exception.ResourceNotFoundException;
import com.fraudshield.backend.fraud.PersistedRuleConfigurationProvider;
import com.fraudshield.backend.fraud.MissingRuleConfigurationException;
import com.fraudshield.backend.workflow.WorkflowStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManagerService {
 private static final ZoneOffset IST=ZoneOffset.ofHoursMinutes(5,30);
 private final JdbcTemplate jdbc;private final WorkflowStore store;private final AlertReviewService reviews;private final PersistedRuleConfigurationProvider provider;private final ObjectMapper json;private final Clock clock;
 public ManagerService(JdbcTemplate jdbc,WorkflowStore store,AlertReviewService reviews,PersistedRuleConfigurationProvider provider,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.store=store;this.reviews=reviews;this.provider=provider;this.json=json;this.clock=clock;}

 public AlertReviewService.Page<Map<String,Object>> proposals(int page,int size,String status){
  if(page<0||size<1||size>100)throw new SemanticValidationException("page","Invalid page or size");if(status!=null&&!Set.of("PENDING","APPROVED","REJECTED").contains(status))throw new SemanticValidationException("status","Unsupported proposal status");String where=status==null?"":" WHERE p.status=?";Object[] countArgs=status==null?new Object[]{}:new Object[]{status};
  long total=jdbc.queryForObject("SELECT count(*) FROM manager_approval_proposals p"+where,Long.class,countArgs);List<Object> args=new ArrayList<>(Arrays.asList(countArgs));args.add(size);args.add(page*size);
  var items=jdbc.queryForList("""
   SELECT p.id proposal_id,p.status proposal_status,p.proposed_by,p.proposed_at,p.proposal_comment,p.reviewed_by,p.reviewed_at,p.review_comment,
   a.id alert_id,a.priority,a.status,t.txn_id,t.amount FROM manager_approval_proposals p JOIN fraud_alerts a ON a.id=p.alert_id JOIN transactions t ON t.id=a.transaction_id
   """+where+" ORDER BY CASE p.status WHEN 'PENDING' THEN 0 ELSE 1 END,p.proposed_at,p.id LIMIT ? OFFSET ?",args.toArray());return new AlertReviewService.Page<>(items,page,size,total);
 }
 public Map<String,Object> proposalDetail(UUID id){Map<String,Object> proposal=reviews.proposal(id);UUID alert=(UUID)proposal.get("alert_id");return Map.of("proposal",proposal,"evidence",reviews.detail(alert));}

 @Transactional public Map<String,Object> decide(UUID id,UserAccount manager,String decision,String comment){
  if(!Set.of("APPROVE","REJECT").contains(decision))throw new SemanticValidationException("decision","Must be APPROVE or REJECT");if(comment==null||comment.isBlank())throw new SemanticValidationException("comment","Must not be blank");
  ProposalRow row=lockProposal(id);if(!"PENDING".equals(row.status()))throw new ConflictException("Proposal is already decided");if(manager.getId().equals(row.maker()))throw new AccessDeniedException("Maker cannot check their own proposal");if(!"PENDING_MANAGER_APPROVAL".equals(row.alertStatus()))throw new ConflictException("Alert is not pending manager approval");
  OffsetDateTime at=now();String proposalStatus="APPROVE".equals(decision)?"APPROVED":"REJECTED";String alertStatus="APPROVE".equals(decision)?"FALSE_POSITIVE":"CONFIRMED_FRAUD";
  jdbc.update("UPDATE manager_approval_proposals SET status=?,reviewed_by=?,reviewed_at=?,review_comment=?,version=version+1 WHERE id=?",proposalStatus,manager.getId(),at,comment,id);
  jdbc.update("UPDATE fraud_alerts SET status=?,closed_at=?,version=version+1 WHERE id=?",alertStatus,at,row.alertId());
  if("REJECT".equals(decision))store.blockCard(row.cardId(),manager.getId().toString(),"FRAUD_RISK_MANAGER",at,"Manager rejected false-positive proposal");
  else jdbc.update("INSERT INTO notification_outbox(id,customer_id,transaction_id,notification_type,payload,created_at) VALUES (?,?,?,'FALSE_POSITIVE_RESOLVED','{}'::jsonb,?)",UUID.randomUUID(),row.customerId(),row.transactionId(),at);
  store.audit("PROPOSAL",id.toString(),manager.getId().toString(),"FRAUD_RISK_MANAGER",at,"PROPOSAL_"+proposalStatus,"PENDING",proposalStatus,comment,null,null);
  store.audit("ALERT",row.alertId().toString(),manager.getId().toString(),"FRAUD_RISK_MANAGER",at,"MANAGER_DECISION","PENDING_MANAGER_APPROVAL",alertStatus,comment,null,null);
  return Map.of("proposal",reviews.proposal(id),"alert",reviews.detail(row.alertId()));
 }

 public Map<String,Object> dashboard(OffsetDateTime from,OffsetDateTime to){
  OffsetDateTime end=to==null?now():to,start=from==null?end.minusDays(30):from;if(!start.isBefore(end))throw new SemanticValidationException("from","Must be earlier than to");
  Map<String,Long> decisions=counts("SELECT original_decision,count(*) FROM transactions WHERE txn_ts>=? AND txn_ts<? GROUP BY original_decision",start,end);
  long total=decisions.values().stream().mapToLong(Long::longValue).sum();long blocks=decisions.getOrDefault("BLOCK",0L),steps=decisions.getOrDefault("STEP_UP",0L);
  Map<String,Long> open=counts("SELECT priority,count(*) FROM fraud_alerts WHERE created_at>=? AND created_at<? AND status NOT IN ('CONFIRMED_FRAUD','FALSE_POSITIVE') GROUP BY priority",start,end);
  Long breached=jdbc.queryForObject("SELECT count(*) FROM fraud_alerts WHERE created_at>=? AND created_at<? AND status NOT IN ('CONFIRMED_FRAUD','FALSE_POSITIVE') AND sla_due_at<CURRENT_TIMESTAMP",Long.class,start,end);
  Map<String,Long> closed=counts("SELECT status,count(*) FROM fraud_alerts WHERE closed_at>=? AND closed_at<? AND status IN ('CONFIRMED_FRAUD','FALSE_POSITIVE') GROUP BY status",start,end);long closedTotal=closed.values().stream().mapToLong(Long::longValue).sum();
  var reasons=jdbc.queryForList("SELECT r.reason_code,count(*) count FROM transaction_reason_codes r JOIN transactions t ON t.id=r.transaction_id WHERE t.txn_ts>=? AND t.txn_ts<? GROUP BY r.reason_code ORDER BY count DESC,r.reason_code LIMIT 5",start,end);
  Map<String,Object> result=new LinkedHashMap<>();result.put("from",start);result.put("to",end);result.put("transactions_by_decision",decisions);result.put("block_rate",total==0?0d:(double)blocks/total);result.put("step_up_rate",total==0?0d:(double)steps/total);result.put("open_alerts_by_priority",open);result.put("sla_breaches",breached);result.put("false_positive_rate",closedTotal==0?null:(double)closed.getOrDefault("FALSE_POSITIVE",0L)/closedTotal);result.put("top_reason_codes",reasons);result.put("generated_at",now());return result;
 }

 public Map<String,Object> configuration(){return jdbc.query("SELECT source_sha256,configuration::text,imported_at FROM rule_configuration_versions WHERE active",r->{if(!r.next())throw new ResourceNotFoundException("Configuration not found");try{return Map.of("version",r.getString(1),"settings",json.readTree(r.getString(2)),"updated_at",r.getObject(3,OffsetDateTime.class));}catch(Exception e){throw new IllegalStateException(e);}});}

 @Transactional public Map<String,Object> updateConfiguration(UserAccount manager,String expected,JsonNode settings,String comment){
  if(comment==null||comment.isBlank())throw new SemanticValidationException("comment","Must not be blank");if(settings==null||!settings.isObject())throw new SemanticValidationException("settings","Must be a JSON object");
  Map<String,Object> current=configuration();if(!Objects.equals(expected,current.get("version")))throw new ConflictException("Configuration version is stale");ObjectNode copy=((ObjectNode)settings).deepCopy();
  try{provider.validate("candidate",copy);}catch(MissingRuleConfigurationException|IllegalArgumentException exception){throw new SemanticValidationException("settings",exception.getMessage());}String encoded;try{encoded=json.writeValueAsString(copy);}catch(Exception e){throw new SemanticValidationException("settings","Cannot serialize settings");}String version=sha256(encoded);OffsetDateTime at=now();
  jdbc.update("UPDATE rule_configuration_versions SET active=false WHERE active");jdbc.update("INSERT INTO rule_configuration_versions(id,source_file,source_sha256,configuration,active,imported_at,imported_by) VALUES (?,'manager-api.json',?,?::jsonb,true,?,?)",UUID.randomUUID(),version,encoded,at,manager.getUsername());
  store.audit("CONFIGURATION",version,manager.getId().toString(),"FRAUD_RISK_MANAGER",at,"CONFIGURATION_UPDATED",expected,version,comment,current.get("settings"),copy);return configuration();
 }

 public AlertReviewService.Page<Map<String,Object>> audits(String type,String id,int page,int size){
  if(page<0||size<1||size>100)throw new SemanticValidationException("page","Invalid page or size");String entity=type.toUpperCase(Locale.ROOT);if(!Set.of("TRANSACTION","ALERT","CARD","CONFIGURATION").contains(entity))throw new SemanticValidationException("entity_type","Unsupported entity type");AuditScope scope=auditScope(entity,id);long total=jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE "+scope.where(),Long.class,scope.args());if(total==0)throw new ResourceNotFoundException("Audit target not found");List<Object> args=new ArrayList<>(Arrays.asList(scope.args()));args.add(size);args.add(page*size);var items=jdbc.queryForList("SELECT id audit_id,entity_type,entity_id,actor_id,actor_role,occurred_at,action,from_status,to_status,comment,before_value,after_value FROM audit_events WHERE "+scope.where()+" ORDER BY occurred_at,id LIMIT ? OFFSET ?",args.toArray());return new AlertReviewService.Page<>(items,page,size,total);
 }
 public byte[] auditCsv(String type,String id){String entity=type.toUpperCase(Locale.ROOT);if(!Set.of("TRANSACTION","ALERT","CARD").contains(entity))throw new SemanticValidationException("entity_type","CSV export supports transaction, alert or card");AuditScope scope=auditScope(entity,id);var rows=jdbc.queryForList("SELECT entity_type,entity_id,actor_id,actor_role,occurred_at,action,from_status,to_status,comment,before_value::text before_value,after_value::text after_value FROM audit_events WHERE "+scope.where()+" ORDER BY occurred_at,id",scope.args());if(rows.isEmpty())throw new ResourceNotFoundException("Audit target not found");StringBuilder out=new StringBuilder("entity,entity_id,actor,role,timestamp,action,from_status,to_status,comment,before,after\n");for(var row:rows){List<String> fields=List.of(csvValue(row.get("entity_type")),csvValue(row.get("entity_id")),csvValue(row.get("actor_id")),csvValue(row.get("actor_role")),csvValue(row.get("occurred_at")),csvValue(row.get("action")),csvValue(row.get("from_status")),csvValue(row.get("to_status")),csvValue(row.get("comment")),csvValue(row.get("before_value")),csvValue(row.get("after_value")));out.append(String.join(",",fields)).append('\n');}return out.toString().getBytes(StandardCharsets.UTF_8);}
 private AuditScope auditScope(String entity,String id){
  if(!"TRANSACTION".equals(entity))return new AuditScope("entity_type=? AND entity_id=?",new Object[]{entity,id});
  List<Map<String,Object>> transactions=jdbc.queryForList("SELECT id,card_id FROM transactions WHERE txn_id=?",id);if(transactions.isEmpty())throw new ResourceNotFoundException("Audit target not found");UUID transactionId=(UUID)transactions.get(0).get("id");String cardId=(String)transactions.get(0).get("card_id");List<AuditIdentity> identities=new ArrayList<>();identities.add(new AuditIdentity("TRANSACTION",id));
  jdbc.queryForList("SELECT id::text FROM step_up_requests WHERE transaction_id=?",String.class,transactionId).forEach(value->identities.add(new AuditIdentity("STEP_UP",value)));
  jdbc.queryForList("SELECT id::text FROM fraud_alerts WHERE transaction_id=?",String.class,transactionId).forEach(value->{identities.add(new AuditIdentity("ALERT",value));jdbc.queryForList("SELECT id::text FROM manager_approval_proposals WHERE alert_id=?",String.class,UUID.fromString(value)).forEach(proposal->identities.add(new AuditIdentity("PROPOSAL",proposal)));});
  String linked=String.join(" OR ",Collections.nCopies(identities.size(),"(entity_type=? AND entity_id=?)"));List<Object> linkedArgs=new ArrayList<>();identities.forEach(value->{linkedArgs.add(value.type());linkedArgs.add(value.id());});List<Object> allArgs=new ArrayList<>(linkedArgs);allArgs.add(cardId);allArgs.addAll(linkedArgs);return new AuditScope("("+linked+") OR (entity_type='CARD' AND entity_id=? AND occurred_at IN (SELECT occurred_at FROM audit_events WHERE "+linked+"))",allArgs.toArray());
 }
 private String csvValue(Object value){if(value==null)return "";String text=value.toString();if(!text.isEmpty()&&"=+-@".indexOf(text.charAt(0))>=0)text="'"+text;return "\""+text.replace("\"","\"\"")+"\"";}
 private Map<String,Long> counts(String sql,Object...args){Map<String,Long> result=new LinkedHashMap<>();jdbc.query(sql,r->{result.put(r.getString(1),r.getLong(2));},args);return result;}
 private ProposalRow lockProposal(UUID id){List<ProposalRow> rows=jdbc.query("""
  SELECT p.status,p.proposed_by,p.alert_id,a.status,t.id,t.card_id,ca.customer_id FROM manager_approval_proposals p JOIN fraud_alerts a ON a.id=p.alert_id JOIN transactions t ON t.id=a.transaction_id JOIN cards ca ON ca.card_id=t.card_id WHERE p.id=? FOR UPDATE OF p,a,t,ca
  """,(r,n)->new ProposalRow(r.getString(1),(UUID)r.getObject(2),(UUID)r.getObject(3),r.getString(4),(UUID)r.getObject(5),r.getString(6),r.getString(7)),id);if(rows.isEmpty())throw new ResourceNotFoundException("Proposal not found");return rows.get(0);}
 private String sha256(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private OffsetDateTime now(){return OffsetDateTime.now(clock).withOffsetSameInstant(IST);}private record ProposalRow(String status,UUID maker,UUID alertId,String alertStatus,UUID transactionId,String cardId,String customerId){}
 private record AuditIdentity(String type,String id){}private record AuditScope(String where,Object[]args){}
}
