-- READ ONLY. Run on a restored isolated backup first, then before/after migration
-- in a writer-free maintenance window. Save results externally for comparison.
-- Any anomaly result means STOP, report IDs/reason, investigate without repair.
-- INVENTORY/SNAPSHOT/SCHEMA result sets are baselines, not anomaly lists.
-- AFTER-only queries at the end may legitimately return rows BEFORE migration.
-- Compare BEFORE/AFTER on the SAME connection/backup with ALL writers stopped.
-- Operator privileges must expose FK/index/trigger metadata (TRIGGER privilege
-- is required to see triggers). Restricted metadata visibility is not proof
-- that triggers/constraints are absent. Do not run with restricted visibility.

-- SCHEMA: no business-table DDL is performed by the migration.
SELECT table_name,engine FROM information_schema.tables WHERE table_schema=DATABASE()
 AND table_name IN ('product_variants','order_items','cart_items','exchange_request_items');
SELECT @@SESSION.foreign_key_checks AS foreign_key_checks;
SELECT required_table.name,'MISSING_OR_NON_INNODB_TABLE' AS reason FROM (
 SELECT 'product_variants' AS name UNION ALL SELECT 'order_items'
 UNION ALL SELECT 'cart_items' UNION ALL SELECT 'exchange_request_items'
) required_table LEFT JOIN information_schema.tables t ON t.table_schema=DATABASE() AND t.table_name=required_table.name
WHERE t.table_name IS NULL OR t.engine<>'InnoDB';
SELECT 'FOREIGN_KEY_CHECKS_DISABLED' AS reason WHERE @@SESSION.foreign_key_checks<>1;
SELECT table_name,index_name,non_unique,seq_in_index,column_name,sub_part
FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='product_variants'
ORDER BY index_name,seq_in_index;
-- ANOMALY: required full-column UNIQUE is absent (equivalent index names allowed).
SELECT required_index.kind,'MISSING_FULL_UNIQUE' AS reason FROM (
 SELECT 'PRODUCT_COMBINATION' AS kind,'product_id' AS first_column,'combination_key' AS second_column
 UNION ALL SELECT 'PRODUCT_SELLER_SKU','product_id','seller_sku_code'
 UNION ALL SELECT 'SYSTEM_SKU','sku_code',NULL
) required_index WHERE NOT EXISTS (
 SELECT 1 FROM information_schema.statistics s WHERE s.table_schema=DATABASE() AND s.table_name='product_variants'
 GROUP BY s.index_name HAVING MAX(s.non_unique)=0
 AND COUNT(*)=IF(required_index.second_column IS NULL,1,2)
 AND SUM(s.seq_in_index=1 AND s.column_name=required_index.first_column AND s.sub_part IS NULL)=1
 AND (required_index.second_column IS NULL OR SUM(s.seq_in_index=2 AND s.column_name=required_index.second_column AND s.sub_part IS NULL)=1));
SELECT table_name,column_name,constraint_name,referenced_table_name,referenced_column_name
FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND referenced_table_name IS NOT NULL
 AND table_name IN ('product_variants','order_items','cart_items','exchange_request_items')
ORDER BY table_name,constraint_name,ordinal_position;
SELECT required_fk.child_table,required_fk.child_column,required_fk.parent_table,'MISSING_EXPECTED_FK' AS reason FROM (
 SELECT 'product_variants' AS child_table,'product_id' AS child_column,'products' AS parent_table
 UNION ALL SELECT 'order_items','variant_id','product_variants'
 UNION ALL SELECT 'cart_items','variant_id','product_variants'
 UNION ALL SELECT 'exchange_request_items','target_variant_id','product_variants'
) required_fk WHERE NOT EXISTS (
 SELECT 1 FROM information_schema.key_column_usage k WHERE k.table_schema=DATABASE() AND k.table_name=required_fk.child_table
 AND k.column_name=required_fk.child_column AND k.ordinal_position=1
 AND k.referenced_table_schema=DATABASE() AND k.referenced_table_name=required_fk.parent_table AND k.referenced_column_name='id'
 AND NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage extra WHERE extra.constraint_schema=k.constraint_schema
     AND extra.table_name=k.table_name AND extra.constraint_name=k.constraint_name AND extra.ordinal_position>1));
