CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_users_username UNIQUE (username),
    CONSTRAINT ck_app_users_role CHECK (
        role IN ('CARDHOLDER', 'FRAUD_ANALYST', 'FRAUD_RISK_MANAGER')
    )
);
