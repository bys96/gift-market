-- SellerStore 운영 수동 migration 참고본 (MySQL 8.4)
-- 백업 후 현재 스키마를 확인하고 실행한다. Flyway 자동 migration이 아니다.
CREATE TABLE seller_stores (
    id BIGINT NOT NULL AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    store_name VARCHAR(30) NOT NULL,
    introduction VARCHAR(500) NULL,
    logo_image_key VARCHAR(500) NULL,
    banner_image_key VARCHAR(500) NULL,
    customer_service_phone VARCHAR(30) NULL,
    customer_service_email VARCHAR(255) NULL,
    customer_service_hours VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_seller_store_seller UNIQUE (seller_id),
    CONSTRAINT uk_seller_store_name UNIQUE (store_name),
    CONSTRAINT fk_seller_store_seller FOREIGN KEY (seller_id) REFERENCES sellers (id)
);

INSERT INTO seller_stores (seller_id, store_name, introduction, created_at, updated_at)
SELECT s.id, s.store_name, s.introduction, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM sellers s
WHERE NOT EXISTS (SELECT 1 FROM seller_stores ss WHERE ss.seller_id = s.id);

-- Rollback: 먼저 참조 검토 후 실행
-- DROP TABLE seller_stores;
