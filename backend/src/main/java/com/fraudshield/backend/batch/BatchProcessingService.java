package com.fraudshield.backend.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.backend.authorization.TransactionAuthorizationService;
import com.fraudshield.backend.authorization.ai.TopFactor;
import com.fraudshield.backend.authorization.dto.AuthorizationRequest;
import com.fraudshield.backend.dataset.DatasetImportException;
import com.fraudshield.backend.domain.model.TransactionChannel;
import com.fraudshield.backend.domain.model.TransactionDecision;
import com.fraudshield.backend.domain.model.TransactionSource;
import com.fraudshield.backend.fraud.PersistedRuleConfigurationProvider;
import jakarta.validation.Validator;
import java.io.BufferedWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class BatchProcessingService {
    private static final Logger LOG=LoggerFactory.getLogger(BatchProcessingService.class);
    private static final ZoneOffset IST=ZoneOffset.ofHoursMinutes(5,30);
    private static final List<String> INPUT=List.of("txn_id","card_id","txn_ts","amount","currency","merchant_id","mcc","channel","txn_city","txn_country");
    private static final List<String> EXPECTED=List.of("expected_decision","expected_reason_codes","expected_rule_risk_score","expected_velocity_10m","expected_avg_amount_30d","expected_speed_kmph");
    private final TransactionAuthorizationService authorizations;private final JdbcTemplate jdbc;private final ObjectMapper json;private final Validator validator;private final PersistedRuleConfigurationProvider configs;
    public BatchProcessingService(TransactionAuthorizationService authorizations,JdbcTemplate jdbc,ObjectMapper json,Validator validator,PersistedRuleConfigurationProvider configs){this.authorizations=authorizations;this.jdbc=jdbc;this.json=json;this.validator=validator;this.configs=configs;}

    public BatchRunResult process(Path input,Path outputDirectory){
        long started=System.nanoTime();CsvData csv=read(input);boolean sample=csv.headers().equals(join(INPUT,EXPECTED));
        if(!sample&&!csv.headers().equals(INPUT))throw new DatasetImportException("Batch headers must exactly match the official sample or evaluation schema");
        TransactionSource source=sample?TransactionSource.BATCH_SAMPLE:TransactionSource.EVALUATION;
        List<BatchRow> rows=new ArrayList<>();Set<String> ids=new HashSet<>();long line=1;
        for(Map<String,String> values:csv.rows()){line++;AuthorizationRequest request=toRequest(values,line);if(!ids.add(request.transactionId()))throw new DatasetImportException("Duplicate txn_id in batch: "+request.transactionId());rows.add(new BatchRow(request,values));}
        rows.sort(Comparator.comparing((BatchRow r)->r.request().transactionTimestamp()).thenComparing(r->r.request().transactionId()));
        for(BatchRow row:rows)authorizations.authorizeBatch(row.request(),source);
        try{Files.createDirectories(outputDirectory);Path decisions=outputDirectory.resolve("decisions_output.csv");Path alerts=outputDirectory.resolve("alerts_output.csv");Path scores=outputDirectory.resolve("fraud_scores_output.csv");
            List<DecisionRow> decisionRows=rows.stream().map(r->decision(r.request().transactionId())).toList();writeDecisions(decisions,decisionRows);int alertCount=writeAlerts(alerts,rows);writeScores(scores,rows);
            List<String> mismatches=sample?compare(rows,decisionRows):List.of();Integer matched=sample?rows.size()-mismatches.size():null;
            long duration=(System.nanoTime()-started)/1_000_000;
            LOG.info("Batch processed rows={} alerts={} durationMs={} output={}",rows.size(),alertCount,duration,outputDirectory.toAbsolutePath());
            return new BatchRunResult(rows.size(),alertCount,duration,matched,mismatches,decisions,alerts,scores);
        }catch(DatasetImportException e){throw e;}catch(Exception e){throw new DatasetImportException("Unable to write batch outputs",e);}
    }

    private AuthorizationRequest toRequest(Map<String,String> row,long line){
        try{AuthorizationRequest request=new AuthorizationRequest(required(row,"txn_id"),required(row,"card_id"),OffsetDateTime.parse(required(row,"txn_ts")),new BigDecimal(required(row,"amount")),required(row,"currency"),required(row,"merchant_id"),required(row,"mcc"),TransactionChannel.valueOf(required(row,"channel")),required(row,"txn_city"),required(row,"txn_country"));
            var violations=validator.validate(request);if(!violations.isEmpty())throw new DatasetImportException("Batch row "+line+" is invalid: "+violations.iterator().next().getPropertyPath()+" "+violations.iterator().next().getMessage());
            if(!request.transactionTimestamp().getOffset().equals(IST))throw new DatasetImportException("Batch row "+line+" txn_ts must use +05:30");return request;
        }catch(DatasetImportException e){throw e;}catch(Exception e){throw new DatasetImportException("Batch row "+line+" is invalid: "+e.getMessage(),e);}
    }

    private DecisionRow decision(String txnId){return jdbc.queryForObject("""
        SELECT t.rule_decision,f.rule_risk_score,f.velocity_10m,f.avg_amount_30d,f.speed_kmph,
        COALESCE((SELECT string_agg(r.reason_code,'|' ORDER BY r.sequence_no) FROM transaction_reason_codes r WHERE r.transaction_id=t.id),'') reasons
        FROM transactions t LEFT JOIN transaction_features f ON f.transaction_id=t.id WHERE t.txn_id=?
        """,(r,n)->new DecisionRow(txnId,r.getString(1),(Integer)r.getObject(2),(Integer)r.getObject(3),r.getBigDecimal(4),(Double)r.getObject(5),r.getString(6)),txnId);}
    private void writeDecisions(Path path,List<DecisionRow> rows)throws Exception{try(BufferedWriter w=Files.newBufferedWriter(path)){line(w,List.of("txn_id","decision","reason_codes","rule_risk_score","velocity_10m","avg_amount_30d","speed_kmph"));for(var r:rows)line(w,List.of(r.txnId(),r.decision(),r.reasons(),value(r.score()),value(r.velocity()),two(r.average()),two(r.speed())));}}
    private int writeAlerts(Path path,List<BatchRow> rows)throws Exception{int count=0;try(BufferedWriter w=Files.newBufferedWriter(path)){line(w,List.of("txn_id","alert_priority","sla_due_at"));for(var row:rows){List<AlertOutput> found=jdbc.query("SELECT a.priority,a.sla_due_at FROM fraud_alerts a JOIN transactions t ON t.id=a.transaction_id WHERE t.txn_id=?",(r,n)->new AlertOutput(r.getString(1),r.getObject(2,OffsetDateTime.class)),row.request().transactionId());if(!found.isEmpty()){count++;var a=found.get(0);line(w,List.of(row.request().transactionId(),a.priority(),a.dueAt().withOffsetSameInstant(IST).toString()));}}}return count;}
    private void writeScores(Path path,List<BatchRow> rows)throws Exception{var config=configs.current();try(BufferedWriter w=Files.newBufferedWriter(path)){line(w,List.of("txn_id","fraud_score","final_decision","top_factors"));for(var row:rows){Map<String,Object> m=jdbc.queryForMap("SELECT t.rule_decision,p.fraud_score,p.top_factors::text,p.model_status FROM transactions t JOIN model_predictions p ON p.transaction_id=t.id WHERE t.txn_id=?",row.request().transactionId());BigDecimal score=(BigDecimal)m.get("fraud_score");String decision=m.get("rule_decision").toString();if(score!=null&&"AVAILABLE".equals(m.get("model_status"))){if("APPROVE".equals(decision)&&score.compareTo(config.aiStepUpThreshold())>=0)decision="STEP_UP";else if("STEP_UP".equals(decision)&&score.compareTo(config.aiBlockThreshold())>=0)decision="BLOCK";}List<TopFactor> factors=json.readValue(m.get("top_factors").toString(),new TypeReference<>(){});line(w,List.of(row.request().transactionId(),score==null?"":score.setScale(4,RoundingMode.HALF_UP).toPlainString(),decision,String.join("|",factors.stream().map(TopFactor::feature).toList())));}}}
    private List<String> compare(List<BatchRow> rows,List<DecisionRow> actual){List<String> mismatches=new ArrayList<>();for(int i=0;i<rows.size();i++){Map<String,String> e=rows.get(i).source();DecisionRow a=actual.get(i);if(!Objects.equals(e.get("expected_decision"),a.decision())||!Objects.equals(e.get("expected_reason_codes"),a.reasons())||!Objects.equals(e.get("expected_rule_risk_score"),value(a.score()))||!Objects.equals(e.get("expected_velocity_10m"),value(a.velocity()))||!Objects.equals(e.get("expected_avg_amount_30d"),two(a.average()))||!Objects.equals(e.get("expected_speed_kmph"),two(a.speed())))mismatches.add(a.txnId());}return mismatches;}

    private CsvData read(Path path){if(!Files.isRegularFile(path))throw new DatasetImportException("Batch input file does not exist: "+path);try{String content=Files.readString(path);List<List<String>> records=parse(content);if(records.isEmpty())throw new DatasetImportException("Batch input is empty");List<String> headers=records.get(0);List<Map<String,String>> rows=new ArrayList<>();for(int i=1;i<records.size();i++){if(records.get(i).size()!=headers.size())throw new DatasetImportException("Batch row "+(i+1)+" has an incorrect column count");Map<String,String> row=new LinkedHashMap<>();for(int c=0;c<headers.size();c++)row.put(headers.get(c),records.get(i).get(c).trim());rows.add(row);}return new CsvData(headers,rows);}catch(java.io.IOException e){throw new DatasetImportException("Unable to read batch input",e);}}
    private List<List<String>> parse(String content){List<List<String>> out=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false;for(int i=0;i<content.length();i++){char c=content.charAt(i);if(quoted){if(c=='"'){if(i+1<content.length()&&content.charAt(i+1)=='"'){field.append('"');i++;}else quoted=false;}else field.append(c);}else if(c=='"'&&field.length()==0)quoted=true;else if(c==','){row.add(field.toString());field.setLength(0);}else if(c=='\n'||c=='\r'){if(c=='\r'&&i+1<content.length()&&content.charAt(i+1)=='\n')i++;row.add(field.toString());field.setLength(0);if(!(row.size()==1&&row.get(0).isBlank()))out.add(List.copyOf(row));row.clear();}else field.append(c);}if(quoted)throw new DatasetImportException("Batch CSV contains an unterminated quoted field");if(field.length()>0||!row.isEmpty()){row.add(field.toString());out.add(List.copyOf(row));}return out;}
    private void line(BufferedWriter w,List<String> fields)throws Exception{w.write(String.join(",",fields.stream().map(this::escape).toList()));w.newLine();}private String escape(String v){if(v.contains(",")||v.contains("\"")||v.contains("\n"))return "\""+v.replace("\"","\"\"")+"\"";return v;}private String required(Map<String,String> r,String k){String v=r.get(k);if(v==null||v.isBlank())throw new DatasetImportException("Missing batch field: "+k);return v;}private String value(Object v){return v==null?"":v.toString();}private String two(BigDecimal v){return v==null?"":v.setScale(2,RoundingMode.HALF_UP).toPlainString();}private String two(Double v){return v==null?"":BigDecimal.valueOf(v).setScale(2,RoundingMode.HALF_UP).toPlainString();}private List<String> join(List<String>a,List<String>b){List<String> r=new ArrayList<>(a);r.addAll(b);return List.copyOf(r);}
    private record CsvData(List<String>headers,List<Map<String,String>>rows){}private record BatchRow(AuthorizationRequest request,Map<String,String>source){}private record DecisionRow(String txnId,String decision,Integer score,Integer velocity,BigDecimal average,Double speed,String reasons){}private record AlertOutput(String priority,OffsetDateTime dueAt){}
}
