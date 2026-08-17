-- SellerOrder 1단계 수동 backfill 스크립트 (MySQL 8)
-- 자동 실행 대상이 아닙니다. 애플리케이션 시작 후 Hibernate ddl-auto=update가
-- seller_orders 테이블과 order_items.seller_order_id(nullable)를 만든 것을 먼저 확인하세요.
-- 운영에서는 검토된 migration 도구로 동일 변경을 관리해야 합니다.

USE gift_market;

-- 0. 스키마 사전 확인
SHOW CREATE TABLE seller_orders;
SHOW COLUMNS FROM order_items LIKE 'seller_order_id';

-- PENDING_PAYMENT는 아직 판매자 처리 대상(PAID)이 아니므로 임의 변환하지 않습니다.
-- 아래 결과가 0이 아니면 해당 결제를 PAID/실패/만료 상태로 먼저 확정한 뒤
-- 이 스크립트를 다시 실행해야 최종 NOT NULL 전환이 가능합니다.
SELECT COUNT(*) AS pending_payment_order_item_count
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE o.status = 'PENDING_PAYMENT';

START TRANSACTION;

-- 1. 기존 주문의 Order + Seller 조합별 SellerOrder 생성
-- order_items.seller_id는 주문 생성 시 저장된 판매자 참조이므로 Product의 현재
-- seller 관계를 다시 조회하지 않습니다. 재실행해도 unique key로 중복 생성되지 않습니다.
INSERT INTO seller_orders (
    order_id,
    seller_id,
    status,
    shipping_company,
    tracking_number,
    prepared_at,
    shipped_at,
    delivered_at,
    created_at,
    updated_at
)
SELECT
    oi.order_id,
    oi.seller_id,
    CASE
        WHEN o.status IN ('CANCELLED', 'PAYMENT_FAILED', 'PAYMENT_EXPIRED')
            THEN 'CANCELLED'
        WHEN o.status IN ('PAID', 'ORDERED')
            THEN 'PAID'
    END,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    MIN(oi.created_at),
    CURRENT_TIMESTAMP(6)
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE o.status IN (
    'PAID',
    'ORDERED',
    'CANCELLED',
    'PAYMENT_FAILED',
    'PAYMENT_EXPIRED'
)
GROUP BY oi.order_id, oi.seller_id, o.status
ON DUPLICATE KEY UPDATE id = id;

-- 2. 각 OrderItem을 동일 Order + Seller의 SellerOrder에 연결
UPDATE order_items oi
JOIN seller_orders so
  ON so.order_id = oi.order_id
 AND so.seller_id = oi.seller_id
SET oi.seller_order_id = so.id
WHERE oi.seller_order_id IS NULL
   OR oi.seller_order_id <> so.id;

COMMIT;

-- 3. backfill 검증

-- 3-1. 최종적으로 반드시 0이어야 합니다.
SELECT COUNT(*) AS null_seller_order_item_count
FROM order_items
WHERE seller_order_id IS NULL;

-- 3-2. 한 SellerOrder에 다른 Order/Seller의 OrderItem이 섞였는지 확인 (0건이어야 함)
SELECT
    oi.id AS order_item_id,
    oi.order_id AS item_order_id,
    oi.seller_id AS item_seller_id,
    so.id AS seller_order_id,
    so.order_id AS seller_order_order_id,
    so.seller_id AS seller_order_seller_id
FROM order_items oi
JOIN seller_orders so ON so.id = oi.seller_order_id
WHERE oi.order_id <> so.order_id
   OR oi.seller_id <> so.seller_id;

-- 3-3. 같은 Order + Seller 조합 중복 확인 (0건이어야 함)
SELECT order_id, seller_id, COUNT(*) AS duplicate_count
FROM seller_orders
GROUP BY order_id, seller_id
HAVING COUNT(*) > 1;

-- 3-4. 전체/연결/미연결 OrderItem 개수 비교
SELECT
    COUNT(*) AS total_order_item_count,
    SUM(CASE WHEN seller_order_id IS NOT NULL THEN 1 ELSE 0 END)
        AS linked_order_item_count,
    SUM(CASE WHEN seller_order_id IS NULL THEN 1 ELSE 0 END)
        AS unlinked_order_item_count
FROM order_items;

-- 3-5. SellerOrder 수와 실제 Order + Seller 조합 수 비교
SELECT COUNT(*) AS seller_order_count
FROM seller_orders;

SELECT COUNT(*) AS eligible_order_seller_pair_count
FROM (
    SELECT DISTINCT oi.order_id, oi.seller_id
    FROM order_items oi
    JOIN orders o ON o.id = oi.order_id
    WHERE o.status IN (
        'PAID',
        'ORDERED',
        'CANCELLED',
        'PAYMENT_FAILED',
        'PAYMENT_EXPIRED'
    )
) pairs;

-- 3-6. FK가 존재하는지 확인. JPA mapping의 명시적 FK 이름은 아래와 같습니다.
SELECT
    constraint_name,
    table_name,
    referenced_table_name
FROM information_schema.referential_constraints
WHERE constraint_schema = DATABASE()
  AND table_name IN ('seller_orders', 'order_items')
  AND constraint_name IN (
      'fk_seller_orders_order',
      'fk_seller_orders_seller',
      'fk_order_items_seller_order'
  );

-- 4. 위 검증에서 NULL=0, 혼합=0, 중복=0, 전체=연결을 모두 확인한 뒤에만 실행하세요.
-- ddl-auto=update에 맡기지 말고 이 DDL을 수동 migration으로 적용합니다.
ALTER TABLE order_items
    MODIFY COLUMN seller_order_id BIGINT NOT NULL;

-- fk_order_items_seller_order가 없는 기존 스키마에서만 아래 문장을 별도로 실행하세요.
-- 이미 FK가 있으면 중복 실행하지 않습니다.
-- ALTER TABLE order_items
--     ADD CONSTRAINT fk_order_items_seller_order
--     FOREIGN KEY (seller_order_id) REFERENCES seller_orders (id);
