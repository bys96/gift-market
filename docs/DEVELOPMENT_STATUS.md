# Gift Market 개발 현황

> 최종 갱신: 2026-08-22
>
> 이 문서는 현재 저장소의 실제 코드를 기준으로 작성한다. 문서와 코드가 충돌하면 실제 코드를 현재 구현 상태의 최종 기준으로 사용한다.

## 0. 현재 요약

Gift Market은 회원/판매자/상품/장바구니/주문/결제/부분취소·부분환불과 SellerOrder 1:N Shipment에 이어 **Return Backend 1~7의 요청·회수·검수·PG 환불·재고복원·완료 처리까지 구현된 상태**다.

현재 가장 큰 미완료 범위는 다음이다.

- 공개 HTTPS staging + 상점용 Toss 테스트 키를 사용한 최종 webhook/부분취소 통합 검증
- FAILED 또는 장기 PROCESSING 부분환불을 운영자가 관측·수동 대응하는 관리자 기능
- Return Frontend
- Exchange Backend/Frontend
- 관리자 주문/결제 운영 화면
- 운영 DB용 versioned migration 도입

## 1. 기술 기준

### Backend

- Java 21
- Spring Boot 4.1.0
- Spring Security
- OAuth2/OIDC, JWT
- Spring Data JPA
- MySQL
- MinIO
- Gradle

### Frontend

- Next.js 16.2.11 App Router
- React 19.2.4
- TypeScript
- Zustand
- TanStack Query dependency 존재
- Tiptap
- 일반 CSS 기반 UI
- Tailwind dependency/import는 존재하지만 신규 UI에는 utility class를 사용하지 않음

## 2. 인증 / Security

현재 `SecurityConfig` 기준:

- OAuth/login/error: permitAll
- `POST /api/auth/token`, `POST /api/auth/logout`: permitAll
- `POST /api/payments/webhooks/toss`: permitAll
- `/api/admin/**`: ADMIN
- `/api/seller/products/**`: SELLER
- `/api/seller/orders/**`: SELLER
- `/api/auth/me`, `/api/users/me`, `/api/storage/**`, `/api/seller-applications/**`, `/api/cart/**`, `/api/orders/**`, `/api/payments/**`, `/api/addresses/**`: authenticated
- 나머지: permitAll

주문/결제/판매자 리소스는 Security rule 외에도 Service에서 사용자/판매자 ownership을 다시 검증한다.

## 3. 완료된 주요 기능

### 회원

- Google OAuth/OIDC
- Kakao OAuth
- JWT Access Token
- Refresh Token cookie
- 프로필
- 배송지 CRUD 및 기본 배송지 정책
- Wishlist

### 판매자

- 판매자 신청
- 관리자 승인
- SELLER role
- 판매자센터
- 상품 등록/수정
- 옵션/Variant
- 재고
- 상품 이미지
- 판매자 상품관리

### 상품 / 장바구니

- 구매자 상품 목록
- 상품 상세
- Variant 단일 dropdown 선택
- Variant 재고/품절 표시
- 음수 additionalPrice 허용 + 최종 판매가격 1원 이상 Backend 검증
- 장바구니
- 바로구매

## 4. 주문 구조

```text
Order
├─ Payment
├─ SellerOrder A
│  ├─ OrderItem
│  ├─ OrderItem
│  └─ Shipment N
└─ SellerOrder B
   └─ OrderItem
```

- `Order`: 구매자가 한 번 결제한 전체 거래
- `SellerOrder`: 한 Order 안에서 판매자별 처리 단위
- `OrderItem`: 주문 당시 상품/옵션/가격/배송비 snapshot
- `Payment`: Order 전체 결제
- `Shipment`: SellerOrder에 속하는 실제 물류 이동과 송장 이력

`OrderItem.sellerOrder`는 필수 관계다. Payment는 멀티셀러라도 Order 단위 하나를 유지한다.

