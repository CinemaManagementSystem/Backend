-- Store the Cloudinary public ID so theater images can be replaced or removed.
ALTER TABLE theaters
    ADD COLUMN IF NOT EXISTS image_public_id VARCHAR(500);
