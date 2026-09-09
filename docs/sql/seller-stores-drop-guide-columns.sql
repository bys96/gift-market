-- SellerStore 안내 컬럼 제거: 기존 운영 DB용 수동 migration (MySQL 8.4).
-- 자동 실행 migration이 아니다. 신규 DB에는 seller-stores.sql만 사용한다.
-- DROP COLUMN은 데이터 삭제이며 ALTER TABLE은 implicit commit을 일으킨다.
-- 반드시 백업/복구 가능 여부와 아래 조회 결과를 확인한 후 단계별로 실행한다.
-- 전체 파일을 확인 없이 일괄 실행하지 않는다. SQL 자체가 아래 조건을 강제하지는 않는다.
-- 참고: https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html
--
-- 적용 순서 (운영 JPA_DDL_AUTO=validate 유지):
-- 1. 두 필드가 제거된 Backend/Frontend를 먼저 배포하고 GET/PATCH 정상 동작 확인.
--    Entity에 없는 여분 DB 컬럼은 일반 Hibernate schema validation 대상이 아니다.
--    저장소 application-example.yaml은 ${JPA_DDL_AUTO:update}를 사용하므로
--    운영 설정이 validate인지 배포 설정에서 확인한다. Secret 출력은 하지 않는다.
-- 2. blue/green 전환 완료 후 구버전 Render 인스턴스와 진행 중 요청을 모두 종료한다.
--    트래픽 전환만으로 충분하지 않다. 구버전 worker/예약 작업/재시작 가능성도 확인한다.
-- 3. 새 버전 안정화와 구버전 롤백 대기 기간을 마친 뒤 백업하고 아래 DROP 실행.
-- 4. 적용 후 metadata 조회와 새 버전 GET/PATCH, validate 기동 확인.
--    DROP 후 구버전을 바로 재배포하면 안 된다. 구버전이 필요하면 먼저 백업 기준으로
--    두 nullable VARCHAR(1000) 컬럼/필요 데이터를 복원한 뒤 구버전을 기동한다.
--    ROLLBACK 명령으로 이 DDL을 취소할 수 없다.

-- A. 실행 전: 대상 DB/버전과 실제 테이블 정의 확인.
SELECT DATABASE() AS target_schema, VERSION() AS mysql_version;
SHOW CREATE TABLE seller_stores;

-- 정확히 두 행, 각각 nullable VARCHAR(1000)인지 확인. 다르면 중단하고 재검토한다.
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'seller_stores'
  AND COLUMN_NAME IN ('shipping_guide', 'return_exchange_guide')
ORDER BY ORDINAL_POSITION;

-- 실사용 데이터가 없다는 전제 확인. 0이 아니면 중단하고 데이터 처리/백업 재검토.
-- raw 데이터는 출력하지 않는다. 빈 문자열도 저장값으로 보수적으로 집계한다.
SELECT COUNT(*) AS store_count,
       COUNT(shipping_guide) AS shipping_guide_value_count,
       COUNT(return_exchange_guide) AS return_exchange_guide_value_count
FROM seller_stores;

-- B. 위 확인 + 백업 + 모든 구버전 종료 확인 후에만 실행 (1회용, 재실행하지 않음).
ALTER TABLE seller_stores
    DROP COLUMN shipping_guide,
    DROP COLUMN return_exchange_guide;

-- C. 적용 후: remaining_columns = 0, 나머지 컬럼/제약 조건은 변경 전과 같아야 한다.
SELECT COUNT(*) AS remaining_columns
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'seller_stores'
  AND COLUMN_NAME IN ('shipping_guide', 'return_exchange_guide');

SHOW CREATE TABLE seller_stores;
-- 새 버전의 GET /api/seller/store 및 PATCH /api/seller/store 응답과
-- 스토어명/소개/로고/배너/고객센터 설정 유지 여부를 확인한다.
