-- Cancellation 1단계 수동 DDL 참고본 (MySQL 8)
-- Hibernate ddl-auto=update 개발환경에서는 애플리케이션 시작 전후 스키마를 확인하고,
-- 이미 생성된 컬럼/테이블에는 이 스크립트를 중복 실행하지 않는다.

ALTER TABLE order_items
    ADD COLUMN canceled_quantity INT NOT NULL DEFAULT 0 AFTER quantity;

ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_canceled_quantity
        CHECK (canceled_quantity >= 0 AND canceled_quantity <= quantity);

CREATE TABLE order_cancellations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    seller_order_id BIGINT NOT NULL,
    client_request_key VARCHAR(100) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    processing_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    rejected_at DATETIME(6) NULL,
    failed_at DATETIME(6) NULL,
    rejected_reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_cancellations_client_request_key
        UNIQUE (client_request_key),
    CONSTRAINT fk_order_cancellations_order
        FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_cancellations_seller_order
        FOREIGN KEY (seller_order_id) REFERENCES seller_orders (id),
    INDEX idx_order_cancellations_order_status (order_id, status),
    INDEX idx_order_cancellations_seller_order_status (seller_order_id, status)
);

CREATE TABLE order_cancellation_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_cancellation_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_cancellation_items_cancellation_item
        UNIQUE (order_cancellation_id, order_item_id),
    CONSTRAINT fk_order_cancellation_items_cancellation
        FOREIGN KEY (order_cancellation_id) REFERENCES order_cancellations (id),
    CONSTRAINT fk_order_cancellation_items_order_item
        FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT chk_order_cancellation_items_quantity CHECK (quantity > 0),
    INDEX idx_order_cancellation_items_order_item (order_item_id)
);

-- 적용 후 검증
SELECT COUNT(*) AS invalid_canceled_quantity_count
FROM order_items
WHERE canceled_quantity IS NULL
   OR canceled_quantity < 0
   OR canceled_quantity > quantity;

SELECT COUNT(*) AS seller_order_mismatch_count
FROM order_cancellation_items oci
JOIN order_cancellations oc ON oc.id = oci.order_cancellation_id
JOIN order_items oi ON oi.id = oci.order_item_id
WHERE oc.seller_order_id <> oi.seller_order_id;

SELECT order_cancellation_id, order_item_id, COUNT(*) AS duplicate_count
FROM order_cancellation_items
GROUP BY order_cancellation_id, order_item_id
HAVING COUNT(*) > 1;
