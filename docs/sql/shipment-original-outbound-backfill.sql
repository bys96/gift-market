-- Shipment ORIGINAL_OUTBOUND 수동 backfill (MySQL 8)
-- 자동 실행 대상이 아닙니다. shipments 테이블 생성 후 운영자가 검증하고 실행하세요.
-- NOT EXISTS 조건으로 멱등성을 보장하므로 재실행해도 기존 최초 배송을 중복 생성하지 않습니다.

USE gift_market;

START TRANSACTION;

INSERT INTO shipments (
    seller_order_id,
    shipment_type,
    shipping_company,
    tracking_number,
    status,
    shipped_at,
    delivered_at,
    created_at,
    updated_at
)
SELECT
    so.id,
    'ORIGINAL_OUTBOUND',
    so.shipping_company,
    so.tracking_number,
    CASE
        WHEN so.status = 'DELIVERED' THEN 'DELIVERED'
        ELSE 'SHIPPED'
    END,
    so.shipped_at,
    CASE
        WHEN so.status = 'DELIVERED' THEN so.delivered_at
        ELSE NULL
    END,
    COALESCE(so.shipped_at, so.created_at, CURRENT_TIMESTAMP(6)),
    COALESCE(so.delivered_at, so.shipped_at, so.updated_at, CURRENT_TIMESTAMP(6))
FROM seller_orders so
WHERE so.status IN ('SHIPPED', 'DELIVERED')
  AND so.shipping_company IS NOT NULL
  AND TRIM(so.shipping_company) <> ''
  AND so.tracking_number IS NOT NULL
  AND TRIM(so.tracking_number) <> ''
  AND so.shipped_at IS NOT NULL
  AND (so.status = 'SHIPPED' OR so.delivered_at IS NOT NULL)
  AND NOT EXISTS (
      SELECT 1
      FROM shipments sh
      WHERE sh.seller_order_id = so.id
        AND sh.shipment_type = 'ORIGINAL_OUTBOUND'
  );

COMMIT;

-- 생성 건과 잔여 누락은 shipment-original-outbound-verification.sql로 확인하세요.
