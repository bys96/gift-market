-- MySQL 8 / 수동 실행 참고본. 자동 migration이 아니다.
-- 반드시 docs/PRODUCT_DESCRIPTION_MEDIA.md의 파일 이전/백업 절차를 먼저 확인한다.
-- 이 SQL은 S3 파일을 복사하지 않는다. 확인한 상품 1개, 상세 이미지 key 1개만 변환한다.
-- 새 data-storage-key를 지원하는 Backend/Frontend 배포 후 실행한다.

-- 1. 읽기 전용 후보 조회. 기존 Tiptap이 생성한 큰따옴표 src를 기준으로 한다.
SELECT id, seller_id, version,
       SUBSTRING(description,
                 GREATEST(1, LOCATE('src="http://localhost:9000/gift-market/products/', description) - 30),
                 260) AS legacy_image_context
FROM products
WHERE description LIKE '%src="http://localhost:9000/gift-market/products/%'
ORDER BY id;

-- 2. 위 SELECT 결과와 S3 object 존재/공개 조회를 직접 확인한 값으로 채운다.
-- 기본 NULL은 실수로 실행해도 아무 상품도 수정하지 않는다.
SET @reviewed_product_id = NULL;
SET @reviewed_object_key = NULL;
-- key 형식: products/{sellerId}/content/{uuid}.png (jpg/jpeg/webp/gif도 허용)
SET @old_attribute = CONCAT('src="http://localhost:9000/gift-market/', @reviewed_object_key, '"');
SET @new_attribute = CONCAT('data-storage-key="', @reviewed_object_key, '"');

-- 3. 변경 전/후 HTML 미리보기. 다른 domain, 다른 key는 치환하지 않는다.
SELECT id, version, description AS before_html,
       REPLACE(description, @old_attribute, @new_attribute) AS after_html
FROM products
WHERE id = @reviewed_product_id
  AND LOCATE(@old_attribute, description) > 0
  AND @reviewed_object_key REGEXP '^products/[1-9][0-9]*/content/[a-fA-F0-9-]{36}[.](jpg|jpeg|png|webp|gif)$';

-- 4. 백업 및 미리보기 확인 후에만 실행한다. 기본 종료는 ROLLBACK이다.
START TRANSACTION;
SET @before_html = NULL;
SET @before_version = NULL;
SELECT description, version INTO @before_html, @before_version
FROM products
WHERE id = @reviewed_product_id
FOR UPDATE;

UPDATE products
SET description = REPLACE(description, @old_attribute, @new_attribute),
    version = version + 1,
    updated_at = NOW(6)
WHERE id = @reviewed_product_id
  AND version = @before_version
  AND BINARY description = BINARY @before_html
  AND LOCATE(@old_attribute, description) > 0
  AND @reviewed_object_key REGEXP '^products/[1-9][0-9]*/content/[a-fA-F0-9-]{36}[.](jpg|jpeg|png|webp|gif)$';

SELECT ROW_COUNT() AS changed_rows; -- 검토한 상품 1개만 변경되는지 확인
SELECT id, version, description FROM products WHERE id = @reviewed_product_id;
ROLLBACK;
-- 실제 반영을 승인한 실행에서는 마지막 ROLLBACK을 COMMIT으로 바꾼다.
-- 반복 실행 시 old attribute가 없어 0 rows이며 이미 변환된 key는 변경하지 않는다.