## 5. SellerOrder / 배송

`SellerOrderStatus`:

```text
PENDING_PAYMENT
PAID
PREPARING
SHIPPED
DELIVERED
CANCELLED
```

신규 주문 prepare에서 판매자별 SellerOrder를 생성하고 모든 OrderItem을 연결한다.

결제 성공 시:

```text
SellerOrder PENDING_PAYMENT → PAID
```

판매자 배송 처리:

```text
PAID → PREPARING → SHIPPED → DELIVERED
```

- 중간 점프/역방향 전이 차단
- 판매자 ownership 검증
- `SellerOrder 1:N Shipment`
- 최초 배송은 `Shipment(type=ORIGINAL_OUTBOUND)`가 source of truth
- `ShipmentType`: ORIGINAL_OUTBOUND / RETURN_COLLECTION / EXCHANGE_COLLECTION / EXCHANGE_OUTBOUND
- `ShipmentStatus`: READY / SHIPPED / DELIVERED / CANCELED
- Shipment 상태 전이: `READY → SHIPPED → DELIVERED`, `READY → CANCELED`
- 이미 SHIPPED인 Shipment는 취소하지 않고 출고 이후 역물류는 별도 Shipment로 처리
- Order → SellerOrder 비관적 잠금 후 ORIGINAL_OUTBOUND 존재 여부를 검사해 최초 송장 중복 생성 방지
- 모든 shipment type에 대한 `(seller_order_id, shipment_type)` UNIQUE는 사용하지 않음

SellerOrder의 `shippingCompany / trackingNumber / shippedAt / deliveredAt` 컬럼은 물리적으로 유지하지만 migration/rollback 호환 snapshot이다. 신규 흐름은 Shipment에서 SellerOrder legacy snapshot 방향으로만 동기화한다.

기존 API의 `shippingCompany / trackingNumber / shippedAt / deliveredAt` 응답 shape은 유지한다. 조회는 ORIGINAL_OUTBOUND Shipment를 우선하고, migration 안정화 기간에는 Shipment가 없는 기존 행만 SellerOrder 값으로 fallback한다. 기존 SHIPPED 행의 배송완료 시 Shipment를 생성하는 호환 경로도 유지한다.

구매자 주문 조회는 `Order.status`와 별도로 SellerOrder 상태를 집계한 파생 `deliveryStatus`를 제공한다.

## 6. Payment 구현 현황

### 상태

`PaymentStatus`:

```text
READY
CONFIRMING
PAID
PARTIALLY_CANCELED
FAILED
EXPIRED
CANCELING
CANCELED
```

### 결제 준비

```text
검증/잠금
→ 재고 예약 차감
→ Order PENDING_PAYMENT
→ SellerOrder PENDING_PAYMENT
→ OrderItem snapshot
→ Payment READY
→ commit
```

- `clientOrderRequestKey` 멱등성
- 중복 Order/Payment/SellerOrder 생성 방지
- 중복 재고 예약 차감 방지
- 장바구니 주문은 prepare 시 CartItem 유지
- 바로구매는 Cart 불변

### 승인

```text
Tx A: READY → CONFIRMING
→ commit
→ Toss confirm/query
→ Tx B: Payment PAID + Order PAID + SellerOrder PAID
```

- Toss timeout/5xx/connection reset 등 결과 불명 시 CONFIRMING 유지
- Toss 단건 조회로 DONE 확인 시 PAID 복구
- 결제 성공 후 snapshot과 동일한 CartItem만 안전하게 삭제

### READY 만료

scheduler가 만료 READY를 처리한다.

```text
Payment READY → EXPIRED
Order → PAYMENT_EXPIRED
SellerOrder → CANCELLED
예약 재고 정확히 1회 복원
```

### CONFIRMING reconciliation

장기 CONFIRMING을 scheduler가 Toss 단건 조회한다.

