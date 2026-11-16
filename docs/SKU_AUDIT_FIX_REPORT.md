# SKU 2차 감사 결함 수정 결과

## 확정 정책과 근본 원인

일반 상품은 Product, Variant 생성 이력이 있는 상품은 각 Variant가 실재고 소유자다. 기존 상품의 관리 방식 변경은 409로 거부한다. 최초 등록 transaction에서만 방식을 선택하며 초기 재고를 입력한다. 별도 재고 이관 시스템이나 추가 DDL은 도입하지 않았다.

High 원인은 활성 옵션 그룹이 0개가 되면 옵션 상품을 일반 상품으로 취급한 뒤, 복원할 때 과거 Variant 합계를 Product 소유 재고처럼 대입한 것이다. 이제 보관은 방식 전환이 아니다. 전체 보관 시 Variant 실재고는 유지하고 판매 가능 합계만 0으로 표시한다. 직접/장바구니 주문 및 결제 READY → CONFIRMING 직전에 서버가 방식과 보관 상태를 검증한다. 이미 예약된 수량은 결제 시 재차 차감하지 않는다.

PG 승인 요청이 이미 시작된 CONFIRMING/QUERY 및 reconciliation 결과는 기존 정책으로 적용한다. 보관을 이유로 이미 승인됐을 수 있는 거래 결과를 버리거나 재고를 이중 복원하지 않는다.

## 수정 전·후 수량

| 시나리오 | Product 표시 합계 / Variant 실재고 |
| --- | --- |
| 수정 전 실제 H2 Service 재현 | 보관 10/10 → 일반 주문 9/10 → 복원 10/10: 예약 수량 재생성 |
| 수정 후 전체 보관 | 0/10, 일반 상품 주문 시도 차단, 주문 생성 없음 |
| 보관 후 기존 옵션 복원 | 10/10, 기존 ID·시스템 SKU·관리 코드 유지 |
| 옵션 주문 1개 후 보관·결제 실패 | 0/9 → 원 Variant에 1개 복원 0/10 → 활성화 10/10, 중복 실패 결과로 추가 복원 없음 |
| 3개 주문 후 보관·반품 1개 | 0/7 → 원 Variant에 1개 복원 0/8 → 활성화 8/8 |

Product 합계 0은 물리 재고 삭제가 아니다. 보관 Variant에 재고가 남아 있으며 활성화 시 기존 수량을 표시한다. 수정 전 결과는 2차 감사에서 실제 Service/Repository/OrderService를 실행해 확인한 기록이다. 새 자동 회귀는 보관 직후 Product 합계 0, 구매 거부, 기존 Variant 수량 보존을 단언하므로 이전 동작을 허용하지 않는다.

## 나머지 결함 수정

- 다중 그룹: 그룹·값 ID 집합으로 조합을 매칭한다. 표시 배열 순서와 sortOrder는 식별자가 아니다. 기존 combination_key는 재작성하지 않고 집합으로 비교한다. 2개 그룹의 첫 그룹, 3개 그룹의 중간 그룹을 제거·복원하고 순서를 뒤집어도 기존 Variant ID·SKU·코드·재고가 유지된다. 동일 집합을 신규 Variant로 발급하는 요청도 차단한다.
- 부분 매핑: 기대 combination_key 집합과 실제 매핑을 비교한다. 0개/일부 소실/중복 그룹/잘못된 상품 소유권을 비활성 Variant까지 검사한다. Backend 409, 응답 mappingValid, 판매자 화면 경고·저장 차단을 적용한다. 옵션명으로 자동 복구하거나 주문 FK를 바꾸지 않는다.
- 혼재 데이터: Variant 이력과 일반 주문 이력이 같이 있으면 구매·신규 결제·편집을 409로 차단한다. 기존 실패 결제/클레임은 원 소유자에 복원하며 Product를 Variant 합계로 자동 덮어쓰지 않는다. 실패·취소 주문도 임의 제외하지 않고 별도 대사한다.
- 이전 SKU UUID 불변·상품별 판매자 코드 UNIQUE·구 요청 409·명시적 재고 버전 충돌·연결된 옵션명 변경 제한은 유지한다.

## 이번 보완 파일

Backend (giftmarket-api/src/main/java/com/giftmarket 기준):

