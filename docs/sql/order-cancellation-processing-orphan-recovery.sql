-- Cancellation 5-3 긴급 개발 데이터 정리용.
-- 자동 실행하지 말고, 아래 SELECT 결과가 의도한 OrderCancellation #4 한 건인지 먼저 확인한다.
SET @target_order_cancellation_id = 4;

SELECT oc.id,
       oc.order_id,
       oc.seller_order_id,
       oc.status,
       oc.failed_at
FROM order_cancellations oc
WHERE oc.id = @target_order_cancellation_id
  AND oc.status = 'PROCESSING'
  AND NOT EXISTS (
      SELECT 1
      FROM payment_cancellations pc
      WHERE pc.order_cancellation_id = oc.id
  );

START TRANSACTION;

UPDATE order_cancellations oc
SET oc.status = 'FAILED',
    oc.failed_at = CURRENT_TIMESTAMP(6)
WHERE oc.id = @target_order_cancellation_id
  AND oc.status = 'PROCESSING'
  AND NOT EXISTS (
      SELECT 1
      FROM payment_cancellations pc
      WHERE pc.order_cancellation_id = oc.id
  );

SELECT ROW_COUNT() AS updated_rows;

SELECT oc.id,
       oc.status,
       oc.failed_at
FROM order_cancellations oc
WHERE oc.id = @target_order_cancellation_id;

-- 두 SELECT 결과와 updated_rows = 1을 확인한 뒤에만 COMMIT한다.
-- 조건이 다르거나 PaymentCancellation이 존재하면 ROLLBACK한다.
-- COMMIT;
ROLLBACK;