- DONE → PAID 복구
- 명확한 실패만 실패 확정
- 불명/timeout/5xx → CONFIRMING 유지

### Toss webhook

```text
POST /api/payments/webhooks/toss
```

- webhook payload 자체를 최종 상태로 신뢰하지 않음
- Toss 단건 조회 후 기존 reconciliation 상태전이 재사용
- `payment_webhook_events`로 transmission/event 중복 방지
- 처리 상태: PROCESSING / PROCESSED / IGNORED / REJECTED / RETRYABLE_FAILED
- 결제 승인, 전체취소, 부분취소 reconciliation에 연결

## 7. 전체 결제 취소

기존 FULL 취소 API는 유지된다.

```text
PATCH /api/orders/{orderId}/cancel
```

핵심 흐름:

```text
Payment PAID
→ PaymentCancellation(FULL) REQUESTED
→ Payment CANCELING
→ Toss 전체취소
→ 성공 확인
→ Payment CANCELED
→ PaymentCancellation SUCCEEDED
→ Order CANCELLED
→ SellerOrder CANCELLED
→ 전체 예약/판매 재고 복원
```

결과 불명 시 CANCELING을 유지하고 scheduler/query/webhook으로 복구한다. 저장된 동일 PG idempotency key만 재사용한다.

## 8. 주문 부분취소 / 부분환불 — 구현 완료

### 8.1 도메인

```text
OrderCancellation
└─ OrderCancellationItem

Payment
└─ PaymentCancellation(FULL/PARTIAL)
```

역할 분리:

- `OrderCancellation`: 구매자/판매자의 주문 취소 업무
- `OrderCancellationItem`: 이번 취소 대상 OrderItem과 요청 수량
- `PaymentCancellation`: 실제 PG 취소 transaction 기록

`OrderCancellationStatus`:

```text
REQUESTED
PROCESSING
COMPLETED
REJECTED
FAILED
```

`PaymentCancellationType`:

```text
FULL
PARTIAL
```

`OrderItem.quantity`는 원 주문 수량을 유지하고 `canceledQuantity`에 확정 취소 누계를 기록한다.

### 8.2 구매자 정책

```text
SellerOrder PAID
→ 즉시 부분취소

SellerOrder PREPARING
→ 취소요청
→ 판매자 승인/거절

SellerOrder SHIPPED
→ 기존 주문취소 불가

SellerOrder DELIVERED
→ 기존 주문취소 불가
```

SHIPPED/DELIVERED는 향후 반품/교환 도메인 범위다.

### 8.3 구매자 API

```text
POST /api/orders/{orderId}/cancellations
GET  /api/orders/{orderId}/cancellations
```

한 번의 취소 요청은 하나의 SellerOrder 안에서 여러 OrderItem과 각 수량을 포함할 수 있다.

Backend가 다음을 검증한다.

- 사용자 ownership
- Order/SellerOrder/OrderItem 관계
- Payment/Order/SellerOrder 상태
- 원 주문 수량
- 확정 취소 수량
- REQUESTED/PROCESSING 점유 수량
- client request key 멱등성

### 8.4 판매자 승인/거절 API

```text
GET   /api/seller/orders/cancellations
GET   /api/seller/orders/cancellations/{cancellationId}
PATCH /api/seller/orders/cancellations/{cancellationId}/approve
PATCH /api/seller/orders/cancellations/{cancellationId}/reject
```

- 활성 Seller만 접근
- 자기 SellerOrder 요청만 조회/처리
- PREPARING + `requiresSellerApproval=true` + REQUESTED만 승인/거절 가능
- 승인/거절과 배송 시작 경합을 lock + 상태 재검증으로 방어

### 8.5 환불 금액 계산

`OrderCancellationRefundCalculator`가 Backend snapshot으로 계산한다.

상품 환불:

```text
SUM(OrderItem.unitPrice × 이번 취소 수량)
```

배송비:

