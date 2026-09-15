-- Settlement v1 foundation.
-- 운영환경에서는 이 SQL을 먼저 적용한 뒤 ddl-auto=validate 애플리케이션을 배포한다.
-- 이 파일은 자동 실행 migration이 아니며 대상 DB에 중복 적용하지 않는다.

CREATE TABLE settlements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    settlement_number VARCHAR(50) NOT NULL,
    period_start DATETIME(6) NOT NULL,
    period_end DATETIME(6) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    total_product_sales_amount BIGINT NOT NULL,
    total_shipping_sales_amount BIGINT NOT NULL,
    total_cancellation_amount BIGINT NOT NULL,
    total_return_amount BIGINT NOT NULL,
    total_commission_amount BIGINT NOT NULL,
    total_adjustment_amount BIGINT NOT NULL,
    settlement_amount BIGINT NOT NULL,
    ledger_entry_count INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    hold_reason VARCHAR(500) NULL,
    held_at DATETIME(6) NULL,
    held_by_admin_user_id BIGINT NULL,
    hold_released_at DATETIME(6) NULL,
    hold_released_by_admin_user_id BIGINT NULL,
    confirmed_at DATETIME(6) NULL,
    confirmed_by_admin_user_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_settlements_number UNIQUE (settlement_number),
    CONSTRAINT uk_settlements_seller_period UNIQUE (seller_id, period_start, period_end),
    CONSTRAINT fk_settlements_seller
        FOREIGN KEY (seller_id) REFERENCES sellers (id),
    CONSTRAINT fk_settlements_confirmed_admin
        FOREIGN KEY (confirmed_by_admin_user_id) REFERENCES users (id),
    CONSTRAINT fk_settlements_held_admin
        FOREIGN KEY (held_by_admin_user_id) REFERENCES users (id),
    CONSTRAINT fk_settlements_released_admin
        FOREIGN KEY (hold_released_by_admin_user_id) REFERENCES users (id),
    INDEX idx_settlements_seller_period (seller_id, period_end, id),
    INDEX idx_settlements_status_period (status, period_end, id),
    INDEX idx_settlements_seller_status_period (seller_id, status, period_end)
);

CREATE TABLE settlement_ledger_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    seller_order_id BIGINT NOT NULL,
    settlement_id BIGINT NULL,
    type VARCHAR(40) NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id BIGINT NOT NULL,
    source_detail_key VARCHAR(100) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    eligible_at DATETIME(6) NULL,
    commission_rate_bps INT NULL,
    commission_base_amount BIGINT NULL,
    reason VARCHAR(500) NULL,
    admin_user_id BIGINT NULL,
    reversal_of_entry_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_settlement_ledger_source
        UNIQUE (source_type, source_id, source_detail_key),
    CONSTRAINT uk_settlement_ledger_reversal UNIQUE (reversal_of_entry_id),
    CONSTRAINT fk_settlement_ledger_seller
        FOREIGN KEY (seller_id) REFERENCES sellers (id),
    CONSTRAINT fk_settlement_ledger_seller_order
        FOREIGN KEY (seller_order_id) REFERENCES seller_orders (id),
    CONSTRAINT fk_settlement_ledger_settlement
        FOREIGN KEY (settlement_id) REFERENCES settlements (id),
    CONSTRAINT fk_settlement_ledger_admin
        FOREIGN KEY (admin_user_id) REFERENCES users (id),
    CONSTRAINT fk_settlement_ledger_reversal
        FOREIGN KEY (reversal_of_entry_id) REFERENCES settlement_ledger_entries (id),
    INDEX idx_settlement_ledger_seller_eligible
        (seller_id, settlement_id, eligible_at, id),
    INDEX idx_settlement_ledger_settlement_order
        (settlement_id, seller_order_id, id),
    INDEX idx_settlement_ledger_order_occurred
        (seller_order_id, occurred_at, id),
    INDEX idx_settlement_ledger_type_occurred
        (type, occurred_at),
    INDEX idx_settlement_ledger_source
        (source_type, source_id)
);
