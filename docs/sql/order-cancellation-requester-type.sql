-- Apply before deploying the backend when ddl-auto=validate is enabled.
ALTER TABLE order_cancellations
    ADD COLUMN requester_type VARCHAR(20) NULL;

UPDATE order_cancellations
SET requester_type = 'BUYER'
WHERE requester_type IS NULL;

ALTER TABLE order_cancellations
    MODIFY COLUMN requester_type VARCHAR(20) NOT NULL;
