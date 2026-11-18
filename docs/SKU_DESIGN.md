# SKU 분리 및 운영 적용

> 2026-11-17: 무옵션 기본 Variant 통합이 아래 과거 재고 소유권 고정/전환 금지 정책을 대체한다. 현재 기준과 수동 이관 절차는 [통합 보고서](./DEFAULT_VARIANT_INTEGRATION.md)를 따른다. 기존 옵션 SKU·ID·매핑 불변 및 클레임 정합성 정책은 유지한다.

## 확정 재고 소유권 정책 — 2차 감사 보완

- 일반 상품의 실재고 소유자는 Product, 한 번이라도 Variant가 생성된 상품의 소유자는 각 Variant다. Variant 행 존재 여부가 재고 관리 방식의 기준이며 active 옵션 그룹의 개수로 방식을 전환하지 않는다. 기존 Variant 행을 삭제하거나 다른 주문에 재연결하지 않는다.
- 신규 등록 transaction에서만 최초 일반/옵션 방식과 초기 재고를 선택한다. 등록 내부 초기화 메서드는 MANDATORY transaction 및 DRAFT/주문 이력 없음 조건을 검증한다. 기존 상품에서 옵션 추가로 Product 재고를 Variant로 바꾸는 요청, 옵션 상품의 Product 합계 직접 수정은 409다. 보관은 전환이 아니다.
- 옵션 전체 보관 시 Variant 실재고는 보존하고, 활성 Variant의 판매 가능 재고 표시 합계만 0이 된다. 일반 상품 주문을 허용하지 않는다. 복원은 기존 옵션/Variant ID·코드·보존 재고를 재사용하며, 수량을 새로 더하거나 Product 소유 재고를 Variant에 배분하지 않는다.
- 주문 생성(직접/장바구니), 장바구니 추가, 교환 신규 예약에서 재고 방식·혼재 이력·현재 조합 및 보관 여부를 서버가 검증한다. 결제 READY → CONFIRMING 전에는 Product/Variant를 잠금 조회·refresh하여 다시 검증한다. 이미 예약된 재고는 결제 시 재차 차감하지 않으며 재고 0/SOLD_OUT 자체로 예약 주문을 거절하지 않는다.
- PG 승인 요청이 이미 시작된 CONFIRMING/QUERY/웹훅/reconciliation은 새 승인 요청과 구별한다. 상품 보관을 이유로 이미 발생했을 수 있는 결제 결과를 버리거나 이중 환불하지 않는다. 기존 결제 결과 적용·대사 정책을 유지한다.
- 취소/반품/교환 회수 및 예약 해제는 원 OrderItem/ExchangeRequestItem이 참조하는 Product 또는 Variant에만 수량을 복원한다. 보관 Variant에 복원된 수량은 그대로 남고 활성화 시 다시 더하지 않는다. 기존 workflow의 완료 수량·결제 취소 이력·idempotency 방어를 유지한다.
- Variant 이력과 variant_id IS NULL 주문 이력이 같이 있는 상품은 혼재 대사 대상이다. 신규 구매/결제 시작 및 상품/옵션/재고 편집은 409로 차단한다. 기존 결제 실패/클레임은 원 소유자에 복원하되 혼재 상품의 Product를 Variant 합계로 자동 덮어쓰지 않는다. 과거 실패·취소 주문도 임의 제외하지 않는다.
- Product 재고가 Variant 합계로 갱신되는 경우는 혼재하지 않은 옵션 상품의 판매 가능 수량 표시를 동기화할 때다. 일반 상품의 소유 재고를 과거 Variant 합계로 대체하는 전환은 없다. Product row lock과 Variant @Version/화면 버전 검증을 유지한다.

## 옵션 조합 식별과 불일치 처리

