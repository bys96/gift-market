-- Exchange 4: 구매자 귀책 교환배송비 별도 결제 aggregate.
-- ddl-auto:update 개발 환경에서는 Entity 자동 반영과 이 SQL을 중복 적용하지 않는다.
CREATE TABLE exchange_shipping_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NULL,
    exchange_request_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_payment_key VARCHAR(200) NULL,
    provider_order_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    attempt_sequence INT NOT NULL,
    provider_status VARCHAR(100) NULL,
    requested_at DATETIME(6) NULL,
    succeeded_at DATETIME(6) NULL,
    failed_at DATETIME(6) NULL,
    expired_at DATETIME(6) NULL,
    failure_code VARCHAR(100) NULL,
    failure_message VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_exchange_shipping_payments_request UNIQUE (exchange_request_id),
    CONSTRAINT uk_exchange_shipping_payments_order_id UNIQUE (provider_order_id),
    CONSTRAINT uk_exchange_shipping_payments_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_exchange_shipping_payments_request FOREIGN KEY (exchange_request_id) REFERENCES exchange_requests (id),
    INDEX idx_exchange_shipping_payments_status_requested (status, requested_at)
);