SELECT event_object_table,trigger_name,'TRIGGER_REVIEW_REQUIRED' AS reason FROM information_schema.triggers
WHERE trigger_schema=DATABASE() AND event_object_table IN ('product_variants','order_items','cart_items','exchange_request_items');
SELECT table_name,column_name,extra,'ON_UPDATE_REVIEW_REQUIRED' AS reason FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN ('product_variants','order_items','cart_items','exchange_request_items')
 AND LOWER(extra) LIKE '%on update%';

-- ANOMALY: all IDs are individual rows (GROUP_CONCAT can truncate ID lists).
SELECT v.product_id,v.id AS variant_id,v.active,v.stock_quantity,'DUPLICATE_DEFAULT' AS reason
FROM product_variants v WHERE v.combination_key='__DEFAULT__'
 AND (SELECT COUNT(*) FROM product_variants d WHERE d.product_id=v.product_id AND d.combination_key='__DEFAULT__')>1
ORDER BY v.product_id,v.id;

SELECT 'VARIANT' AS source,v.id,v.product_id,'ORPHAN_PRODUCT' AS reason
FROM product_variants v LEFT JOIN products p ON p.id=v.product_id WHERE p.id IS NULL
UNION ALL
SELECT 'ORDER',i.id,i.product_id,'ORPHAN_PRODUCT_OR_VARIANT' FROM order_items i
LEFT JOIN products p ON p.id=i.product_id LEFT JOIN product_variants v ON v.id=i.variant_id
WHERE p.id IS NULL OR (i.variant_id IS NOT NULL AND v.id IS NULL)
UNION ALL
SELECT 'CART',c.id,c.product_id,'ORPHAN_PRODUCT_OR_VARIANT' FROM cart_items c
LEFT JOIN products p ON p.id=c.product_id LEFT JOIN product_variants v ON v.id=c.variant_id
WHERE p.id IS NULL OR (c.variant_id IS NOT NULL AND v.id IS NULL)
UNION ALL
SELECT 'EXCHANGE',e.id,e.target_product_id,'ORPHAN_ORDER_PRODUCT_OR_VARIANT' FROM exchange_request_items e
LEFT JOIN products p ON p.id=e.target_product_id LEFT JOIN order_items i ON i.id=e.order_item_id
LEFT JOIN product_variants v ON v.id=e.target_variant_id
WHERE p.id IS NULL OR i.id IS NULL OR (e.target_variant_id IS NOT NULL AND v.id IS NULL);

-- INVENTORY baseline: Product fields must be identical before/after. Existing
-- Variant totals remain identical; for a new default only, totals become the
-- original Product stock. Never add reserved or previously refunded quantities.
SELECT p.id,p.stock_quantity,p.status,p.version,p.created_at,p.updated_at,
 (SELECT COUNT(*) FROM product_variants v WHERE v.product_id=p.id) AS variant_count,
 (SELECT COALESCE(SUM(v.stock_quantity),0) FROM product_variants v WHERE v.product_id=p.id AND v.active=true) AS active_stock,
 (SELECT COALESCE(SUM(v.stock_quantity),0) FROM product_variants v WHERE v.product_id=p.id) AS retained_stock
FROM products p ORDER BY p.id;

SELECT m.variant_id,m.option_value_id,ov.option_group_id,'ORPHAN_OR_WRONG_OWNER_OPTION_MAPPING' AS reason
FROM product_variant_option_values m LEFT JOIN product_variants v ON v.id=m.variant_id
LEFT JOIN product_option_values ov ON ov.id=m.option_value_id
LEFT JOIN product_option_groups g ON g.id=ov.option_group_id
WHERE v.id IS NULL OR ov.id IS NULL OR g.id IS NULL OR g.product_id<>v.product_id;
SELECT v.id AS variant_id,v.product_id,v.combination_key,'INVALID_OPTION_MAPPING' AS reason
FROM product_variants v WHERE v.combination_key<>'__DEFAULT__' AND (
 NOT REGEXP_LIKE(v.combination_key,'^[1-9][0-9]*:[1-9][0-9]*([|][1-9][0-9]*:[1-9][0-9]*)*$')
 OR 1+LENGTH(v.combination_key)-LENGTH(REPLACE(v.combination_key,'|',''))<>
    (SELECT COUNT(*) FROM product_variant_option_values m WHERE m.variant_id=v.id)
 OR (SELECT COUNT(*) FROM product_variant_option_values m WHERE m.variant_id=v.id)<>
    (SELECT COUNT(DISTINCT ov.option_group_id) FROM product_variant_option_values m
     JOIN product_option_values ov ON ov.id=m.option_value_id WHERE m.variant_id=v.id)
 OR EXISTS (SELECT 1 FROM product_variant_option_values m
    LEFT JOIN product_option_values ov ON ov.id=m.option_value_id LEFT JOIN product_option_groups g ON g.id=ov.option_group_id
    WHERE m.variant_id=v.id AND (g.id IS NULL OR g.product_id<>v.product_id
       OR FIND_IN_SET(CONCAT(g.id,':',ov.id),REPLACE(v.combination_key,'|',','))=0)));

