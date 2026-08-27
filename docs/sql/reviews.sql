-- Review domain reference DDL. Do not run again when Hibernate ddl-auto already created these objects.
CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    option_snapshot VARCHAR(1000) NULL,
    unit_price_snapshot BIGINT NOT NULL,
    rating INT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reviews_order_item UNIQUE (order_item_id),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_reviews_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    INDEX idx_reviews_product_active_created (product_id, deleted_at, created_at),
    INDEX idx_reviews_user (user_id)
);

CREATE TABLE review_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    review_id BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_images_review_object UNIQUE (review_id, object_key),
    CONSTRAINT fk_review_images_review FOREIGN KEY (review_id) REFERENCES reviews (id),
    INDEX idx_review_images_review_sort (review_id, sort_order)
);
