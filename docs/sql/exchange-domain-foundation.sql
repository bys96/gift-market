-- Exchange 1 수동 적용용 MySQL DDL.
-- 운영 반영 전에는 versioned migration 도구로 옮겨 검증한다.

ALTER TABLE order_items
    ADD COLUMN exchanged_quantity INT NULL AFTER returned_quantity;

UPDATE order_items
SET exchanged_quantity = 0
WHERE exchanged_quantity IS NULL;

ALTER TABLE order_items
    MODIFY COLUMN exchanged_quantity INT NOT NULL DEFAULT 0;

CREATE TABLE exchange_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    seller_order_id BIGINT NOT NULL,
    client_request_key VARCHAR(100) NOT NULL,
    reason_type VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    responsibility VARCHAR(20) NULL,
    status VARCHAR(30) NOT NULL,
    collection_recipient_name VARCHAR(100) NOT NULL,
    collection_phone VARCHAR(30) NOT NULL,
    collection_postal_code VARCHAR(20) NOT NULL,
    collection_address VARCHAR(255) NOT NULL,
    collection_address_detail VARCHAR(255) NULL,
    reshipping_recipient_name VARCHAR(100) NOT NULL,
    reshipping_phone VARCHAR(30) NOT NULL,
    reshipping_postal_code VARCHAR(20) NOT NULL,
    reshipping_address VARCHAR(255) NOT NULL,
    reshipping_address_detail VARCHAR(255) NULL,
    collection_shipment_id BIGINT NULL,
    outbound_shipment_id BIGINT NULL,
    requested_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    payment_pending_at DATETIME(6) NULL,
    payment_due_at DATETIME(6) NULL,
    collecting_at DATETIME(6) NULL,
    received_at DATETIME(6) NULL,
    inspected_at DATETIME(6) NULL,
    reshipping_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    rejected_at DATETIME(6) NULL,
    canceled_at DATETIME(6) NULL,
    failed_at DATETIME(6) NULL,
    rejected_reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_exchange_requests_client_request_key UNIQUE (client_request_key),
    CONSTRAINT uk_exchange_requests_collection_shipment UNIQUE (collection_shipment_id),
    CONSTRAINT uk_exchange_requests_outbound_shipment UNIQUE (outbound_shipment_id),
    CONSTRAINT fk_exchange_requests_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_exchange_requests_seller_order FOREIGN KEY (seller_order_id) REFERENCES seller_orders (id),
    CONSTRAINT fk_exchange_requests_collection_shipment FOREIGN KEY (collection_shipment_id) REFERENCES shipments (id),
    CONSTRAINT fk_exchange_requests_outbound_shipment FOREIGN KEY (outbound_shipment_id) REFERENCES shipments (id),
    INDEX idx_exchange_requests_order_status (order_id, status),
    INDEX idx_exchange_requests_seller_order_status (seller_order_id, status)
);

CREATE TABLE exchange_request_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exchange_request_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    target_product_id BIGINT NOT NULL,
    target_variant_id BIGINT NULL,
    quantity INT NOT NULL,
    target_product_name VARCHAR(200) NOT NULL,
    target_option_snapshot VARCHAR(1000) NULL,
    target_unit_price BIGINT NOT NULL,
    reserved_quantity INT NOT NULL DEFAULT 0,
    released_quantity INT NOT NULL DEFAULT 0,
    consumed_quantity INT NOT NULL DEFAULT 0,
    inspection_result VARCHAR(30) NULL,
    restocked_quantity INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_exchange_request_items_request_item UNIQUE (exchange_request_id, order_item_id),
    CONSTRAINT fk_exchange_request_items_request FOREIGN KEY (exchange_request_id) REFERENCES exchange_requests (id),
    CONSTRAINT fk_exchange_request_items_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT fk_exchange_request_items_target_product FOREIGN KEY (target_product_id) REFERENCES products (id),
    CONSTRAINT fk_exchange_request_items_target_variant FOREIGN KEY (target_variant_id) REFERENCES product_variants (id),
    INDEX idx_exchange_request_items_order_item (order_item_id)
);

CREATE TABLE exchange_request_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exchange_request_id BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_exchange_request_images_request_object UNIQUE (exchange_request_id, object_key),
    CONSTRAINT fk_exchange_request_images_request FOREIGN KEY (exchange_request_id) REFERENCES exchange_requests (id),
    INDEX idx_exchange_request_images_request_sort (exchange_request_id, sort_order)
);