- 조합 식별은 groupId:valueId 쌍의 집합이다. 화면 배열/sortOrder는 표시 순서만 정한다. 새 combination_key는 그룹 ID 순으로 생성하고 기존 key는 재작성하지 않는다. 기존 key와 요청은 집합으로 비교하여 순서만 바뀐 기존 조합의 ID/SKU를 유지한다. 동일 ID 집합을 신규 Variant로 발급하려는 요청도 409다.
- Frontend의 영속 옵션 clientId는 그룹·값 ID로 구성하고 조합 key는 정렬된 ID 집합으로 만든다. 2개 그룹의 앞 그룹, 3개 그룹의 중간 그룹을 보관·복원해도 배열 순서 때문에 신규 Variant가 되지 않는다.
- 저장 전에 모든 Variant(비활성 포함)의 combination_key와 실제 매핑을 비교한다. 0개/일부 소실/추가 매핑/그룹 중복/상품 소유권 불일치를 409로 거절하며 자동 보충하지 않는다. 판매자 응답 mappingValid와 화면 경고를 제공하고 상품 저장도 차단한다.
- 사전 대사는 sql/product-sku-reconciliation.sql의 두 SELECT를 격리 복구본에서 먼저 실행한다. 첫 조회는 순서에 무관하게 부분 소실과 잘못된 매핑을, 둘째 조회는 재고 방식 혼재를 식별한다. 이 파일은 DDL/자동 migration/자동 보정이 아니다.
- 불일치 발견 시 해당 상품을 판매 보류하고 원본 백업의 Variant ID·group/value ID·매핑·SKU·재고, 주문 snapshot/FK, 취소/반품/교환 예약·복원 이력을 대사한다. 옵션명만으로 매핑을 추정하거나 주문 FK를 옮기지 않는다. 복구안과 재고 기준을 개별 승인한 뒤 별도 운영 절차로 처리한다. 이번 구현은 운영 DB를 수정하지 않는다.

## 정책과 ERD

`Product 1:N ProductVariant`: 기존 id(IDENTITY), product_id, sku_code(전역 UNIQUE/NOT NULL), combination_key, stock_quantity, active를 유지하고 seller_sku_code VARCHAR(100) NOT NULL과 `(product_id, seller_sku_code)` UNIQUE를 추가한다. 비활성 옵션도 코드를 점유한다.

- 기존 sku_code는 변경하지 않고 seller_sku_code에 그대로 복사한다. 신규 sku_code는 서버에서 `GM-{UUID v4}`로 생성한다. 영속화 전에 최종 값이 있어 임시 SKU/NULL이 필요 없다. DB 전역 UNIQUE가 최종 방어선이며 충돌 시 transaction 전체가 실패한다. UUID 충돌을 성공으로 취급하거나 기존 행에 연결하지 않는다.
- 신규 UUID namespace와 기존 값의 충돌은 이관 사전 검사에서 `GM-` 접두사 발견 시 중단하여 방지한다. 그런 데이터가 있으면 삭제/재발급하지 말고 접두사 변경을 별도 검토한다.
- 판매자 코드는 Java trim + Locale.ROOT 대문자화, 정규화 후 100자 이하. `%`, `_`, `-`, 한글 등 기존 문자를 계속 허용하며 LIKE 검색에 사용하지 않는다. DB는 utf8mb4_0900_as_ci으로 악센트 등 다른 문자를 구별한다. 기존 서비스도 대문자로 저장했으나 비정상 수입 데이터가 있으면 SQL은 중단한다. Unicode 대문자화가 Java/MySQL에서 다른 수입 데이터는 별도 대사한다.
- 같은 ID 수정: 판매자 코드/가격/활성 상태만 수정하며 재고 변경은 별도 의도와 화면 버전 검증을 적용, 시스템 SKU 불변. 옵션 값 조합 변경 금지는 유지한다. 제거는 비활성화, 재활성화는 같은 ID/SKU. 새로운 조합은 새 ID/SKU. 동일한 기존 조합을 신규 행으로 재등록하는 것은 기존 combination UNIQUE 정책상 불가하며 기존 행을 재활성화한다.

## API와 화면

요청은 sellerSkuCode가 판매자 입력이다. 세 종류의 Variant DTO 모두 skuCode 별칭을 제거했다. sellerSkuCode 누락(null)은 이전 형식 요청으로 HTTP 409를 반환한다. 빈 문자열은 validation 오류다. 등록도 동일 정책으로 구버전 쓰기를 차단하며, 버전 헤더나 배포 순서에 의존하지 않는다. 응답의 skuCode와 관리자·구매자 응답 의미는 유지한다. 응답 skuCode는 기존 값 또는 신규 시스템 UUID이며 sellerSkuCode를 별도로 반환한다. 판매자 편집기는 판매자 코드를 입력하고 시스템 SKU를 읽기 전용 텍스트로 표시한다. 저장된 임시저장 JSON의 내부 skuCode는 판매자 코드 의미를 유지하며 전송할 때 sellerSkuCode로 변환한다.

