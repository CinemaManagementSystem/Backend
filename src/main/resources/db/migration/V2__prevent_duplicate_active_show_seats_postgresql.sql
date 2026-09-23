-- PostgreSQL 16 migration: one active hold/booking per show + seat.
-- Existing application states map as: PENDING = HELD, CONFIRMED = BOOKED,
-- CANCELLED = RELEASED. Apply through Flyway or psql before deploying code
-- that writes booking_seats.show_id.

BEGIN;

ALTER TABLE booking_seats ADD COLUMN IF NOT EXISTS show_id BIGINT;

UPDATE booking_seats bs
SET show_id = b.show_id
FROM bookings b
WHERE bs.booking_id = b.id
  AND bs.show_id IS NULL;

-- Repair legacy rows whose parent is no longer active so the partial index
-- represents the true active hold/booking state.
UPDATE booking_seats bs
SET status = 'CANCELLED'
FROM bookings b
WHERE bs.booking_id = b.id
  AND b.status IN ('CANCELLED', 'EXPIRED')
  AND UPPER(bs.status) <> 'CANCELLED';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM booking_seats
WHERE UPPER(status) NOT IN ('CANCELLED', 'EXPIRED', 'RELEASED')
        GROUP BY show_id, seat_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot add active show-seat uniqueness: duplicate active booking_seats exist. Resolve duplicates before rerunning this migration.';
    END IF;
END $$;

ALTER TABLE booking_seats ALTER COLUMN show_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_booking_seats_show'
    ) THEN
        ALTER TABLE booking_seats
            ADD CONSTRAINT fk_booking_seats_show
            FOREIGN KEY (show_id) REFERENCES shows(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_booking_seats_show_seat
    ON booking_seats (show_id, seat_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_booking_seats_active_show_seat
    ON booking_seats (show_id, seat_id)
    WHERE UPPER(status) NOT IN ('CANCELLED', 'EXPIRED', 'RELEASED');

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bookings_customer_idempotency_key
    ON bookings (customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

COMMIT;
