-- Gift Market minimum category seed for a newly created schema.
-- Prerequisite: the `categories` table has already been created by the schema migration.
-- This script is intentionally limited to one neutral root/child pair because the repository
-- does not yet define an authoritative production category taxonomy.
-- It is safe to run repeatedly and does not force AUTO_INCREMENT ids.

START TRANSACTION;

-- MySQL UNIQUE(parent_id, name) does not prevent duplicate root names because parent_id is NULL.
-- The explicit NOT EXISTS predicate therefore provides root-level idempotency.
INSERT INTO categories (
    parent_id,
    name,
    sort_order,
    active,
    created_at,
    updated_at
)
SELECT
    NULL,
    '기타',
    900,
    TRUE,
    NOW(6),
    NOW(6)
WHERE NOT EXISTS (
    SELECT 1
    FROM categories
    WHERE parent_id IS NULL
      AND name = '기타'
);

-- If an existing environment already has the same root name, reuse its oldest row instead of
-- inserting or depending on a fixed id. Reactivation is required for the public category API.
SET @gift_market_category_root_id = (
    SELECT MIN(id)
    FROM categories
    WHERE parent_id IS NULL
      AND name = '기타'
);

UPDATE categories
SET active = TRUE,
    updated_at = NOW(6)
WHERE id = @gift_market_category_root_id
  AND active = FALSE;

INSERT INTO categories (
    parent_id,
    name,
    sort_order,
    active,
    created_at,
    updated_at
)
SELECT
    @gift_market_category_root_id,
    '기타 상품',
    10,
    TRUE,
    NOW(6),
    NOW(6)
WHERE @gift_market_category_root_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM categories
      WHERE parent_id = @gift_market_category_root_id
        AND name = '기타 상품'
  );

SET @gift_market_category_child_id = (
    SELECT MIN(id)
    FROM categories
    WHERE parent_id = @gift_market_category_root_id
      AND name = '기타 상품'
);

UPDATE categories
SET active = TRUE,
    updated_at = NOW(6)
WHERE id = @gift_market_category_child_id
  AND active = FALSE;

COMMIT;

SET @gift_market_category_root_id = NULL;
SET @gift_market_category_child_id = NULL;
