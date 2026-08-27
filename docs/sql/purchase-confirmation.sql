-- Purchase confirmation schema change reference.
-- Local development uses Hibernate ddl-auto:update. Do not run this file when the
-- column has already been created by Hibernate.

ALTER TABLE order_items
    ADD COLUMN confirmed_quantity INT NOT NULL DEFAULT 0 AFTER exchanged_quantity;

-- Existing rows remain unconfirmed. Validate before applying in an existing schema:
-- SELECT COUNT(*) FROM order_items WHERE confirmed_quantity < 0 OR confirmed_quantity > quantity;
