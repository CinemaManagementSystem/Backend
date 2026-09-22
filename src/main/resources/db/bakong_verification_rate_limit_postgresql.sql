-- Apply this migration when Hibernate ddl-auto is disabled in production.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS verification_started_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS last_verification_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS next_verification_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS verification_attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS scheduled_verification_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS manual_verification_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS verification_failure_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS last_verification_error VARCHAR(500);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS rate_limited_until TIMESTAMP;

CREATE TABLE IF NOT EXISTS bakong_request_budgets (
    request_date DATE PRIMARY KEY,
    request_count INTEGER NOT NULL DEFAULT 0,
    rate_limited_until TIMESTAMP,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_payments_bakong_due
    ON payments (status, payment_method, next_verification_at, rate_limited_until);

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_transaction_reference
    ON payment_transactions (payment_id, reference)
    WHERE reference IS NOT NULL;