-- Candidate inventory and current reserved order quantities (do NOT add them).
SELECT p.id,p.stock_quantity,p.status,
       (SELECT COALESCE(SUM(i.quantity),0) FROM order_items i JOIN orders o ON o.id=i.order_id
         WHERE i.product_id=p.id AND o.status='PENDING_PAYMENT') AS pending_quantity
FROM products p WHERE NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=p.id);

-- Anomalies: invalid stock, groups without variants, mixed legacy history.
SELECT p.id,p.stock_quantity,'INVALID_STOCK' AS reason FROM products p WHERE p.stock_quantity<0 OR p.stock_quantity IS NULL
UNION ALL
SELECT p.id,p.stock_quantity,'OPTIONS_WITHOUT_VARIANTS' FROM products p
WHERE NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=p.id)
  AND EXISTS (SELECT 1 FROM product_option_groups g WHERE g.product_id=p.id)
UNION ALL
SELECT p.id,p.stock_quantity,'MIXED_NULL_ORDER_HISTORY' FROM products p
WHERE EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=p.id)
  AND NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=p.id AND v.combination_key='__DEFAULT__')
  AND EXISTS (SELECT 1 FROM order_items i WHERE i.product_id=p.id AND i.variant_id IS NULL);

SELECT i.id,i.product_id,i.variant_id,i.option_snapshot,i.additional_price,'INVALID_ORDER_INVENTORY' AS reason FROM order_items i
LEFT JOIN product_variants v ON v.id=i.variant_id
WHERE ((i.variant_id IS NULL OR v.combination_key='__DEFAULT__') AND (COALESCE(i.additional_price,0)<>0 OR NULLIF(TRIM(i.option_snapshot),'') IS NOT NULL))
   OR (i.variant_id IS NOT NULL AND (v.id IS NULL OR v.product_id<>i.product_id));

SELECT v.*,'INVALID_VARIANT_OR_DEFAULT' AS reason FROM product_variants v WHERE v.stock_quantity<0 OR v.stock_quantity IS NULL
 OR v.sku_code IS NULL OR v.seller_sku_code IS NULL OR v.combination_key IS NULL OR v.additional_price IS NULL OR v.active IS NULL
 OR (v.combination_key='__DEFAULT__' AND (CAST(v.combination_key AS BINARY)<>CAST('__DEFAULT__' AS BINARY) OR v.additional_price<>0
     OR CAST(v.seller_sku_code AS BINARY)<>CAST(CONCAT('DEFAULT-',v.product_id) AS BINARY)
     OR NOT REGEXP_LIKE(v.sku_code,'^GM-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$','c')
     OR (v.active=true AND (EXISTS (SELECT 1 FROM product_variants o WHERE o.product_id=v.product_id AND o.combination_key<>'__DEFAULT__')
         OR EXISTS (SELECT 1 FROM product_option_groups g WHERE g.product_id=v.product_id)))
     OR EXISTS (SELECT 1 FROM product_variant_option_values m WHERE m.variant_id=v.id)));

SELECT p.id,p.stock_quantity,COALESCE(SUM(IF(v.active,v.stock_quantity,0)),0) AS expected_cache
FROM products p JOIN product_variants v ON v.product_id=p.id GROUP BY p.id,p.stock_quantity
HAVING p.stock_quantity<>expected_cache;

