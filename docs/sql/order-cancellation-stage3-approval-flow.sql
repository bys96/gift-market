-- Cancellation 3단계 수동 DDL 참고본 (MySQL 8)
-- Hibernate ddl-auto=update 개발환경에서는 실제 컬럼 존재 여부를 먼저 확인한다.

ALTER TABLE order_cancellations
    ADD COLUMN requires_seller_approval BOOLEAN NOT NULL DEFAULT FALSE
        AFTER reason;

-- 기존 데이터는 요청 당시 SellerOrder 상태를 현재 상태만으로 확정할 수 없으므로
-- 자동 backfill하지 않는다. 아래 조회 결과를 업무 이력과 대조한 뒤 승인형 요청 ID만 갱신한다.
SELECT oc.id,
       oc.order_id,
       oc.seller_order_id,
       oc.status,
       so.status AS current_seller_order_status,
       oc.requested_at
FROM order_cancellations oc
JOIN seller_orders so ON so.id = oc.seller_order_id
WHERE oc.requires_seller_approval = FALSE
  AND oc.status = 'REQUESTED'
ORDER BY oc.id;

-- 검증된 ID에 한해서 실행하는 예시이며 자동 실행하지 않는다.
-- UPDATE order_cancellations
-- SET requires_seller_approval = TRUE
-- WHERE id IN (...);

SELECT requires_seller_approval, status, COUNT(*) AS count
FROM order_cancellations
GROUP BY requires_seller_approval, status
ORDER BY requires_seller_approval, status;
