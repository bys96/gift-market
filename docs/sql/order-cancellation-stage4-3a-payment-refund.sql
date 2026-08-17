-- Cancellation 4-3A manual migration reference (MySQL 8)
-- Do not execute before reviewing the current schema and taking a backup.

ALTER TABLE payment_cancellations
    ADD COLUMN order_cancellation_id BIGINT NULL,
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'FULL';

ALTER TABLE payment_cancellations
    ADD CONSTRAINT fk_payment_cancellations_order_cancellation
        FOREIGN KEY (order_cancellation_id) REFERENCES order_cancellations(id),
    ADD CONSTRAINT uk_payment_cancellations_order_cancellation
        UNIQUE (order_cancellation_id);

-- Existing full-cancellation rows remain FULL and have no commerce cancellation link.
UPDATE payment_cancellations
SET type = 'FULL'
WHERE type IS NULL;

-- Verification
SELECT COUNT(*) AS invalid_type_count
FROM payment_cancellations
WHERE type IS NULL OR type NOT IN ('FULL', 'PARTIAL');

SELECT order_cancellation_id, COUNT(*) AS duplicate_count
FROM payment_cancellations
WHERE order_cancellation_id IS NOT NULL
GROUP BY order_cancellation_id
HAVING COUNT(*) > 1;
