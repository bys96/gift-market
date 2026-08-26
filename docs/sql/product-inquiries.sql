-- Product inquiry domain reference DDL.
-- Local development uses Hibernate ddl-auto:update. Do not run this file against a schema
-- where Hibernate has already created the same table.

CREATE TABLE product_inquiries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    is_private BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_inquiries_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_inquiries_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_product_inquiries_product_created (product_id, created_at),
    INDEX idx_product_inquiries_user (user_id),
    INDEX idx_product_inquiries_status_created (status, created_at)
);

CREATE TABLE product_inquiry_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inquiry_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_inquiry_answers_inquiry UNIQUE (inquiry_id),
    CONSTRAINT fk_product_inquiry_answers_inquiry FOREIGN KEY (inquiry_id) REFERENCES product_inquiries (id),
    CONSTRAINT fk_product_inquiry_answers_seller FOREIGN KEY (seller_id) REFERENCES sellers (id)
);