SELECT c.id,c.user_id,c.product_id,c.variant_id,'DUPLICATE_DEFAULT_CART' AS reason FROM cart_items c
LEFT JOIN product_variants v ON v.id=c.variant_id
WHERE (c.variant_id IS NULL OR v.combination_key='__DEFAULT__') AND (
 SELECT COUNT(*) FROM cart_items other LEFT JOIN product_variants ov ON ov.id=other.variant_id
 WHERE other.user_id=c.user_id AND other.product_id=c.product_id
   AND (other.variant_id IS NULL OR ov.combination_key='__DEFAULT__'))>1 ORDER BY c.user_id,c.product_id,c.id;

SELECT c.id,c.user_id,c.product_id,c.variant_id,'INVALID_CART_INVENTORY' AS reason FROM cart_items c
LEFT JOIN product_variants v ON v.id=c.variant_id
WHERE (c.variant_id IS NOT NULL AND (v.id IS NULL OR v.product_id<>c.product_id))
 OR (c.variant_id IS NULL AND EXISTS (SELECT 1 FROM product_variants pv WHERE pv.product_id=c.product_id)
     AND NOT EXISTS (SELECT 1 FROM product_variants pv WHERE pv.product_id=c.product_id AND pv.combination_key='__DEFAULT__'));

SELECT e.id,e.order_item_id,e.target_product_id,e.target_variant_id,e.target_option_snapshot,'INVALID_EXCHANGE_INVENTORY' AS reason
FROM exchange_request_items e JOIN order_items i ON i.id=e.order_item_id
LEFT JOIN product_variants original ON original.id=i.variant_id
LEFT JOIN product_variants target ON target.id=e.target_variant_id
WHERE e.target_product_id<>i.product_id
 OR ((i.variant_id IS NULL OR original.combination_key='__DEFAULT__')
     <> (e.target_variant_id IS NULL OR target.combination_key='__DEFAULT__'))
 OR (target.combination_key='__DEFAULT__' AND NULLIF(TRIM(e.target_option_snapshot),'') IS NOT NULL)
 OR EXISTS (SELECT 1 FROM product_variants v WHERE v.id=e.target_variant_id AND v.product_id<>e.target_product_id)
 OR (e.target_variant_id IS NULL AND EXISTS (SELECT 1 FROM product_variants v WHERE v.id=i.variant_id AND v.combination_key<>'__DEFAULT__'))
 OR (e.target_variant_id IS NULL AND
 (NULLIF(TRIM(e.target_option_snapshot),'') IS NOT NULL OR
  EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=e.target_product_id)
  AND NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=e.target_product_id AND v.combination_key='__DEFAULT__')));

-- Save BEFORE and compare AFTER: all existing variant rows must be byte-equivalent.
SELECT * FROM product_variants ORDER BY id;
-- Snapshot/amount/count/restoration state must be unchanged; only the nullable FK changes.
SELECT * FROM order_items ORDER BY id;
SELECT * FROM cart_items ORDER BY id;
SELECT * FROM exchange_request_items ORDER BY id;
SELECT * FROM return_request_items ORDER BY id;
SELECT id,order_id,status,amount,version FROM payments ORDER BY id;
SELECT id,payment_id,order_cancellation_id,return_request_id,type,amount,status FROM payment_cancellations ORDER BY id;
SELECT * FROM order_cancellations ORDER BY id;
SELECT * FROM order_cancellation_items ORDER BY id;

-- AFTER: no missing default SKU for no-option products; exactly one via UNIQUE.
SELECT p.id FROM products p WHERE NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=p.id);
SELECT p.id,COUNT(v.id) AS default_variant_count FROM products p
LEFT JOIN product_variants v ON v.product_id=p.id AND v.combination_key='__DEFAULT__'
WHERE NOT EXISTS (SELECT 1 FROM product_variants opt WHERE opt.product_id=p.id AND opt.combination_key<>'__DEFAULT__')
GROUP BY p.id HAVING COUNT(v.id)<>1;
SELECT i.id,i.product_id FROM order_items i JOIN product_variants v ON v.product_id=i.product_id
 AND v.combination_key='__DEFAULT__' WHERE i.variant_id IS NULL;
SELECT e.id FROM exchange_request_items e JOIN product_variants v ON v.product_id=e.target_product_id
 AND v.combination_key='__DEFAULT__' WHERE e.target_variant_id IS NULL;
SELECT c.id FROM cart_items c JOIN product_variants v ON v.product_id=c.product_id
 AND v.combination_key='__DEFAULT__' WHERE c.variant_id IS NULL;
