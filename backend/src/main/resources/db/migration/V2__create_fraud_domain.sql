CREATE TABLE country_risk (
    country_code VARCHAR(2) PRIMARY KEY,
    country_name VARCHAR(100) NOT NULL,
    risk_level VARCHAR(8) NOT NULL CHECK (risk_level IN ('LOW', 'HIGH'))
);

CREATE TABLE cities (
    city VARCHAR(100) PRIMARY KEY,
    country_code VARCHAR(2) NOT NULL REFERENCES country_risk(country_code),
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    city_tier VARCHAR(16) NOT NULL CHECK (city_tier IN ('METRO', 'NON_METRO', 'INTL')),
    CONSTRAINT uk_cities_city_country UNIQUE (city, country_code)
);

CREATE TABLE mcc_codes (
    mcc CHAR(4) PRIMARY KEY CHECK (mcc ~ '^[0-9]{4}$'),
    description VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    high_risk BOOLEAN NOT NULL
);

CREATE TABLE customers (
    customer_id VARCHAR(40) PRIMARY KEY,
    user_id UUID UNIQUE REFERENCES app_users(id),
    full_name VARCHAR(200) NOT NULL,
    mobile VARCHAR(40) NOT NULL,
    email VARCHAR(254) NOT NULL,
    city VARCHAR(100) NOT NULL REFERENCES cities(city),
    username VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE cards (
    card_id VARCHAR(40) PRIMARY KEY,
    customer_id VARCHAR(40) NOT NULL REFERENCES customers(customer_id),
    pan_hash VARCHAR(100) NOT NULL UNIQUE,
    last_four CHAR(4) NOT NULL CHECK (last_four ~ '^[0-9]{4}$'),
    card_type VARCHAR(8) NOT NULL CHECK (card_type IN ('DEBIT', 'CREDIT')),
    card_status VARCHAR(8) NOT NULL CHECK (card_status IN ('ACTIVE', 'BLOCKED', 'EXPIRED')),
    seed_card_status VARCHAR(8) NOT NULL CHECK (seed_card_status IN ('ACTIVE', 'BLOCKED', 'EXPIRED')),
    issue_date DATE NOT NULL,
    expiry_date DATE NOT NULL CHECK (expiry_date >= issue_date),
    home_city VARCHAR(100) NOT NULL,
    home_country VARCHAR(2) NOT NULL,
    daily_limit NUMERIC(14,2) NOT NULL CHECK (daily_limit > 0),
    international_enabled BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cards_home_city FOREIGN KEY (home_city, home_country)
        REFERENCES cities(city, country_code)
);

CREATE INDEX idx_cards_customer ON cards(customer_id);

CREATE TABLE merchants (
    merchant_id VARCHAR(40) PRIMARY KEY,
    merchant_name VARCHAR(255) NOT NULL,
    mcc CHAR(4) NOT NULL REFERENCES mcc_codes(mcc),
    mcc_description VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    country VARCHAR(2) NOT NULL,
    merchant_risk VARCHAR(16) NOT NULL CHECK (merchant_risk IN ('LOW', 'MEDIUM', 'HIGH', 'BLACKLISTED')),
    CONSTRAINT fk_merchants_city FOREIGN KEY (city, country) REFERENCES cities(city, country_code)
);

CREATE INDEX idx_merchants_mcc ON merchants(mcc);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    txn_id VARCHAR(10) NOT NULL UNIQUE CHECK (txn_id ~ '^T[0-9]{9}$'),
    card_id VARCHAR(40) NOT NULL REFERENCES cards(card_id),
    txn_ts TIMESTAMPTZ NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0 AND amount <= 1000000),
    currency CHAR(3) NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),
    merchant_id VARCHAR(40) NOT NULL REFERENCES merchants(merchant_id),
    mcc CHAR(4) NOT NULL REFERENCES mcc_codes(mcc),
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('POS', 'ECOM', 'ATM', 'CONTACTLESS')),
    txn_city VARCHAR(100) NOT NULL,
    txn_country VARCHAR(2) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('RECEIVED', 'APPROVED', 'STEP_UP_PENDING', 'BLOCKED')),
    original_decision VARCHAR(8) CHECK (original_decision IN ('APPROVE', 'STEP_UP', 'BLOCK')),
    original_status VARCHAR(24) NOT NULL CHECK (original_status IN ('RECEIVED', 'APPROVED', 'STEP_UP_PENDING', 'BLOCKED')),
    transaction_source VARCHAR(16) NOT NULL CHECK (transaction_source IN ('HISTORICAL', 'LIVE', 'BATCH_SAMPLE', 'EVALUATION', 'DEMO')),
    resettable BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_transactions_city FOREIGN KEY (txn_city, txn_country) REFERENCES cities(city, country_code)
);

