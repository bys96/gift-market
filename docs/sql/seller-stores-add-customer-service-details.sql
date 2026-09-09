-- SellerStore 고객센터 상세 필드 추가 (MySQL 8.4, 수동 실행 참고본).
-- 신규 DB는 seller-stores.sql을 사용한다. 이 파일은 기존 DB에 1회 적용한다.
-- 실행 전 백업하고 대상 schema/기존 컬럼을 확인한다. 자동 migration이 아니다.
-- 운영 JPA_DDL_AUTO=validate: ADD → 새 코드 배포 → 확인 → 별도 DROP 순서.
-- 기존 customer_service_hours는 구버전 인스턴스와 롤백을 위해 이 단계에서 유지한다.

SELECT DATABASE() AS target_schema;
SHOW CREATE TABLE seller_stores;

-- 기대값: 0행. 일부 컬럼이 이미 있으면 일괄 재실행하지 말고 현재 schema를 재검토한다.
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'seller_stores'
  AND COLUMN_NAME IN (
      'customer_service_open_time', 'customer_service_close_time',
      'customer_service_closed_days', 'customer_service_note'
  );

ALTER TABLE seller_stores
    ADD COLUMN customer_service_open_time VARCHAR(5) NULL,
    ADD COLUMN customer_service_close_time VARCHAR(5) NULL,
    ADD COLUMN customer_service_closed_days VARCHAR(255) NULL,
    ADD COLUMN customer_service_note VARCHAR(500) NULL;

-- 시간은 API에서 HH:mm 형식 검증. 휴무일/추가 안내는 텍스트다.
-- 기존 자유 형식 운영시간은 자동 분해/이전하지 않는다.
-- 기존 값이 있다면 백업을 기준으로 새 UI에서 확인/이전한 뒤 DROP한다.
-- 기대값: 네 컬럼이 위 타입으로 모두 nullable, 기존 컬럼은 그대로 존재.
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'seller_stores'
  AND COLUMN_NAME IN (
      'customer_service_open_time', 'customer_service_close_time',
      'customer_service_closed_days', 'customer_service_note', 'customer_service_hours'
  );
