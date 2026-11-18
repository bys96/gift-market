-- MySQL 8.4 MANUAL migration. Never run against production from an agent.
-- Stop ALL API, scheduler, reconciliation, webhook and external writers first.
-- Back up and restore-test the database, then run the preflight SELECT file.
-- Existing seller_sku_code + option archive migrations must already be applied.
-- No DDL on business tables. Product stock remains a display cache.
-- Copy CURRENT available stock only: it already excludes reserved/sold quantities.
-- This procedure never replays reservations, refunds or cancellations.
-- Use a fresh dedicated connection with no pending transaction (routine DDL
-- commits implicitly). Do not use --force. No schema/data repair is performed.
-- The operator must have metadata visibility (including TRIGGER privilege).
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_product_default_variant$$
CREATE PROCEDURE migrate_product_default_variant()
BEGIN
    DECLARE EXIT HANDLER FOR SQLWARNING
    BEGIN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SQL warning: migration rolled back; review schema/session settings';
    END;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    START TRANSACTION;

    -- Rollback/FK-only guarantees require the actual deployed schema, not merely
    -- the JPA declaration. Accept equivalent index names, never prefix indexes.
    IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
          AND table_name IN ('product_variants','order_items','cart_items','exchange_request_items')
          AND engine='InnoDB')<>4 OR @@SESSION.foreign_key_checks<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema safety: require InnoDB tables and foreign_key_checks=1';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.triggers WHERE trigger_schema=DATABASE()
          AND event_object_table IN ('product_variants','order_items','cart_items','exchange_request_items'))
       OR EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
          AND table_name IN ('product_variants','order_items','cart_items','exchange_request_items')
          AND LOWER(extra) LIKE '%on update%') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema side effects: review triggers/ON UPDATE columns before migration';
    END IF;

    IF EXISTS (SELECT 1 FROM product_variants WHERE combination_key='__DEFAULT__'
               GROUP BY product_id HAVING COUNT(*)>1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate default variants: report product/variant IDs; no automatic merge';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
          WHERE table_schema=DATABASE() AND table_name='product_variants'
          GROUP BY index_name HAVING MAX(non_unique)=0 AND COUNT(*)=2
            AND SUM(seq_in_index=1 AND column_name='product_id' AND sub_part IS NULL)=1
            AND SUM(seq_in_index=2 AND column_name='combination_key' AND sub_part IS NULL)=1)
       OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
          WHERE table_schema=DATABASE() AND table_name='product_variants'
          GROUP BY index_name HAVING MAX(non_unique)=0 AND COUNT(*)=2
            AND SUM(seq_in_index=1 AND column_name='product_id' AND sub_part IS NULL)=1
            AND SUM(seq_in_index=2 AND column_name='seller_sku_code' AND sub_part IS NULL)=1)
       OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
          WHERE table_schema=DATABASE() AND table_name='product_variants'
          GROUP BY index_name HAVING MAX(non_unique)=0 AND COUNT(*)=1
            AND SUM(seq_in_index=1 AND column_name='sku_code' AND sub_part IS NULL)=1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing full UNIQUE: product/combination, product/seller SKU, or system SKU';
    END IF;

    IF EXISTS (SELECT 1 FROM (
          SELECT 'product_variants' AS child_table,'product_id' AS child_column,'products' AS parent_table
          UNION ALL SELECT 'order_items','variant_id','product_variants'
          UNION ALL SELECT 'cart_items','variant_id','product_variants'
          UNION ALL SELECT 'exchange_request_items','target_variant_id','product_variants'
        ) required_fk WHERE NOT EXISTS (
          SELECT 1 FROM information_schema.key_column_usage k
          WHERE k.table_schema=DATABASE() AND k.table_name=required_fk.child_table
            AND k.column_name=required_fk.child_column AND k.ordinal_position=1
            AND k.referenced_table_schema=DATABASE() AND k.referenced_table_name=required_fk.parent_table
            AND k.referenced_column_name='id'
            AND NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage extra
              WHERE extra.constraint_schema=k.constraint_schema AND extra.table_name=k.table_name
                AND extra.constraint_name=k.constraint_name AND extra.ordinal_position>1))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing expected Product/Variant FK: review deployed JPA schema';
    END IF;

    IF EXISTS (SELECT 1 FROM product_variants v LEFT JOIN products p ON p.id=v.product_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM order_items i LEFT JOIN products p ON p.id=i.product_id
                   LEFT JOIN product_variants v ON v.id=i.variant_id
                   WHERE p.id IS NULL OR (i.variant_id IS NOT NULL AND v.id IS NULL))
       OR EXISTS (SELECT 1 FROM cart_items c LEFT JOIN products p ON p.id=c.product_id
                   LEFT JOIN product_variants v ON v.id=c.variant_id
                   WHERE p.id IS NULL OR (c.variant_id IS NOT NULL AND v.id IS NULL))
       OR EXISTS (SELECT 1 FROM exchange_request_items e LEFT JOIN products p ON p.id=e.target_product_id
                   LEFT JOIN order_items i ON i.id=e.order_item_id
                   LEFT JOIN product_variants v ON v.id=e.target_variant_id
                   WHERE p.id IS NULL OR i.id IS NULL OR (e.target_variant_id IS NOT NULL AND v.id IS NULL)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Orphan product/order/variant reference: inspect preflight IDs';
    END IF;

    IF EXISTS (SELECT 1 FROM products WHERE stock_quantity IS NULL OR stock_quantity < 0)
       OR EXISTS (SELECT 1 FROM product_variants WHERE stock_quantity IS NULL OR stock_quantity < 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid inventory: inspect preflight results; no automatic repair';
    END IF;
    IF EXISTS (SELECT 1 FROM product_variants WHERE sku_code IS NULL OR seller_sku_code IS NULL
                  OR combination_key IS NULL OR additional_price IS NULL OR active IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'NULL Variant identity/price/state: inspect preflight IDs';
    END IF;

    IF EXISTS (SELECT 1 FROM product_variant_option_values m
          LEFT JOIN product_variants v ON v.id=m.variant_id
          LEFT JOIN product_option_values ov ON ov.id=m.option_value_id
          LEFT JOIN product_option_groups g ON g.id=ov.option_group_id
          WHERE v.id IS NULL OR ov.id IS NULL OR g.id IS NULL OR g.product_id<>v.product_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Orphan/wrong-owner option mapping: inspect preflight IDs';
    END IF;

    -- A no-variant product with any active/archived options is not safely classifiable.
    IF EXISTS (SELECT 1 FROM products p
               WHERE NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=p.id)
                 AND EXISTS (SELECT 1 FROM product_option_groups g WHERE g.product_id=p.id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Options without variants: inspect preflight results';
    END IF;

    IF EXISTS (SELECT 1 FROM product_variants v WHERE v.combination_key<>'__DEFAULT__' AND (
          NOT REGEXP_LIKE(v.combination_key,'^[1-9][0-9]*:[1-9][0-9]*([|][1-9][0-9]*:[1-9][0-9]*)*$')
          OR 1+LENGTH(v.combination_key)-LENGTH(REPLACE(v.combination_key,'|',''))<>
             (SELECT COUNT(*) FROM product_variant_option_values m WHERE m.variant_id=v.id)
          OR (SELECT COUNT(*) FROM product_variant_option_values m WHERE m.variant_id=v.id)<>
             (SELECT COUNT(DISTINCT ov.option_group_id) FROM product_variant_option_values m
              JOIN product_option_values ov ON ov.id=m.option_value_id WHERE m.variant_id=v.id)
          OR EXISTS (SELECT 1 FROM product_variant_option_values m
              LEFT JOIN product_option_values ov ON ov.id=m.option_value_id
              LEFT JOIN product_option_groups g ON g.id=ov.option_group_id
              WHERE m.variant_id=v.id AND (g.id IS NULL OR g.product_id<>v.product_id OR
                FIND_IN_SET(CONCAT(g.id,':',ov.id),REPLACE(v.combination_key,'|',','))=0)))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Variant mapping mismatch: run product-sku-reconciliation.sql';
    END IF;

    IF EXISTS (SELECT 1 FROM order_items i LEFT JOIN product_variants v ON v.id=i.variant_id
               WHERE (i.variant_id IS NULL OR v.combination_key='__DEFAULT__')
                 AND (COALESCE(i.additional_price,0) <> 0 OR NULLIF(TRIM(i.option_snapshot),'') IS NOT NULL))
       OR EXISTS (SELECT 1 FROM order_items i JOIN product_variants v ON v.id=i.variant_id
                  WHERE v.product_id<>i.product_id)
       OR EXISTS (SELECT 1 FROM order_items i WHERE i.variant_id IS NULL
                    AND EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=i.product_id)
                    AND NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=i.product_id AND v.combination_key='__DEFAULT__')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous order inventory owner: inspect preflight results';
    END IF;

    IF EXISTS (SELECT 1 FROM product_variants v WHERE v.combination_key='__DEFAULT__'
                  AND (CAST(v.combination_key AS BINARY)<>CAST('__DEFAULT__' AS BINARY)
                       OR v.additional_price<>0
                       OR CAST(v.seller_sku_code AS BINARY)<>CAST(CONCAT('DEFAULT-',v.product_id) AS BINARY)
                       OR NOT REGEXP_LIKE(v.sku_code,'^GM-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$','c')
                       OR (v.active=true AND (EXISTS (SELECT 1 FROM product_variants o WHERE o.product_id=v.product_id AND o.combination_key<>'__DEFAULT__')
                           OR EXISTS (SELECT 1 FROM product_option_groups g WHERE g.product_id=v.product_id)))
                       OR EXISTS (SELECT 1 FROM product_variant_option_values m WHERE m.variant_id=v.id))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Reserved default identity is inconsistent: inspect preflight results';
    END IF;

    IF EXISTS (SELECT 1 FROM products p
               WHERE EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=p.id)
                 AND p.stock_quantity<>(SELECT COALESCE(SUM(v.stock_quantity),0) FROM product_variants v WHERE v.product_id=p.id AND v.active=true)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Product/variant stock cache mismatch: inspect preflight results';
    END IF;

    -- Do not merge/delete cart IDs: pending orders may reference source_cart_item_id.
    IF EXISTS (SELECT 1 FROM cart_items c
               WHERE (c.variant_id IS NULL OR EXISTS (SELECT 1 FROM product_variants v WHERE v.id=c.variant_id AND v.combination_key='__DEFAULT__'))
               GROUP BY c.user_id,c.product_id HAVING COUNT(*)>1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate default cart rows: report IDs; no automatic merge';
    END IF;

    IF EXISTS (SELECT 1 FROM cart_items c JOIN product_variants v ON v.id=c.variant_id WHERE v.product_id<>c.product_id)
       OR EXISTS (SELECT 1 FROM cart_items c WHERE c.variant_id IS NULL
                    AND EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=c.product_id)
                    AND NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=c.product_id AND v.combination_key='__DEFAULT__')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous cart inventory owner: inspect preflight results';
    END IF;

    IF EXISTS (SELECT 1 FROM exchange_request_items e JOIN order_items i ON i.id=e.order_item_id
               LEFT JOIN product_variants original ON original.id=i.variant_id
               LEFT JOIN product_variants target ON target.id=e.target_variant_id
               WHERE e.target_product_id<>i.product_id
                  OR ((i.variant_id IS NULL OR original.combination_key='__DEFAULT__')
                       <> (e.target_variant_id IS NULL OR target.combination_key='__DEFAULT__'))
                  OR (target.combination_key='__DEFAULT__' AND NULLIF(TRIM(e.target_option_snapshot),'') IS NOT NULL)
                  OR EXISTS (SELECT 1 FROM product_variants v WHERE v.id=e.target_variant_id AND v.product_id<>e.target_product_id)
                  OR (e.target_variant_id IS NULL AND EXISTS
                      (SELECT 1 FROM product_variants v WHERE v.id=i.variant_id AND v.combination_key<>'__DEFAULT__'))
                  OR (e.target_variant_id IS NULL AND
                      (NULLIF(TRIM(e.target_option_snapshot),'') IS NOT NULL
                       OR EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=e.target_product_id)
                          AND NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=e.target_product_id AND v.combination_key='__DEFAULT__')))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous exchange inventory owner: inspect preflight results';
    END IF;

    INSERT INTO product_variants
        (product_id,sku_code,seller_sku_code,combination_key,additional_price,stock_quantity,active,version,created_at,updated_at)
    SELECT p.id,CONCAT('GM-',UUID()),CONCAT('DEFAULT-',p.id),'__DEFAULT__',0,p.stock_quantity,true,0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)
      FROM products p
     WHERE NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=p.id)
       AND NOT EXISTS (SELECT 1 FROM product_option_groups g WHERE g.product_id=p.id);

    -- FK-only backfill, including cancelled/failed/refunded orders. No snapshot,
    -- amount, quantity, restoration flag, payment or claim row is changed.
    UPDATE order_items i JOIN product_variants v ON v.product_id=i.product_id AND v.combination_key='__DEFAULT__'
       SET i.variant_id=v.id WHERE i.variant_id IS NULL;
    UPDATE exchange_request_items e JOIN product_variants v ON v.product_id=e.target_product_id AND v.combination_key='__DEFAULT__'
       SET e.target_variant_id=v.id WHERE e.target_variant_id IS NULL;
    UPDATE cart_items c JOIN product_variants v ON v.product_id=c.product_id AND v.combination_key='__DEFAULT__'
       SET c.variant_id=v.id WHERE c.variant_id IS NULL;
    COMMIT;
END$$
CALL migrate_product_default_variant()$$
DROP PROCEDURE migrate_product_default_variant$$
DELIMITER ;