```text
취소 후 SellerOrder에 배송할 수량이 남음
→ 배송비 0원 환불

취소 후 SellerOrder의 모든 상품 잔여수량 = 0
→ 해당 SellerOrder의 OrderItem.shippingFee 원 snapshot 합계 환불
```

Frontend가 환불금액을 결정하지 않는다.

### 8.6 부분환불 Payment 처리

- `PaymentStatus.PARTIALLY_CANCELED` 구현
- provider-neutral `GatewayPaymentStatus.PARTIALLY_CANCELED` 구현
- Toss `PARTIAL_CANCELED` 매핑 구현
- `GatewayCancelCommand.cancelAmount` 구현
- Toss cancel body에 PARTIAL일 때만 `cancelAmount` 전달
- FULL 취소 body는 기존 전액취소 형태 유지
- Toss `isPartialCancelable` 검증

PARTIAL 성공 시:

```text
PaymentCancellation(PARTIAL) SUCCEEDED
OrderCancellation COMPLETED
OrderItem.canceledQuantity 증가
취소 수량만 재고 복원
```

잔액이 남으면:

```text
Payment PARTIALLY_CANCELED
Order PAID 유지
```

잔액이 0이면:

```text
Payment CANCELED
```

SellerOrder는 모든 상품이 전량 취소된 경우에만 `CANCELLED`가 된다.

### 8.7 환불 잔액

`PaymentRefundBalanceService`는 다음을 계산한다.

```text
originalAmount
- SUCCEEDED cancellation amount
- REQUESTED cancellation reserved amount
= availableRefundAmount
```

부분환불이 동시에 Payment 원금을 초과하지 않도록 검증한다.

### 8.8 부분 재고 복원

`OrderInventoryService.restoreCancellationItems(...)`가 취소 확정된 정확한 수량만 복원한다.

- 일반 상품 stock 복원
- Variant stock 복원
- 기존 Product/Variant 잠금 정책 재사용
- 완료 멱등성으로 중복 재고복원 방지

### 8.9 결과 불명 / reconciliation / orphan recovery

PARTIAL `PaymentCancellation`의 REQUESTED/PROCESSING 장기 체류를 scheduler가 조회한다.

- 저장된 provider transaction key 우선 매칭
- 없으면 금액/사유/요청시각/DONE 조건이 유일한 경우에만 매칭
- 성공 확인 → 기존 completion transaction 재사용
- 거래가 없고 안전한 경우에만 저장된 amount/reason/idempotency key로 재시도
- 모호하면 추측하지 않고 unresolved 유지
- webhook과 scheduler가 같은 reconciliation 경로를 사용

판매자 승인 후 PaymentCancellation 생성 전에 예외가 발생한 고아 PROCESSING은 별도 transaction에서 PaymentCancellation이 정말 없을 때만 FAILED로 전환한다.

## 9. Cancellation Frontend — 구현 완료

### 구매자

`/my/orders/[orderId]`

- SellerOrder별 취소 가능 상품 선택
- 여러 상품 동시 선택
- 수량 선택
- PAID 즉시취소
- PREPARING 취소요청
- SHIPPED/DELIVERED/CANCELLED 취소 버튼 미노출
- `availableCancellationQuantity` Backend 값 사용
- REQUESTED/PROCESSING/REJECTED/FAILED/COMPLETED 취소 이력 표시
- 원 주문금액 유지
- SUCCEEDED 누적 환불금액과 현재 결제잔액 별도 표시
- 전량 취소 상품도 주문 이력에서 제거하지 않고 취소완료 표시

### 판매자

```text
/seller/orders/cancellations
/seller/orders/cancellations/{cancellationId}
```

- 상태 필터 + pagination
- 상품/옵션 snapshot
- 원 주문수량 / 기취소수량 / 이번 요청수량
- 승인/거절
- 거절 사유 최대 500자
- REQUESTED만 액션 노출
- PROCESSING/COMPLETED/REJECTED/FAILED 상태 안내