이미 열린 구버전 탭은 skuCode만 전송하므로 서버가 409로 거부하며 transaction 쓰기가 시작되지 않는다. 최신 Frontend는 sellerSkuCode만 전송한다. 구버전/신버전 Backend 동시 실행과 구버전 롤백은 금지한다. 기존 임시저장 JSON은 화면 버전이 없으면 재고 변경을 허용하지 않고 최신 조회를 안내한다.

## 영향 범위

주문 OrderItem은 variant FK와 옵션명/가격 snapshot을 유지한다. 교환은 targetVariantId를 조회하며 원본/대상 참조 및 예약을 유지한다. 재고 차감/복원, 취소·반품·구매확정·정산의 금액과 수량 로직은 변경하지 않는다. 관리자 상품은 두 코드를 표시하고 관리자 교환의 시스템 SKU는 기존 의미를 유지한다. 구매자 화면에 SKU 표시를 추가하지 않는다. 저장소 밖 CSV/외부 연동 사용 여부는 운영자가 별도 확인한다.

## 수동 배포 순서

1. 운영자가 상품/옵션·주문·클레임·정산 관련 테이블을 포함한 DB 백업을 만들고 격리 DB 복구를 확인한다. API/Web 배포본도 보존한다. 실제 credential을 명령/로그/문서에 기록하지 않는다.
2. 유지보수 모드에서 모든 API 및 작업자/외부 writer를 중단한다. 구버전은 seller_sku_code를 채우지 않으므로 컬럼 추가 중에도 동시 실행하지 않는다.
3. 옵션 전체 행의 id, product_id, sku_code, combination_key, stock_quantity, active, version과 주문 snapshot/FK, 교환 참조 및 정산 원장을 외부 백업으로 대사할 기준을 확보한다. 사전 검사 SQL이 충돌/비정규 데이터를 발견하면 중단하고 개별 검토한다. 자동 이름 변경이나 행 삭제는 하지 않는다.
4. `sql/product-variant-seller-sku.sql` 이후 `sql/product-option-archive.sql`을 MySQL 클라이언트로 실행한다. 후자는 옵션 그룹/값에 archived BOOLEAN NOT NULL DEFAULT FALSE 두 컬럼만 추가하며 ID/이름/재고/매핑을 바꾸지 않는다. 컬럼 추가 → NULL만 백필 → 사전 검사 → NOT NULL → 복합 UNIQUE 순이다. MySQL DDL은 암묵적 커밋이므로 실패 시 트랜잭션 rollback을 가정하지 않는다. 실패 상태를 조사한 뒤 같은 파일을 재실행할 수 있다. 성공 후 재실행도 판매자 수정값을 덮어쓰지 않는다.
5. 기존 전역 sku_code UNIQUE/NOT NULL과 신규 복합 UNIQUE의 실제 `SHOW CREATE TABLE product_variants` 결과를 확인한다. 행 수·ID·상품/판매자 연결·재고·활성·주문 snapshot/FK·교환 참조·정산 원장을 백업 기준과 대조한다. 기존 sku_code는 한 글자도 달라지면 안 된다.
6. API를 production ddl-auto=validate로 기동하고 새 Web 배포본을 전환한다. 기존 브라우저를 새로고침한다. 격리 검증 후 트래픽을 연다. 이 작업에서 운영 SQL/배포는 실행하지 않는다.

## 실패 및 복구

- DDL 중간 실패: writer를 중단한 채 남은 컬럼/인덱스 상태와 오류를 확인한다. SQL은 기존 SKU/ID/재고를 바꾸지 않으므로 원인을 해결한 뒤 재실행한다.
- 신규 코드가 아직 쓰이지 않은 단계: 앱을 중단하고 백업과 대사 후 신버전 수정/재배포를 우선한다. **백업 이후 어떤 writer도 동작하지 않았다는 사실을 확인한 경우에만**, 검증한 이관 전 백업을 복원하고 구 API/Web을 함께 재기동하는 원복이 가능하다. 읽기 전용 검증 중 생성한 데이터까지 포함해 쓰기 여부를 확인한다.
- **신버전 쓰기 이후 구버전으로 단순 롤백 불가**: 다른 상품의 동일 판매자 코드가 허용되며 구버전은 시스템 UUID를 판매자 코드로 오해한다. 읽기 전용 유지보수 상태에서 수정본으로 전진 복구한다. 신규 컬럼을 DROP하거나 seller_sku_code를 sku_code로 덮어쓰지 않는다.
- 전체 DB 백업 복원은 그 이후 주문/결제까지 되돌릴 수 있으므로 자동 실행하지 않는다. PG와 신규 거래를 별도 대사하고 운영 승인된 복구 계획이 필요하다.