CREATE INDEX idx_transactions_card_ts ON transactions(card_id, txn_ts, txn_id);
CREATE INDEX idx_transactions_card_status_ts ON transactions(card_id, status, txn_ts);
CREATE INDEX idx_transactions_card_merchant_ts ON transactions(card_id, merchant_id, txn_ts);
CREATE INDEX idx_transactions_resettable ON transactions(resettable) WHERE resettable;

CREATE TABLE transaction_features (
    transaction_id UUID PRIMARY KEY REFERENCES transactions(id) ON DELETE CASCADE,
    velocity_10m INTEGER CHECK (velocity_10m >= 0),
    velocity_24h INTEGER CHECK (velocity_24h >= 0),
    approved_today NUMERIC(16,2) CHECK (approved_today >= 0),
    avg_amount_30d NUMERIC(16,6) CHECK (avg_amount_30d >= 0),
    speed_kmph DOUBLE PRECISION CHECK (speed_kmph >= 0),
    new_merchant BOOLEAN,
    feature_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    configuration_version VARCHAR(128)
);

CREATE TABLE transaction_reason_codes (
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    sequence_no SMALLINT NOT NULL CHECK (sequence_no > 0),
    reason_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (transaction_id, sequence_no),
    CONSTRAINT uk_transaction_reason UNIQUE (transaction_id, reason_code)
);

CREATE TABLE model_predictions (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    model_version VARCHAR(128) NOT NULL,
    model_status VARCHAR(24) NOT NULL CHECK (model_status IN ('AVAILABLE', 'MODEL_UNAVAILABLE', 'NOT_EVALUATED')),
    fraud_score NUMERIC(7,6) CHECK (fraud_score BETWEEN 0 AND 1),
    top_factors JSONB NOT NULL DEFAULT '[]'::jsonb,
    feature_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_model_prediction UNIQUE (transaction_id, model_version)
);

