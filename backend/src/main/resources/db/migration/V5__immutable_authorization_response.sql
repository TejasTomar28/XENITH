ALTER TABLE transactions
    ADD COLUMN authorization_response JSONB;

UPDATE transactions t
   SET authorization_response = jsonb_build_object(
       'txn_id', t.txn_id,
       'decision', t.original_decision,
       'reason_codes', COALESCE((
           SELECT jsonb_agg(r.reason_code ORDER BY r.sequence_no)
             FROM transaction_reason_codes r
            WHERE r.transaction_id = t.id
              AND r.reason_code NOT IN ('STEP_UP_DENIED', 'STEP_UP_TIMEOUT')
       ), '[]'::jsonb),
       'rule_risk_score', f.rule_risk_score,
       'status', t.original_status,
       'alert_id', CASE WHEN a.created_at = t.processed_at THEN to_jsonb(a.id) ELSE 'null'::jsonb END,
       'processed_at', to_jsonb(t.processed_at),
       'fraud_score', p.fraud_score,
       'top_factors', COALESCE(p.top_factors, '[]'::jsonb),
       'model_status', COALESCE(p.model_status, 'NOT_EVALUATED')
   )
   FROM transaction_features f
   LEFT JOIN model_predictions p ON p.transaction_id = f.transaction_id
   LEFT JOIN fraud_alerts a ON a.transaction_id = f.transaction_id
  WHERE t.id = f.transaction_id
    AND t.original_decision IS NOT NULL;

COMMENT ON COLUMN transactions.authorization_response IS
    'Immutable gateway response snapshot used for idempotent transaction retries';
