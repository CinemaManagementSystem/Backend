-- Migration: Add image_url to theaters table
ALTER TABLE theaters ADD COLUMN IF NOT EXISTS image_url VARCHAR(1000);
