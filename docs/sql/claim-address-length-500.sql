-- Manual DDL: apply before deploying the corresponding application.
-- Expands existing columns without truncating stored values.
ALTER TABLE return_requests MODIFY COLUMN collection_address VARCHAR(500) NOT NULL, MODIFY COLUMN collection_address_detail VARCHAR(500) NULL;
ALTER TABLE exchange_requests MODIFY COLUMN collection_address VARCHAR(500) NOT NULL, MODIFY COLUMN collection_address_detail VARCHAR(500) NULL, MODIFY COLUMN reshipping_address VARCHAR(500) NOT NULL, MODIFY COLUMN reshipping_address_detail VARCHAR(500) NULL;