기존 SellerOrder 상세에도 상품별 취소완료수량/남은 처리수량과 활성 승인형 취소요청을 표시한다.

## 10. 현재 API 요약

### 구매자 주문

```text
POST  /api/orders
POST  /api/orders/direct
GET   /api/orders
GET   /api/orders/{orderId}
PATCH /api/orders/{orderId}/cancel
POST  /api/orders/{orderId}/cancellations
GET   /api/orders/{orderId}/cancellations
```

### 판매자 주문

```text
GET   /api/seller/orders
GET   /api/seller/orders/{sellerOrderId}
PATCH /api/seller/orders/{sellerOrderId}/prepare
PATCH /api/seller/orders/{sellerOrderId}/ship
PATCH /api/seller/orders/{sellerOrderId}/deliver

GET   /api/seller/orders/cancellations
GET   /api/seller/orders/cancellations/{cancellationId}
PATCH /api/seller/orders/cancellations/{cancellationId}/approve
PATCH /api/seller/orders/cancellations/{cancellationId}/reject
```

### Payment

```text
POST /api/payments/{paymentId}/confirm
GET  /api/payments/{paymentId}
POST /api/payments/webhooks/toss
```

## 11. Return Backend 구현 현황

Return Backend 1~7의 정상 상태 흐름은 다음과 같다.

```text
REQUESTED → APPROVED → COLLECTING → RECEIVED → INSPECTED → REFUNDING → COMPLETED
REQUESTED → REJECTED
```

- 구매자 생성·주문별 목록·단건 조회와 ownership 검증
- `clientRequestKey`: 동일 key/동일 payload는 기존 결과 반환, 다른 payload 재사용은 충돌 처리
- `quantity - canceledQuantity - returnedQuantity - 활성 Return 점유수량` 기준 반품 가능 수량 검증
- Order → SellerOrder → ReturnRequest → 정렬된 OrderItem 비관적 잠금
- 판매자 목록·상세·승인·거절과 `OTHER` 귀책(BUYER/SELLER) 확정
- 별도 `RETURN_COLLECTION Shipment` 생성, 송장 등록, 회수 시작과 입고 처리
- 모든 ReturnRequestItem 일괄 검수와 RESTOCKABLE/NON_RESTOCKABLE 기록
- OrderItem 주문 당시 가격·원배송비·반품/교환 배송비 snapshot 기반 환불 예정금액 확정
- BUYER/SELLER 귀책 및 SellerOrder 부분/전체반품 배송비 정책 적용
- 기존 Cancellation/완료 Return과의 원 배송비 중복 환불 방지
- Payment 환불 가능 잔액과 계산 확정 후 아직 미예약인 Return snapshot을 함께 검증
- ReturnRequest와 `PaymentCancellation(PARTIAL)` UNIQUE 연결, 고정 PG idempotency key 사용
- Toss 실제 부분환불, 결과 불명 유지, scheduler reconciliation과 webhook 연계
- PG 성공 시 Payment PARTIALLY_CANCELED/CANCELED 반영; Order/SellerOrder 배송 후 상태는 유지
- PG 성공 또는 0원 환불 확정 후 모든 item의 `returnedQuantity` 증가
- RESTOCKABLE만 Product/Variant 재고 복원하고 `restockedQuantity` 기록
- `SUCCEEDED + REFUNDING`과 `0원 + REFUNDING` completion recovery 및 COMPLETED 멱등 장벽

Return Frontend는 미구현이다. Exchange는 설계만 존재하며 Backend/Frontend 모두 미구현이다. 공개 staging과 상점용 Toss 테스트 키를 사용한 실제 PG/반품 E2E 검증도 운영 전 과제로 남아 있다.

## 12. 실제 검증 상태

기존 개발 과정에서 확인된 항목:

