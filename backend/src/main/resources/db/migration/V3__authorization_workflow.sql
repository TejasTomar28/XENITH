ALTER TABLE transactions
    ADD COLUMN rule_decision VARCHAR(8)
        CHECK (rule_decision IN ('APPROVE', 'STEP_UP', 'BLOCK'));

ALTER TABLE transaction_features
    ADD COLUMN rule_risk_score INTEGER
        CHECK (rule_risk_score BETWEEN 0 AND 100);

ALTER TABLE model_predictions
    ALTER COLUMN model_version DROP NOT NULL;

ALTER TABLE model_predictions
    DROP CONSTRAINT uk_model_prediction;

ALTER TABLE model_predictions
    ADD CONSTRAINT uk_model_prediction_transaction UNIQUE (transaction_id);
