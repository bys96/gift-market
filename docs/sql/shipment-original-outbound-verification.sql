-- Shipment ORIGINAL_OUTBOUND migration 검증 (MySQL 8)
-- 모든 결과가 0행이어야 Shipment-only 읽기로 전환할 수 있습니다.

USE gift_market;

-- 1. 배송 중/완료 SellerOrder 중 최초 배송 Shipment 누락
SELECT so.id, so.status, so.shipping_company, so.tracking_number
FROM seller_orders so
WHERE so.status IN ('SHIPPED', 'DELIVERED')
  AND NOT EXISTS (
      SELECT 1
      FROM shipments sh
      WHERE sh.seller_order_id = so.id
        AND sh.shipment_type = 'ORIGINAL_OUTBOUND'
  );

-- 2. SellerOrder별 ORIGINAL_OUTBOUND 중복
SELECT sh.seller_order_id, COUNT(*) AS original_outbound_count
FROM shipments sh
WHERE sh.shipment_type = 'ORIGINAL_OUTBOUND'
GROUP BY sh.seller_order_id
HAVING COUNT(*) > 1;

-- 3. legacy snapshot과 Shipment 배송사/송장 불일치
SELECT
    so.id AS seller_order_id,
    so.shipping_company AS legacy_shipping_company,
    sh.shipping_company AS shipment_shipping_company,
    so.tracking_number AS legacy_tracking_number,
    sh.tracking_number AS shipment_tracking_number
FROM seller_orders so
JOIN shipments sh
  ON sh.seller_order_id = so.id
 AND sh.shipment_type = 'ORIGINAL_OUTBOUND'
WHERE NOT (so.shipping_company <=> sh.shipping_company)
   OR NOT (so.tracking_number <=> sh.tracking_number);

-- 4. SHIPPED/DELIVERED 상태 및 timestamp 불일치
SELECT
    so.id AS seller_order_id,
    so.status AS seller_order_status,
    sh.status AS shipment_status,
    so.shipped_at AS legacy_shipped_at,
    sh.shipped_at AS shipment_shipped_at,
    so.delivered_at AS legacy_delivered_at,
    sh.delivered_at AS shipment_delivered_at
FROM seller_orders so
JOIN shipments sh
  ON sh.seller_order_id = so.id
 AND sh.shipment_type = 'ORIGINAL_OUTBOUND'
WHERE (so.status = 'SHIPPED' AND sh.status <> 'SHIPPED')
   OR (so.status = 'DELIVERED' AND sh.status <> 'DELIVERED')
   OR NOT (so.shipped_at <=> sh.shipped_at)
   OR NOT (so.delivered_at <=> sh.delivered_at);
