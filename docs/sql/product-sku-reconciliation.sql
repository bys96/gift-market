-- READ ONLY, MySQL 8.4. Run against an isolated restored copy first.
-- No inferred repair, reassignment, stock update, or FK update is authorized.
-- Query 1: zero/partial/extra/duplicate/wrong-owner mappings, including inactive
-- variants and variants referenced by historical orders. Display order ignored.
WITH expected AS (
    SELECT v.id AS variant_id, token.pair
    FROM product_variants v
    JOIN JSON_TABLE(
        IF(REGEXP_LIKE(v.combination_key, '^[1-9][0-9]*:[1-9][0-9]*([|][1-9][0-9]*:[1-9][0-9]*)*$'),
           CONCAT('["', REPLACE(v.combination_key, '|', '","'), '"]'), '[]'),
        '$[*]' COLUMNS (pair VARCHAR(100) PATH '$')
    ) AS token
), actual AS (
    SELECT m.variant_id, m.option_value_id, ov.option_group_id,
           g.product_id AS owner_product_id,
           CONCAT(ov.option_group_id, ':', m.option_value_id) AS pair
    FROM product_variant_option_values m
    LEFT JOIN product_option_values ov ON ov.id = m.option_value_id
    LEFT JOIN product_option_groups g ON g.id = ov.option_group_id
)
SELECT v.id, v.product_id, v.sku_code, v.seller_sku_code, v.active,
       v.stock_quantity, v.combination_key,
       (SELECT COUNT(*) FROM expected e WHERE e.variant_id = v.id) AS expected_count,
       (SELECT COUNT(*) FROM actual a WHERE a.variant_id = v.id) AS actual_count,
       EXISTS (SELECT 1 FROM order_items oi WHERE oi.variant_id = v.id) AS has_order_history
FROM product_variants v
WHERE (v.combination_key <> '__DEFAULT__' AND NOT REGEXP_LIKE(v.combination_key, '^[1-9][0-9]*:[1-9][0-9]*([|][1-9][0-9]*:[1-9][0-9]*)*$'))
   OR (v.combination_key = '__DEFAULT__' AND (v.additional_price <> 0 OR EXISTS
       (SELECT 1 FROM actual a WHERE a.variant_id=v.id)))
   OR (SELECT COUNT(*) FROM expected e WHERE e.variant_id = v.id)
      <> (SELECT COUNT(*) FROM actual a WHERE a.variant_id = v.id)
   OR (SELECT COUNT(*) FROM expected e WHERE e.variant_id = v.id)
      <> (SELECT COUNT(DISTINCT SUBSTRING_INDEX(e.pair, ':', 1)) FROM expected e WHERE e.variant_id = v.id)
   OR (SELECT COUNT(*) FROM actual a WHERE a.variant_id = v.id)
      <> (SELECT COUNT(DISTINCT a.option_group_id) FROM actual a WHERE a.variant_id = v.id)
   OR EXISTS (SELECT 1 FROM actual a WHERE a.variant_id = v.id
              AND (a.owner_product_id IS NULL OR a.owner_product_id <> v.product_id
                   OR NOT EXISTS (SELECT 1 FROM expected e WHERE e.variant_id = v.id AND e.pair = a.pair)))
   OR EXISTS (SELECT 1 FROM expected e WHERE e.variant_id = v.id
              AND NOT EXISTS (SELECT 1 FROM actual a WHERE a.variant_id = v.id AND a.pair = e.pair));

-- Query 2: immutable Variant inventory owners with any historical plain order.
-- Include failed/cancelled/confirmed orders: do not guess future claim effects.
SELECT p.id AS product_id, p.stock_quantity AS product_stock,
       (SELECT COALESCE(SUM(v.stock_quantity), 0) FROM product_variants v WHERE v.product_id = p.id) AS retained_variant_stock,
       COUNT(*) AS plain_order_item_count
FROM products p JOIN order_items oi ON oi.product_id = p.id AND oi.variant_id IS NULL
WHERE EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=p.id AND v.combination_key='__DEFAULT__')
GROUP BY p.id, p.stock_quantity;
