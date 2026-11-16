-- MANUAL MySQL 8.4 migration; all writers must be stopped.
-- Back up and restore-test the DB before running. DDL implicitly commits.
-- Run after product-variant-seller-sku.sql, before the new API starts.
DROP PROCEDURE IF EXISTS migrate_product_option_archive;
DELIMITER $$
CREATE PROCEDURE migrate_product_option_archive()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'product_option_groups' AND column_name = 'archived') THEN
        ALTER TABLE product_option_groups ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'product_option_values' AND column_name = 'archived') THEN
        ALTER TABLE product_option_values ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END$$
DELIMITER ;
CALL migrate_product_option_archive();
DROP PROCEDURE migrate_product_option_archive;
-- Re-running preserves archived states. A failed second ALTER does not undo
-- the first. Inspect information_schema, fix the cause, then rerun offline.
-- Do not reconstruct missing mappings from names or point history at new IDs.
