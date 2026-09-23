-- Promotion Domain Enhancement Migration

-- 1. Promotions table updates
ALTER TABLE promotions
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS combinable_with_membership BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Promotion Usage updates
ALTER TABLE promotion_usage
    ADD COLUMN IF NOT EXISTS booking_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Make order_id nullable in promotion_usage
ALTER TABLE promotion_usage
    ALTER COLUMN order_id DROP NOT NULL;

-- Add booking reference constraint and unique constraint to prevent duplicate redemption per booking
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_promotion_usage_booking') THEN
        ALTER TABLE promotion_usage
            ADD CONSTRAINT fk_promotion_usage_booking
            FOREIGN KEY (booking_id) REFERENCES bookings(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_promotion_usage_booking_promo') THEN
        ALTER TABLE promotion_usage
            ADD CONSTRAINT uk_promotion_usage_booking_promo
            UNIQUE (promotion_id, booking_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_promotion_usage_booking ON promotion_usage (booking_id);

-- 3. Payments table updates
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS promotion_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS promotion_code VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_payments_promotion') THEN
        ALTER TABLE payments
            ADD CONSTRAINT fk_payments_promotion
            FOREIGN KEY (promotion_id) REFERENCES promotions(id);
    END IF;
END $$;
