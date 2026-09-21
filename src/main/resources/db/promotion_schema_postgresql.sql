-- Promotion module schema for PostgreSQL 16.
-- This project does not currently have Flyway/Liquibase wired in, so apply
-- this manually or move it into your migration tool when one is added.

CREATE TABLE IF NOT EXISTS promotions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(255) UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    discount_type VARCHAR(32) NOT NULL CHECK (discount_type IN ('PERCENT', 'FIXED_AMOUNT')),
    discount_value NUMERIC(12, 2) NOT NULL CHECK (discount_value >= 0),
    max_discount_amount NUMERIC(12, 2) CHECK (max_discount_amount IS NULL OR max_discount_amount >= 0),
    min_order_amount NUMERIC(12, 2) CHECK (min_order_amount IS NULL OR min_order_amount >= 0),
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    usage_limit_total INTEGER CHECK (usage_limit_total IS NULL OR usage_limit_total >= 0),
    usage_limit_per_user INTEGER CHECK (usage_limit_per_user IS NULL OR usage_limit_per_user >= 0),
    used_count INTEGER NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    status VARCHAR(32) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'EXPIRED')),
    scope VARCHAR(32) NOT NULL CHECK (scope IN ('ALL', 'MOVIE', 'SHOW', 'PRODUCT')),
    target_movie_id BIGINT,
    target_show_id BIGINT,
    target_product_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_promotions_date_range CHECK (end_date >= start_date),
    CONSTRAINT ck_promotions_scope_target CHECK (
        (scope = 'ALL' AND target_movie_id IS NULL AND target_show_id IS NULL AND target_product_id IS NULL)
        OR (scope = 'MOVIE' AND target_movie_id IS NOT NULL)
        OR (scope = 'SHOW' AND target_show_id IS NOT NULL)
        OR (scope = 'PRODUCT' AND target_product_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_promotions_code ON promotions (code);
CREATE INDEX IF NOT EXISTS idx_promotions_status_dates ON promotions (status, start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_promotions_scope ON promotions (scope, target_movie_id, target_show_id, target_product_id);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS promotion_id BIGINT NULL;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_promotion
    FOREIGN KEY (promotion_id)
    REFERENCES promotions(id);

CREATE TABLE IF NOT EXISTS promotion_usage (
    id BIGSERIAL PRIMARY KEY,
    promotion_id BIGINT NOT NULL REFERENCES promotions(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    order_id BIGINT NOT NULL REFERENCES orders(id),
    discount_applied NUMERIC(12, 2) NOT NULL CHECK (discount_applied >= 0),
    status VARCHAR(32) NOT NULL CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED')),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_promotion_usage_order UNIQUE (order_id)
);

CREATE INDEX IF NOT EXISTS idx_promotion_usage_promotion_status ON promotion_usage (promotion_id, status);
CREATE INDEX IF NOT EXISTS idx_promotion_usage_user_status ON promotion_usage (user_id, status);