- product/service/ProductInventoryPolicy.java, ProductVariantMapping.java (신규 검증 정책)
- product/service/ProductOptionService.java, ProductVariantService.java, ProductService.java, ProductRegistrationService.java, ProductModificationService.java
- product/repository/ProductVariantRepository.java, ProductOptionGroupRepository.java, order/repository/OrderItemRepository.java
- product/dto/response/ProductResponse.java, ProductDetailResponse.java, ProductVariantResponse.java
- order/service/OrderService.java, OrderInventoryService.java, cart/service/CartService.java, payment/service/PaymentTransactionService.java

Frontend (giftmarket-web 기준):

- components/seller/ProductOptionManager.tsx, ProductForm.tsx, types/product.ts
- app/seller/(seller-center)/products/[productId]/page.tsx, app/admin/(admin-center)/products/[productId]/page.tsx
- tests/seller-sku.test.mjs

테스트 (giftmarket-api/src/test/java/com/giftmarket 기준):

- product/service/ProductVariantMappingTest.java (신규), ProductStockIntegrationTest.java, ProductSkuMySqlIntegrationTest.java
- order/integration/OrderPurchaseReviewIntegrationTest.java, ReturnWorkflowIntegrationTest.java, MultiSellerOrderClaimIntegrationTest.java, MySqlOrderConcurrencyIntegrationTest.java
- order/repository/ClaimPendingQuantityRepositoryTest.java: 조합 키 fixture를 실제 ID 형식으로 정정
- 정책 의존성 연결/검증 책임 변경: ProductModificationServiceTest, ProductOptionServiceTest, ProductVariantServiceTest, ProductServiceMediaTest, SellerProductSearchServiceTest, OrderServicePaymentPreparationTest, OrderInventoryServiceTest, CartServiceAdminHiddenTest, PaymentTransactionServiceTest

문서/SQL: SKU_DESIGN.md, DEVELOPMENT_STATUS.md, 이 보고서, sql/product-sku-reconciliation.sql (신규 읽기 전용 SELECT).

## 검증

| 항목 | 결과 |
| --- | --- |
| Backend 전체 | 1,225개 통과, 실패·오류·스킵 0 |
| 격리 MySQL | 98개 통과, 실패·오류·스킵 0. 보관/복원 각각과 주문의 동시 실행 포함 |
| Frontend Node 전체 | 112개 통과, 실패·스킵 0 |
| ESLint | 경고 없이 통과 |
| TypeScript / production build | 상세 안내 문구 반영 후 최종 통과 |
| git diff --check | 통과 |

실제 주문·결제 준비/차단·중복 실패 결과·유상 주문 취소·반품 환불 workflow·교환 승인/예약은 Service와 Repository를 사용한다. PG는 테스트 Gateway다. MySQL은 격리 Testcontainers에서 보관·복원과 주문의 경쟁, 실제 대사 SELECT의 부분 매핑 탐지를 검증한다. 중복 매핑 집합 검증은 직접 객체 검사이며 DB UNIQUE 방어와 구분한다. Frontend는 실제 편집기 함수를 추출 실행하는 테스트와 기존 소스 검사 테스트를 포함하며 실제 브라우저 E2E는 아니다.

## 운영 영향 및 남은 위험

- 신규 DDL이나 운영 SQL을 실행하지 않았다. 기존 두 수동 DDL 적용/백업·복구본 검증 절차는 유지한다. 새 대사 SQL은 SELECT만 포함한다.
- 대사 SQL은 매핑 소실·불일치 및 재고 방식 혼재를 탐지한다. 원본 백업과 주문·클레임·예약·복원 이력을 대조한 개별 복구 승인이 필요하다. 이 코드로 과거 손실 데이터를 자동 보정하지 않는다.
- 취소·반품·교환·결제·정산 금액 및 수량 정책을 바꾸지 않았으며 원 FK/snapshot·복원 대상·기존 idempotency를 유지한다. 상품명 검색·공통 스크롤 로직을 변경하지 않았다.
- 실제 PC/모바일 브라우저와 운영 복구본 인수, 이미 진행 중인 외부 PG 거래 대사는 남아 있다. 독립 재감사가 필요하며 테스트 통과만으로 운영 배포를 승인하지 않는다.
- 커밋·푸시·배포·운영 DB 마이그레이션은 수행하지 않았다.
