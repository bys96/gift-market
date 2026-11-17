# 상품 상세 다중 옵션 구매 구현

2026-11-17. 기존 장바구니 주문은 다중 Variant를 지원했지만 상품 상세 선택 UI와 바로구매는 단일 항목이었다. 기존 Order/SellerOrder/OrderItem 구조를 그대로 사용하며 DB 변경은 없다.

## 동작 및 API

옵션을 선택하면 카드가 추가되고 동일 옵션 재선택은 재고 한도 내에서 수량을 증가시킨다. 옵션별 단가·수량·합계·삭제를 표시하며 무옵션 상품은 기존 UI를 사용한다. 주문서에는 옵션별 행이 표시된다.

- 신규 `POST /api/cart/items/bulk`: `{ "items": [{ "productId": 10, "variantId": 11, "quantity": 2, "expectedUnitPrice": 10000 }] }`. 기존 `ApiResponse<CartResponse>`를 반환한다. 전체 rollback과 기존 수량 병합을 보장한다. `expectedUnitPrice`는 선택값이며 가격 변경 시 새로고침 안내 오류를 반환한다. 기존 단일 담기 API도 유지한다.
- 확장 `POST /api/orders/direct`: 기존 배송지·요청 키·`productId`에 `items: [{ variantId, quantity }]`를 전달한다. 기존 단일 요청도 지원하며 목록과 상위 단일 수량 필드의 혼용은 거부한다. 두 목록 API 모두 1~100개 및 중첩 Bean Validation을 적용한다.
- 같은 옵션의 중복 바로구매 요청은 수량을 합쳐서 요청하도록 거부한다. 가격·판매 상태·보관 상태·재고·소유권은 기존 서버 검증을 유지한다. 배송비는 기존 장바구니와 동일하게 항목별 합산한다.
- 잠금 안에서 모든 주문 항목과 금액을 검증한 후 기존 예약 차감을 수행한다. 결제 성공 시 재차 차감하지 않으며 실패/취소 복원, 재고 동기화, 멱등성은 기존 로직을 사용한다. Variant ID 및 시스템 SKU를 수정하거나 재발급하지 않는다.

## 변경 파일

경로는 저장소 루트 기준이다.

- Backend: `giftmarket-api/src/main/java/com/giftmarket/cart/controller/CartController.java`
- Backend: `giftmarket-api/src/main/java/com/giftmarket/cart/dto/request/CartItemCreateRequest.java`
- Backend 신규: `giftmarket-api/src/main/java/com/giftmarket/cart/dto/request/CartItemsCreateRequest.java`
- Backend: `giftmarket-api/src/main/java/com/giftmarket/cart/service/CartService.java`
- Backend: `giftmarket-api/src/main/java/com/giftmarket/order/dto/request/DirectOrderCreateRequest.java`
- Backend 신규: `giftmarket-api/src/main/java/com/giftmarket/order/dto/request/DirectOrderItemRequest.java`
- Backend: `giftmarket-api/src/main/java/com/giftmarket/order/service/OrderService.java`
- Frontend: `giftmarket-web/components/product/ProductDetailActions.tsx`
- Frontend: `giftmarket-web/styles/product/purchase.css`
- Frontend 신규: `giftmarket-web/lib/product-selection.ts`
- Frontend: `giftmarket-web/app/order/page.tsx`
- Frontend: `giftmarket-web/lib/cart-api.ts`
- Frontend: `giftmarket-web/stores/cart-store.ts`
- Frontend: `giftmarket-web/types/cart.ts`
- Frontend: `giftmarket-web/types/order.ts`
- 테스트: `giftmarket-api/src/test/java/com/giftmarket/cart/service/CartServiceAdminHiddenTest.java`
- 테스트 신규: `giftmarket-api/src/test/java/com/giftmarket/order/controller/MultiOptionPurchaseHttpContractTest.java`
- 테스트 신규: `giftmarket-api/src/test/java/com/giftmarket/order/integration/MultiOptionPurchaseIntegrationTest.java`
- 테스트: `giftmarket-api/src/test/java/com/giftmarket/order/integration/MySqlOrderConcurrencyIntegrationTest.java`
- 테스트 신규: `giftmarket-web/tests/product-selection.test.mjs`
- 문서: `docs/DEVELOPMENT_STATUS.md`, `docs/MULTI_OPTION_PURCHASE.md`

## 검증

- 전체 Backend 기본 테스트 1,231개 및 격리 MySQL 테스트 101개가 실패·오류·스킵 없이 통과했다. 이후 추가한 HTTP/무옵션/동시 다중 주문 테스트는 최종 관련 검증에 포함한다.
- Frontend 전체 Node 테스트 120개, lint 및 TypeScript 검사가 통과했다.
- 최종 코드로 관련 Backend 13개(다중 옵션 통합 8개·HTTP 계약 3개·장바구니 회귀 2개) 및 MySQL 주문 동시성 65개가 실패·오류·스킵 없이 통과했다. MySQL 검증에는 두 구매자가 반대 옵션 순서로 동시에 주문하는 경우 한 주문만 성공하고 양쪽 옵션 재고가 음수가 되지 않는 테스트를 추가했다.
- 최종 production build와 `git diff --check`가 통과했다. 빌드가 생성한 `next-env.d.ts` 변경은 원래 상태로 복원했다.
- 검증 범위: 2개 옵션 각각 2개 및 44,000원 상품 합계, 중복 옵션 수량 병합, 독립 증감·삭제, 재고 상한, 품절·보관·가격 변경 차단, 일괄 담기의 신규 행/기존 수량 rollback, 다중 바로구매·장바구니 주문, 결제 성공 후 예약 중복 차감 방지, 취소 재시도 시 정확히 한 번 복원, ID/SKU 보존, 단일/무옵션 회귀, 예약 후 품절된 주문서 재개.
- PG는 테스트 대역을 사용했다. 실제 Toss·운영 DB·배포와 실제 브라우저 PC/모바일 E2E 검증은 수행하지 않았다. 커밋·푸시는 하지 않았다.
