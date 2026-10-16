-- Gift Occasion master and product relation DDL for the production MySQL database.
-- Apply this file before deploying an application version that maps these tables.
-- Both CREATE TABLE statements are guarded, and the seed uses INSERT IGNORE.
-- Review an existing schema before rerunning: IF NOT EXISTS does not reconcile a different table definition.

CREATE TABLE IF NOT EXISTS gift_occasions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    display_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_gift_occasions_name UNIQUE (name),
    INDEX idx_gift_occasions_active_display_order (active, display_order)
);

CREATE TABLE IF NOT EXISTS product_gift_occasions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    gift_occasion_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_gift_occasions_product_occasion
        UNIQUE (product_id, gift_occasion_id),
    CONSTRAINT fk_product_gift_occasions_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_gift_occasions_occasion
        FOREIGN KEY (gift_occasion_id) REFERENCES gift_occasions (id),
    INDEX idx_product_gift_occasions_occasion_id (gift_occasion_id)
);

-- Physical deletion is intentionally not cascaded. Gift Occasions are retired with active = FALSE.
INSERT IGNORE INTO gift_occasions (name, active, display_order, created_at, updated_at) VALUES
    ('생일', TRUE, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('감사', TRUE, 2, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('축하', TRUE, 3, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('응원', TRUE, 4, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('집들이', TRUE, 5, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('기념일', TRUE, 6, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('출산·육아', TRUE, 7, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('가벼운 선물', TRUE, 8, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
