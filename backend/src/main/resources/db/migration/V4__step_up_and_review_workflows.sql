DROP INDEX uk_step_up_pending_transaction;
CREATE UNIQUE INDEX uk_step_up_transaction ON step_up_requests(transaction_id);
CREATE INDEX idx_transaction_reason_code ON transaction_reason_codes(reason_code);
CREATE INDEX idx_audit_occurred_at ON audit_events(occurred_at);
