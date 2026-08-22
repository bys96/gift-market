-- Return 5 refund calculation snapshot migration reference (MySQL 8)
-- Review the current schema and take a backup before manual execution.

ALTER TABLE return_requests
    ADD COLUMN product_refund_amount BIGINT NULL,
    ADD COLUMN original_shipping_refund_amount BIGINT NULL,
    ADD COLUMN return_shipping_charge BIGINT NULL,
    ADD COLUMN refund_amount BIGINT NULL;

-- Existing requests are intentionally not backfilled.
-- A complete snapshot is either entirely NULL or entirely non-NULL.
SELECT COUNT(*) AS partial_snapshot_count
FROM return_requests
WHERE (product_refund_amount IS NULL
       OR original_shipping_refund_amount IS NULL
       OR return_shipping_charge IS NULL
       OR refund_amount IS NULL)
  AND NOT (product_refund_amount IS NULL
           AND original_shipping_refund_amount IS NULL
           AND return_shipping_charge IS NULL
           AND refund_amount IS NULL);

SELECT COUNT(*) AS invalid_snapshot_count
FROM return_requests
WHERE product_refund_amount < 0
   OR original_shipping_refund_amount < 0
   OR return_shipping_charge < 0
   OR refund_amount < 0
   OR refund_amount <> product_refund_amount
                       + original_shipping_refund_amount
                       - return_shipping_charge;
