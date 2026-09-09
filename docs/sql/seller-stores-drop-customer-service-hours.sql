-- SellerStore 기존 고객센터 운영시간 컬럼 제거 (MySQL 8.4, 수동 실행 참고본).
-- ADD SQL과 분리된 마지막 단계이며 자동 실행하지 않는다.
-- 반드시 백업/복구 가능 여부 확인 후 단계별로 실행한다.
-- DROP은 destructive DDL이다. ALTER TABLE은 implicit commit으로 ROLLBACK할 수 없다.
--
-- 선행 조건:
-- 1. seller-stores-add-customer-service-details.sql 적용 완료.
-- 2. 새 Backend/Frontend 배포 후 GET/PATCH, 새 고객센터 필드, 이미지 업로드 확인.
-- 3. 구버전 Render/blue-green 인스턴스 및 진행 요청 완전 종료, 롤백 대기 기간 종료.
-- 4. 기존 자유 형식 값의 백업 및 필요한 수동 이전/폐기 검토 완료.
-- 트래픽 전환만으로 구버전 종료를 가정하지 않는다.
-- DROP 후 구버전 롤백 시 먼저 기존 nullable VARCHAR(255) 컬럼/데이터를 복원해야 한다.

SELECT DATABASE() AS target_schema;
SHOW CREATE TABLE seller_stores;

-- 신규 네 컬럼과 기존 컬럼이 모두 존재하는지 확인. 다르면 실행 중단.
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'seller_stores'
  AND COLUMN_NAME IN (
      'customer_service_open_time', 'customer_service_close_time',
      'customer_service_closed_days', 'customer_service_note', 'customer_service_hours'
  );

-- 저장된 값이 있으면 백업/수동 이전 또는 폐기 결정 없이 DROP하지 않는다.
SELECT COUNT(customer_service_hours) AS legacy_value_count FROM seller_stores;

-- 위 조건을 확인한 뒤 1회 실행. SQL 자체가 선행 조건을 강제하지는 않는다.
ALTER TABLE seller_stores DROP COLUMN customer_service_hours;

-- 기대값: 0. 새 버전 GET/PATCH와 validate 기동도 확인한다.
SELECT COUNT(*) AS remaining_legacy_columns
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'seller_stores'
  AND COLUMN_NAME = 'customer_service_hours';

SHOW CREATE TABLE seller_stores;
