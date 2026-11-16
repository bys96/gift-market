-- MySQL 8.4; MANUAL, offline migration. See ../SKU_DESIGN.md.
-- Stop ALL old/new API instances and writers; take and verify a backup first.
-- DDL commits implicitly. On failure fix the cause and rerun; never drop data.
-- Then apply product-option-archive.sql before starting the audited API.
-- Legacy skuCode-only write requests are rejected by the new API (HTTP 409).
DROP PROCEDURE IF EXISTS migrate_product_variant_seller_sku;
DELIMITER $$
CREATE PROCEDURE migrate_product_variant_seller_sku()
BEGIN
    DECLARE column_exists INT DEFAULT 0;
    DECLARE index_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO column_exists FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'product_variants'
        AND column_name = 'seller_sku_code';

    IF column_exists = 0 THEN
        -- Old service already canonicalizes to trim + ROOT uppercase. Unexpected
        -- imported/noncanonical data requires review, never silent rewriting.
        IF EXISTS (SELECT 1 FROM product_variants
                   WHERE sku_code IS NULL OR CHAR_LENGTH(TRIM(sku_code)) = 0
                      OR BINARY sku_code <> BINARY UPPER(TRIM(sku_code))) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Noncanonical legacy SKU: review before migration';
        END IF;
        -- Reserve this namespace before enabling the new writer. This also
        -- prevents legacy codes colliding with any newly generated UUID SKU.
        IF EXISTS (SELECT 1 FROM product_variants WHERE sku_code LIKE 'GM-%') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy GM- namespace found: review before migration';
        END IF;
        ALTER TABLE product_variants ADD COLUMN seller_sku_code VARCHAR(100)
            CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci NULL;
    END IF;

    -- Only fill missing values: reruns preserve seller edits and system UUIDs.
    UPDATE product_variants SET seller_sku_code = sku_code WHERE seller_sku_code IS NULL;

    IF EXISTS (SELECT 1 FROM product_variants
               WHERE seller_sku_code IS NULL OR CHAR_LENGTH(TRIM(seller_sku_code)) = 0
                  OR BINARY seller_sku_code <> BINARY UPPER(TRIM(seller_sku_code))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Noncanonical seller SKU: review before adding constraints';
    END IF;
    IF EXISTS (SELECT 1 FROM product_variants GROUP BY product_id,
               UPPER(TRIM(seller_sku_code)) COLLATE utf8mb4_0900_as_ci HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate seller SKU within product (including inactive)';
    END IF;
    ALTER TABLE product_variants MODIFY COLUMN seller_sku_code VARCHAR(100)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci NOT NULL;
    SELECT COUNT(*) INTO index_exists FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'product_variants'
        AND index_name = 'uk_product_variants_product_seller_sku';
    IF index_exists = 0 THEN
        ALTER TABLE product_variants ADD CONSTRAINT uk_product_variants_product_seller_sku
            UNIQUE (product_id, seller_sku_code);
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'product_variants'
          AND index_name = 'uk_product_variants_product_seller_sku' AND non_unique = 0
          AND sub_part IS NULL
          AND ((seq_in_index = 1 AND column_name = 'product_id')
            OR (seq_in_index = 2 AND column_name = 'seller_sku_code'))) <> 2
       OR (SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'product_variants'
          AND index_name = 'uk_product_variants_product_seller_sku') <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unexpected seller SKU index definition: review required';
    END IF;
END$$
DELIMITER ;
CALL migrate_product_variant_seller_sku();
DROP PROCEDURE migrate_product_variant_seller_sku;