## 실제 브라우저 인수 항목

PC/모바일 상품 등록·수정에서 코드 입력/변경, 다른 상품·판매자 동일 코드, 공백/대소문자 중복, 비활성 코드 충돌 메시지, 신규 옵션, 비활성/재활성, 임시저장 복원, 시스템 SKU 수정 불가, 기존 옵션 조합 변경 금지를 확인한다. 기존 주문부터 취소/반품/교환·구매확정·정산까지 옵션 ID와 snapshot·재고가 유지되는지 확인한다. 자동 테스트는 실제 Toss 및 운영 배포 E2E를 대체하지 않는다.

## 변경 파일

Backend (`giftmarket-api/src/main/java/com/giftmarket/` 기준):

- `product/entity/ProductVariant.java`: 서버 UUID 생성, 판매자 코드 정규화, 시스템 SKU 갱신 금지, 복합 UNIQUE.
- `product/repository/ProductVariantRepository.java`: 상품별 판매자 코드 조회.
- `product/service/ProductVariantService.java`: 판매자 코드 검사/저장, 기존 ID·조합·비활성 정책 유지.
- `product/service/ProductRegistrationService.java`, `ProductModificationService.java`: 새 요청 필드 연결.
- `product/dto/request/ProductVariantRequest.java`, `ProductRegistrationVariantRequest.java`, `ProductModificationVariantRequest.java`: sellerSkuCode 명시 필수, 구 요청 차단.
- `product/dto/response/ProductVariantResponse.java`, `admin/dto/response/AdminProductDetailResponse.java`: 두 코드 구분.
- `global/exception/GlobalExceptionHandler.java`: DB 중복 경쟁 결과를 ApiResponse 409로 반환.

Frontend (`giftmarket-web/` 기준):

- `components/seller/ProductForm.tsx`, `ProductOptionManager.tsx`: 판매자 입력/요청과 시스템 표시 분리, 중복/길이 안내.
- `lib/seller-sku.ts`: Backend와 같은 trim/대문자화 및 정규화 후 길이 검증.
- `types/product.ts`, `types/admin.ts`: 응답/요청 의미 분리. `types/product-draft.ts`의 기존 skuCode는 임시저장 호환을 위해 유지.
- `app/seller/(seller-center)/products/[productId]/page.tsx`, `app/admin/(admin-center)/products/[productId]/page.tsx`: 관리 코드 표시.
- `styles/seller/products.css`, `styles/admin.css`: 긴 시스템 SKU 줄바꿈과 보조 표시.

테스트:

- `ProductSkuPolicyTest`: UUID 및 코드 변경/비활성/재활성 불변식, 정규화 길이.
- `ProductSkuContractTest`: 구 입력 거부·신규 DTO 및 응답 의미 분리.
- `ProductStockIntegrationTest`, `ProductSkuMySqlIntegrationTest`: 실제 상품 수정, 재고 보존, 타 상품 코드 재사용, 비활성 충돌, 조합 변경 차단. H2와 MySQL에서 실행.
- `ProductSkuMigrationMySqlTest`: 수동 SQL 자체 실행/재실행, 기존 ID/SKU/재고/참조 snapshot 보존, 예약 접두사/비정상 데이터 중단, MySQL UNIQUE/NOT NULL 및 동시 경쟁.
- `OrderPurchaseReviewIntegrationTest`: 결제 후 코드 변경/비활성 상태에서도 기존 주문 snapshot·옵션 FK·재고 보존 및 이후 배송/구매확정/리뷰 흐름.
- `tests/seller-sku.test.mjs`: 정규화, 요청 필드, 실제 편집기 함수의 기존/신규 옵션 및 비활성·재활성 보존. UI 브라우저 E2E와 구별한다.

