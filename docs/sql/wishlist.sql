-- Wishlist server domain reference DDL.
-- Local development currently uses Hibernate ddl-auto:update.
-- Do not execute this file automatically or duplicate tables created by Hibernate.

CREATE TABLE wishlist_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_wishlist_items_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_wishlist_items_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_wishlist_items_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    INDEX idx_wishlist_items_user_created_at (user_id, created_at)
);