- Toss 테스트 카드 결제 성공
- 간편결제 테스트 성공
- confirm HTTP 200 / DONE
- Payment PAID / Order PAID
- 재고 1회 차감
- 전체취소 성공
- Payment CANCELED / Order CANCELLED
- PaymentCancellation SUCCEEDED
- 전체취소 재고 복원
- 부분취소 DB 흐름 검증
- PREPARING 취소요청 → 판매자 승인 → PARTIAL PaymentCancellation SUCCEEDED 흐름 검증
- 구매자/판매자 cancellation UI 구현

현재 소스에는 주문/결제/cancellation/Shipment/Return 관련 테스트가 존재한다. Return 7 완료 후 Backend 전체 테스트를 실행해 **279 tests / 279 success / 0 failure / 0 error / 0 skipped**를 확인했다.

개발 DB에서는 `docs/sql/shipment-original-outbound-backfill.sql`을 실행해 SellerOrder 32를 DELIVERED ORIGINAL_OUTBOUND, SellerOrder 34를 SHIPPED ORIGINAL_OUTBOUND로 변환했다. `shipment-original-outbound-verification.sql`의 누락/중복/배송정보 불일치/상태·timestamp 불일치 네 검증은 모두 0 rows였다.

## 13. 운영 전 필수 Toss 검증 TODO

이 TODO는 실제 staging 검증 전 완료 처리하거나 삭제하지 않는다.

- [ ] 공개 HTTPS dev/staging URL 준비
- [ ] 전자결제 신청 후 상점용 Toss `test_gck` / `test_gsk` 적용
- [ ] Toss 개발자센터 webhook 등록
- [ ] 실제 staging 테스트 결제
- [ ] `PAYMENT_STATUS_CHANGED` webhook HTTP 200 확인
- [ ] `payment_webhook_events`가 `PROCESSED`인지 확인
- [ ] 동일 webhook 재전송 멱등성 확인
- [ ] Payment / Order PAID 확인
- [ ] 전체 결제 취소
- [ ] 전체취소 webhook
- [ ] Payment CANCELED / Order CANCELLED 확인
- [ ] 전체취소 재고 1회 복원 확인
- [ ] 부분취소 1회
- [ ] 동일 Payment에 부분취소 여러 회
- [ ] 멀티셀러에서 한 SellerOrder만 전량 취소
- [ ] 부분취소 후 Payment PARTIALLY_CANCELED / 잔액 확인
- [ ] 최종 잔액 0 시 CANCELED 확인
- [ ] 부분취소 webhook 재전송 멱등성 확인
- [ ] timeout/connection reset/5xx 후 reconciliation 복구 확인
- [ ] 부분취소 중복 요청/더블클릭 멱등성 확인
- [ ] canceledQuantity 정확성 확인
- [ ] Product/Variant 재고가 정확히 1회만 복원되는지 확인
- [ ] Cart 정합성 확인
- [ ] ORIGINAL_OUTBOUND Shipment 기반 실제 출고/배송완료 확인
- [ ] legacy fallback 제거 전 staging 회귀 확인
- [ ] dual-write 제거 전 migration/rollback 안정성 확인
- [ ] 반품 PG 환불/timeout/reconciliation/completion staging E2E 검증
- [ ] 교환 구현 후 회수/재배송/추가 배송비 통합 검증
- [ ] 운영 키 전환 전 Payment/Cancellation 전체 회귀

현재 docs 예제용 `test_gck_docs_...` / `test_gsk_docs_...` 계열을 사용하는 개발 테스트와, 상점용 테스트 키로 수행해야 하는 staging 최종 검증을 구분한다. 실제 Secret 값은 문서에 기록하지 않는다.

## 14. 다음 개발 우선순위 제안

### 1순위: Return Frontend

Shipment 도입과 개발 DB backfill/검증, OrderItem 반품/교환 배송비 snapshot, 구매자 반품 요청과 판매자 승인/거절·회수·입고·검수 Backend까지 완료됐다.