CREATE TABLE step_up_requests (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    customer_id VARCHAR(40) NOT NULL REFERENCES customers(customer_id),
    otp_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'DENIED', 'EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_step_up_resolution CHECK (
        (status = 'PENDING' AND resolved_at IS NULL) OR
        (status <> 'PENDING' AND resolved_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_step_up_pending_transaction ON step_up_requests(transaction_id) WHERE status = 'PENDING';
CREATE INDEX idx_step_up_customer_status ON step_up_requests(customer_id, status, created_at DESC);
CREATE INDEX idx_step_up_expiry ON step_up_requests(expires_at) WHERE status = 'PENDING';

CREATE TABLE fraud_alerts (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL UNIQUE REFERENCES transactions(id) ON DELETE CASCADE,
    priority VARCHAR(8) NOT NULL CHECK (priority IN ('HIGH', 'MEDIUM')),
    status VARCHAR(32) NOT NULL CHECK (status IN ('OPEN', 'IN_REVIEW', 'CONFIRMED_FRAUD', 'FALSE_POSITIVE', 'PENDING_MANAGER_APPROVAL')),
    assigned_analyst_id UUID REFERENCES app_users(id),
    sla_due_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    outcome_comment VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_alert_closed_at CHECK (
        (status IN ('CONFIRMED_FRAUD', 'FALSE_POSITIVE') AND closed_at IS NOT NULL) OR
        (status NOT IN ('CONFIRMED_FRAUD', 'FALSE_POSITIVE') AND closed_at IS NULL)
    )
);

CREATE INDEX idx_alert_queue ON fraud_alerts(status, priority, sla_due_at);
CREATE INDEX idx_alert_assignee ON fraud_alerts(assigned_analyst_id, status, priority, sla_due_at);

CREATE TABLE manager_approval_proposals (
    id UUID PRIMARY KEY,
    alert_id UUID NOT NULL UNIQUE REFERENCES fraud_alerts(id) ON DELETE CASCADE,
    proposed_by UUID NOT NULL REFERENCES app_users(id),
    proposed_at TIMESTAMPTZ NOT NULL,
    proposal_comment VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by UUID REFERENCES app_users(id),
    reviewed_at TIMESTAMPTZ,
    review_comment VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_distinct_maker_checker CHECK (reviewed_by IS NULL OR reviewed_by <> proposed_by),
    CONSTRAINT ck_manager_review CHECK (
        (status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL) OR
        (status <> 'PENDING' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    )
);

CREATE INDEX idx_manager_pending ON manager_approval_proposals(status, proposed_at) WHERE status = 'PENDING';

CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(40) NOT NULL REFERENCES customers(customer_id),
    transaction_id UUID REFERENCES transactions(id) ON DELETE CASCADE,
    notification_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ
);

CREATE TABLE rule_configuration_versions (
    id UUID PRIMARY KEY,
    source_file VARCHAR(255) NOT NULL,
    source_sha256 CHAR(64) NOT NULL UNIQUE,
    configuration JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    imported_at TIMESTAMPTZ NOT NULL,
    imported_by VARCHAR(100) NOT NULL
);

CREATE UNIQUE INDEX uk_rule_configuration_active ON rule_configuration_versions(active) WHERE active;

CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(128) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    actor_role VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    action VARCHAR(100) NOT NULL,
    from_status VARCHAR(64),
    to_status VARCHAR(64),
    comment VARCHAR(2000),
    before_value JSONB,
    after_value JSONB,
    resettable BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_audit_entity_history ON audit_events(entity_type, entity_id, occurred_at, id);

CREATE FUNCTION protect_audit_events() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF current_setting('fraudshield.reset_mode', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'audit_events are append-only';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_audit_events_append_only
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW EXECUTE FUNCTION protect_audit_events();

CREATE TABLE data_import_executions (
    id UUID PRIMARY KEY,
    source_directory VARCHAR(1000) NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    diagnostic VARCHAR(2000),
    CONSTRAINT ck_import_completion CHECK (
        (status = 'RUNNING' AND completed_at IS NULL) OR
        (status <> 'RUNNING' AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_import_fingerprint ON data_import_executions(source_fingerprint, started_at DESC);

CREATE FUNCTION reset_fraudshield_evaluation_data() RETURNS TABLE(deleted_transactions BIGINT, restored_cards BIGINT)
LANGUAGE plpgsql AS $$
DECLARE
    tx_count BIGINT;
    card_count BIGINT;
BEGIN
    PERFORM set_config('fraudshield.reset_mode', 'on', true);
    DELETE FROM audit_events
     WHERE resettable
        OR (entity_type = 'TRANSACTION' AND entity_id IN
            (SELECT txn_id FROM transactions WHERE resettable));
    WITH changed AS (
        UPDATE cards c
           SET card_status = c.seed_card_status, version = c.version + 1
         WHERE c.card_status <> c.seed_card_status
           AND EXISTS (SELECT 1 FROM transactions t WHERE t.card_id = c.card_id AND t.resettable)
        RETURNING 1
    ) SELECT count(*) INTO card_count FROM changed;
    WITH removed AS (
        DELETE FROM transactions WHERE resettable RETURNING 1
    ) SELECT count(*) INTO tx_count FROM removed;
    RETURN QUERY SELECT tx_count, card_count;
END;
$$;
