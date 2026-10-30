-- Manual DDL: apply before deploying the corresponding application.
-- Existing seller cancellations used the implicit full-order request mode.
ALTER TABLE order_cancellations
    ADD COLUMN seller_items_explicit BOOLEAN NOT NULL DEFAULT FALSE;
