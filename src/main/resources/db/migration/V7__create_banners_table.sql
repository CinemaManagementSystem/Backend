-- V7: Create banners table for dynamic hero and page carousels
CREATE TABLE IF NOT EXISTS banners (
    id BIGSERIAL PRIMARY KEY,
    section VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    subtitle VARCHAR(500),
    image_url VARCHAR(1000) NOT NULL,
    image_public_id VARCHAR(255),
    link_url VARCHAR(1000),
    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_banners_section_active_sort ON banners (section, is_active, sort_order);
CREATE INDEX IF NOT EXISTS idx_banners_start_end_date ON banners (start_date, end_date);