취소·반품·교환·정산은 기존 전체 회귀 테스트로 검증한다. 마이그레이션 테스트의 축약 history 테이블 대사는 실제 운영 DB 전체 대사를 대체하지 않는다.

이전 SKU 작업 검증 기록 (2026-11-13, 이번 결함 수정의 검증 결과가 아님): `gradlew test mysqlIntegrationTest --no-daemon` 성공, 기본 전체 1,201개 및 MySQL 8.4 통합 81개 모두 실패/스킵 0. `node --test tests/*.test.mjs` 105개 통과, `npm run lint`, `npx tsc --noEmit`, `npm run build` 성공. 신규 Frontend SKU 테스트는 6개다. 실제 운영 DB/배포/브라우저 E2E는 실행하지 않았다.

## 2026-11-16 배포 차단 결함 수정 정책

- 기존 Variant metadata 수정은 stockQuantity를 변경하지 않는다. Frontend는 수량을 바꾸지 않았으면 재고 필드를 생략한다. 수량을 바꿨으면 stockQuantity + expectedVersion을 보낸다. 서버는 화면 버전과 @Version을 비교하고, 이후 동시 변경은 JPA optimistic lock으로 방어한다. 같은 수량으로 돌아온 경우도 버전이 달라 충돌한다. 버전 없이 다른 재고를 전송하면 409이며 최신 조회를 안내한다. 신규 Variant는 초기 재고 필수다.
- 일반 상품 metadata 수정은 expectedStockVersion이 없으면 현재 재고를 유지한다. 일반 상품 재고 변경은 expectedStockVersion을 검증한다. 기존 stock API도 expectedVersion 필수이며 옵션 상품의 합계 직접 수정을 금지한다. 등록 시 초기 재고는 그대로 저장한다.
- 옵션 제거는 그룹/값의 archived 처리 + Variant 비활성화다. 매핑과 Variant, 판매자 코드, 시스템 SKU, 재고 및 과거 FK를 삭제하지 않는다. 판매자 옵션 응답은 archivedOptionGroups로 보관 대상을 별도 제공하고, 구매자 활성 옵션 조회에서는 제외한다.
- 복원은 보관 옵션 복원 버튼으로 기존 ID를 요청한 후 기존 비활성 Variant를 활성화한다. 같은 이름을 신규 ID로 재등록해 보관 코드를 점유하려 하면 보관 옵션 복원을 안내한다. 다른 실물 옵션은 새 값 + 새 Variant + 다른 판매자 코드로 등록한다. 시스템 SKU는 새 Variant에만 발급한다. 기존 코드를 새 행으로 옮기거나 이력을 새 Variant에 연결하지 않는다.
- Variant에 한 번이라도 연결된 옵션 그룹/값은 주문 유무나 현재 재고가 0인지에 관계없이 이름 변경을 금지한다. 오탈자도 예외로 추정하지 않는다. Variant에 연결되기 전에는 오탈자 수정이 가능하다. 전체 상품 수정과 별도 옵션 API가 같은 검증을 사용한다. 옵션/Variant 수정은 상품 row lock을 공유한다.
- 추가 DDL 중 첫 ALTER 성공 후 두 번째가 실패할 수 있다. writer를 중단한 상태로 information_schema와 SHOW CREATE TABLE을 확인하고 원인을 고친 후 같은 파일을 재실행한다. 이미 archived=true인 상태는 재실행으로 초기화하지 않는다. 신규 컬럼 삭제나 구버전 Backend 기동으로 복구하지 않는다.
- 배포 전 매핑이 이미 소실된 Variant가 있는지 대사한다. sql/product-sku-reconciliation.sql을 사용한다. 매핑 0개만 찾는 LEFT JOIN 조회는 일부 소실을 놓치므로 단독 대사 기준으로 사용하지 않는다. 비어 있는 매핑을 옵션 이름만으로 자동 재구성하지 않는다. 발견 시 해당 상품 쓰기를 보류하고 백업/원본 ID로 개별 복구를 검토한다. 이번 변경은 이미 소실된 매핑을 추정 복구하지 않는다.

전체 검증과 남은 인수 항목은 SKU_AUDIT_FIX_REPORT.md에 기록한다.