```text
OrderItem.returnShippingFee
OrderItem.exchangeShippingFee
```

구매자 반품 API:

```text
POST /api/orders/{orderId}/seller-orders/{sellerOrderId}/returns
GET  /api/orders/{orderId}/returns
GET  /api/returns/{returnRequestId}
```

판매자 검수 완료 transaction에서 환불 snapshot을 확정한 뒤 별도 workflow가 PaymentCancellation(PARTIAL)을 ReturnRequest에 연결하고 commit 후 PG를 호출한다. timeout/5xx 등 결과 불명은 REQUESTED/REFUNDING으로 유지하며 저장된 고정 멱등 키로 reconciliation하고 webhook도 Return 환불을 분기 처리한다. PG 성공 또는 0원 환불은 별도 completion transaction에서 returnedQuantity를 확정하고 RESTOCKABLE 수량만 재고와 restockedQuantity에 반영한 뒤 COMPLETED로 전이한다. SUCCEEDED + REFUNDING 고아 상태는 recovery가 멱등 재처리한다.

Return Backend 1~7은 완료됐다. Return Frontend와 Exchange는 미구현이며, 운영 staging 환경의 PG/반품 E2E 검증은 아직 필요하다.

### 2순위: 관리자 주문/결제 운영

- 전체 주문/결제/SellerOrder 조회
- FAILED / 장기 CONFIRMING / CANCELING / PROCESSING 관측
- 환불 실패 수동 대응 정책
- reconciliation 재실행/재조회 같은 안전한 운영 액션
- 상태 강제 변경은 최소화

### 운영 전 병행

- versioned DB migration 도입
- 공개 staging Toss 통합 검증
- 주문/결제/취소 integration test 확충

## 15. 기타 주의사항

- Shipment는 이미 도입됐으며 최초 배송 source of truth로 사용
- 새 UI에 Tailwind utility class 사용 금지
- Frontend 금액을 최종 신뢰하지 않음
- PG timeout/5xx를 실패로 단정하지 않음
- 실제 Secret/API Key/token을 코드·문서·로그에 기록하지 않음
- 기존 정상 전체취소/reconciliation을 신규 기능 때문에 불필요하게 재작성하지 않음

## 16. 새 세션 인계

```text
AGENTS.md와 docs/DEVELOPMENT_STATUS.md를 읽되 실제 최신 코드를 최종 기준으로 확인해.

현재 Payment 준비/승인/READY 만료/CONFIRMING reconciliation/Toss webhook/전체취소/CANCELING reconciliation이 구현되어 있다.

SellerOrder 판매자 주문관리와 구매자 판매자별 배송조회가 구현되어 있다.

SellerOrder 1:N Shipment가 구현되어 있고 최초 배송은 ORIGINAL_OUTBOUND Shipment가 source of truth다. SellerOrder 배송 컬럼은 migration/rollback snapshot이며 개발 DB backfill과 네 가지 검증 SQL 확인까지 완료됐다.

OrderCancellation + OrderCancellationItem 기반 상품/수량 부분취소, PAID 즉시취소, PREPARING 판매자 승인/거절, Toss 부분환불, Payment PARTIALLY_CANCELED, 부분 재고복원, 부분환불 reconciliation/webhook/orphan recovery, 구매자/판매자 cancellation UI까지 구현되어 있다.

DELIVERED SellerOrder의 반품 요청부터 판매자 검수, 환불 예정금액 snapshot, PaymentCancellation 기반 PG 부분환불·reconciliation, returnedQuantity·RESTOCKABLE 재고 복원과 COMPLETED까지 Backend가 구현되어 있다. Frontend와 Exchange는 아직 미구현이다.
운영 전 공개 staging + 상점용 Toss 테스트 키로 결제/전체취소/부분취소/webhook 전체 회귀가 필수다.
```
