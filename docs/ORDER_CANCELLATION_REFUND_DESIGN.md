# Gift Market 주문 부분취소 / 부분환불 설계

> 기준: 2026-08-17 최신 `gift-market.zip` 실제 코드 + Toss Payments 최신 공식 Core API 문서.  
> 이번 문서는 설계만 다루며 Java/TypeScript/DB 변경은 수행하지 않는다.

## 0. 결론

현재의 `PaymentCancellation`은 **PG 취소 거래 기록**으로 유지하고, 구매자/판매자의 업무 흐름은 별도 `OrderCancellation` + `OrderCancellationItem` 도메인으로 분리한다.

핵심 모델은 다음과 같다.

```text
Order 1 ─ N SellerOrder 1 ─ N OrderItem
  │                            │
  └─ 1 ─ N Payment            └─ canceledQuantity 추가
            │
            └─ 1 ─ N PaymentCancellation   ← 실제 PG 취소 transaction

SellerOrder 1 ─ N OrderCancellation
OrderCancellation 1 ─ N OrderCancellationItem
OrderCancellation 1 ─ 0..1 PaymentCancellation
```

정책:

- `PAID`: 구매자가 상품/수량 단위 즉시 취소 가능.
- `PREPARING`: 구매자는 취소 요청만 가능. 판매자 승인 후 동일한 부분환불 실행.
- `SHIPPED`, `DELIVERED`: 주문 취소 차단. 향후 반품/교환 도메인으로 분리.
- 일부 수량만 취소되면 `SellerOrder` 상태는 유지.
- SellerOrder의 모든 주문 수량이 취소된 경우에만 `SellerOrder.CANCELLED`.
- 배송비는 SellerOrder에 배송할 잔여 수량이 하나라도 있으면 환불하지 않는다. 전부 취소되어 배송 자체가 없어질 때 해당 SellerOrder에 귀속된 **원 주문 배송비 snapshot 합계**를 환불한다.
- 부분환불 후 Payment에는 잔액이 있으므로 `CANCELED`로 바꾸지 않는다. `PaymentStatus.PARTIALLY_CANCELED`를 추가하는 방향을 채택한다.
- 재고는 PG 취소 성공을 검증하여 cancellation을 최종 `COMPLETED`로 확정하는 동일 DB transaction에서 **이번 취소 확정 수량만 정확히 1회** 복원한다.
- timeout/5xx/응답 유실은 실패로 단정하지 않고 `PROCESSING`을 유지한 뒤 query/reconciliation으로 확정한다.

---

# 1. 현재 기존 전체취소 구조 분석

## 1.1 Legacy ORDERED 취소

`OrderService.cancelOrder()`는 `Order`를 pessimistic write lock으로 잡고 `ORDERED`만 허용한다.

```text
Order lock
→ OrderInventoryService.restore(orderId)
→ Order.CANCELLED
→ 모든 SellerOrder.CANCELLED
```

`OrderInventoryService.restore()`는 모든 `OrderItem`을 읽고 productId/variantId 순으로 Product/Variant row lock을 잡아 **주문 전체 수량**을 복원한다.

## 1.2 결제 전 취소

`PaymentCancellationTransactionService.start()`에서:

```text
Order PENDING_PAYMENT + Payment READY
→ 전체 재고 restore
→ Payment cancelBeforeApproval
→ Order CANCELLED
→ 모든 SellerOrder CANCELLED
```

PG 호출은 없다.

## 1.3 결제 완료 후 전체취소

현재 구매자 결제 취소는 `PaymentCancellationService` → `PaymentCancellationTransactionService`로 진행된다.

```text
Payment PAID + Order PAID
→ PaymentCancellation REQUESTED 생성
→ Payment CANCELING
→ Gateway.cancel()
→ 성공 또는 query/webhook/reconciliation 확인
→ Gateway 상태 CANCELED + remainingAmount=0 검증
→ 주문 전체 재고 restore
→ Payment CANCELED
→ PaymentCancellation SUCCEEDED
→ Order CANCELLED
→ 모든 SellerOrder CANCELLED
```

현재 `PaymentCancellation.amount`는 생성 시 무조건 `payment.amount` 전체 금액이다. Toss request DTO도 `cancelReason`만 보내므로 실제 구현은 전액 취소 전용이다.

## 1.4 현재 멱등성 / 불명 상태 처리

- `PaymentCancellation.clientRequestKey` unique
- `PaymentCancellation.idempotencyKey` unique
- PG cancel 요청에 동일 idempotency key 재사용
- timeout/5xx는 `PaymentGatewayUncertainException` 계열로 결과 불명 처리
- `Payment.CANCELING` 유지
- scheduler query 후 필요하면 동일 idempotency key로 재시도
- Toss webhook은 payload를 최종 신뢰하지 않고 payment query로 재검증

이 안정성 패턴은 부분환불에서도 그대로 보존한다.

---

# 2. 현재 코드와 인수인계의 차이 / 주의점

1. 압축본 루트에는 `AGENTS.md`가 없다. 인수인계에서 필수 읽기 대상으로 지정했지만 최신 압축본에는 포함되지 않았다.
2. 현재 배송비 snapshot은 `SellerOrder` 컬럼이 아니라 **각 `OrderItem.shippingFee`** 에 저장된다.
3. 현재 주문 준비 시 배송비도 판매자 묶음배송이 아니라 **CartItem/OrderItem별 배송비 합산**이다. 코드 주석도 향후 판매자 묶음배송/배송비 템플릿 도입을 별도 과제로 남긴다.
4. `PaymentStatus`에는 현재 `PARTIALLY_CANCELED`가 없고 `READY/CONFIRMING/PAID/FAILED/EXPIRED/CANCELING/CANCELED`만 있다.
5. Toss mapper는 `PARTIAL_CANCELED`를 의도적으로 `UNKNOWN`으로 매핑한다. 부분취소 도입 시 반드시 확장해야 한다.
6. `OrderInventoryService.restore(orderId)`는 전체 Order 전용이라 부분취소에 그대로 사용할 수 없다.
7. 판매자 배송 상태 전이는 `Order` lock → `SellerOrder` lock 순서다. 부분취소도 이 순서를 깨면 안 된다.

---

# 3. 문제점

현재 전체취소 구조를 그대로 부분취소에 확장하면 다음 문제가 발생한다.

- `Payment.CANCELING` 하나로 전체 Payment를 잠그면 여러 SellerOrder의 독립 취소가 불가능해진다.
- `PaymentCancellation.amount = payment.amount` 고정이라 부분금액을 기록할 수 없다.
- `OrderInventoryService.restore()`가 주문 전체 재고를 복원한다.
- `Order.cancel()`과 `SellerOrderLifecycleService.cancel(orderId)`가 전체 주문을 종료한다.
- OrderItem에 이미 취소된 수량이 없어 중복/추가 취소 가능 수량을 판단할 수 없다.
- Toss `PARTIAL_CANCELED`를 정상 상태로 해석하지 못한다.
- query 결과의 `balanceAmount`와 개별 취소 transaction을 연결할 구조가 없다.
- PREPARING의 판매자 승인/거절이라는 업무 상태를 PG transaction 상태인 `PaymentCancellation`에 넣으면 책임이 섞인다.

따라서 **주문 취소 업무 도메인**과 **PG 환불 거래 도메인**을 분리한다.

---

# 4. 목표 사용자 정책

| SellerOrder 상태 | 구매자 행동 | Backend 최종 정책 |
|---|---|---|
| PAID | 즉시 취소 | 요청 범위 검증 후 즉시 부분/전체 환불 처리 |
| PREPARING | 취소 요청 | `REQUESTED`, 판매자 승인 전 PG 호출 금지 |
| SHIPPED | 취소 불가 | Backend 차단, 향후 반품/교환 |
| DELIVERED | 취소 불가 | Backend 차단, 향후 반품/교환 |
| CANCELLED | 추가 취소 불가 | 완료 상태 반환 또는 명확한 거절 |

Frontend의 버튼 노출은 UX일 뿐이며 상태 검증의 최종 권위는 Backend다.

---

# 5. 부분취소 단위

API 입력은 한 번의 취소 요청이 **하나의 SellerOrder 안에서 여러 OrderItem과 각 수량**을 포함할 수 있도록 설계한다.

```text
OrderCancellation
- sellerOrderId
- reason
- items[]
  - orderItemId
  - quantity
```

이 구조 하나로 다음을 모두 지원한다.

- SellerOrder 전체 취소
- OrderItem 1개 전체 취소
- OrderItem 수량 일부 취소
- 같은 SellerOrder 내 여러 상품 동시 취소

한 cancellation에 서로 다른 SellerOrder를 섞지 않는다. 판매자 승인 책임, 배송비 판단, lock 범위를 명확히 하기 위해서다.

---

# 6. 멀티셀러 정책

Payment는 계속 Order 단위 1건이다.

Seller A만 취소하면:

```text
Order = PAID 유지
Payment = PARTIALLY_CANCELED
SellerOrder A = 전량 취소면 CANCELLED / 일부면 기존 상태 유지
SellerOrder B = 영향 없음
```

Order의 모든 SellerOrder가 전량 취소되고 PG 잔액이 0원이 된 경우에만:

```text
Payment = CANCELED
Order = CANCELLED
```

즉 `Order.status`는 부분취소 여부를 배송 처리 상태처럼 세분화하지 않는다. 부분취소 정보는 cancellation/item aggregate로 조회한다.

---

# 7. 배송비 환불 정책

## 7.1 현재 실제 코드 기준

현재 배송비는 `OrderItem.shippingFee` snapshot이며 주문 총 배송비는 모든 OrderItem 배송비 합계다.

따라서 현 단계에서 SellerOrder의 원 배송비는:

```text
sellerOrderOriginalShippingFee
= SUM(orderItem.shippingFee WHERE orderItem.seller_order_id = ?)
```

## 7.2 취소 시 정책

취소 반영 후 SellerOrder에 `remainingQuantity > 0`인 OrderItem이 하나라도 있으면:

```text
shippingRefund = 0
```

이번 취소로 SellerOrder의 모든 수량이 0이 되면:

```text
shippingRefund = sellerOrderOriginalShippingFee
```

단 이미 이전 취소에서 배송비를 환불한 적이 없어야 한다.

이를 위해 `OrderCancellation.shippingRefundAmount`를 snapshot으로 저장하고, 계산 시 **기존 COMPLETED cancellation의 shippingRefundAmount 합계**를 확인한다.

현재 주문 자체가 item별 배송비를 실제로 청구했으므로, SellerOrder 전량 취소 시 그 SellerOrder 소속 모든 item shippingFee의 원 합계를 돌려주는 것이 현재 금액 모델과 일치한다.

향후 판매자 묶음배송/조건부 무료배송이 생기면 이 규칙은 Shipping 정책으로 교체한다. 이번 구현에서는 과거 주문 금액을 현재 Product 배송비로 재계산하지 않는다.

---

# 8. ERD

```text
orders
  1
  ├────────────── N seller_orders
  │                    1
  │                    ├──────── N order_items
  │                    │              - quantity
  │                    │              - canceled_quantity NEW
  │                    │              - unit_price
  │                    │              - shipping_fee
  │                    │
  │                    └──────── N order_cancellations NEW
  │                                   - id
  │                                   - seller_order_id
  │                                   - user_id (requester buyer snapshot/FK)
  │                                   - client_request_key UNIQUE
  │                                   - reason
  │                                   - status
  │                                   - approval_type
  │                                   - product_refund_amount
  │                                   - shipping_refund_amount
  │                                   - refund_amount
  │                                   - requested_at
  │                                   - approved_at
  │                                   - rejected_at
  │                                   - completed_at
  │                                   - rejected_reason
  │                                      1
  │                                      └──── N order_cancellation_items NEW
  │                                                - order_cancellation_id
  │                                                - order_item_id
  │                                                - quantity
  │                                                - unit_refund_amount
  │                                                - refund_amount
  │
  └────────────── N payments
                         1
                         └──────── N payment_cancellations
                                      - order_cancellation_id NULLABLE NEW
                                      - amount (실제 cancel amount)
                                      - client_request_key UNIQUE
                                      - idempotency_key UNIQUE
                                      - status
                                      - provider_transaction_key
```

권장 unique:

- `order_cancellations.client_request_key`
- `order_cancellation_items(order_cancellation_id, order_item_id)`
- 기존 `payment_cancellations.client_request_key`
- 기존 `payment_cancellations.idempotency_key`
- `payment_cancellations.order_cancellation_id`는 1:1 연결을 원칙으로 unique nullable 권장

---

# 9. Cancellation Request 도메인

이름은 `OrderCancellation`을 권장한다. `CancellationRequest`라고만 하면 이미 완료된 즉시취소까지 “request”라는 이름에 묶이기 때문이다.

### OrderCancellationStatus

```text
REQUESTED       PREPARING에서 판매자 승인 대기
PROCESSING      승인 완료 또는 PAID 즉시취소가 PG 결과 확정 대기
COMPLETED       PG 취소 성공 + DB 수량/재고 반영 완료
REJECTED        판매자 거절
FAILED          명확한 PG 실패로 이번 시도 종료
```

`APPROVED`는 별도 장기 상태로 두지 않는다. 판매자 승인 transaction에서 `REQUESTED → PROCESSING`으로 바로 바꾸고 `approvedAt`을 기록한다. 승인됐지만 아직 PG 호출 전이라는 짧은 상태를 따로 노출하지 않아 상태 폭발을 막는다.

`FAILED` cancellation은 같은 business cancellation을 자동 재사용하지 않는다. 사용자가 다시 요청할 때 새 client request key/new cancellation을 만들되, **결과 불명은 FAILED로 만들지 않는다.**

---

# 10. PaymentCancellation 관계

`PaymentCancellation`은 계속 **PG 취소 transaction 기록**이다.

변경 방향:

- `amount`를 `payment.amount` 고정 생성하지 않고 실제 환불 요청 금액 저장
- `orderCancellation` nullable FK 추가
  - 새 부분취소: 연결 필수
  - 기존 전체취소 데이터: null 허용으로 migration 안전성 확보
- reason/idempotency/providerTransactionKey/status 구조는 재사용

업무 승인/거절 상태를 `PaymentCancellation`에 추가하지 않는다.

기존 전체취소 API는 단계적으로 `OrderCancellation` 경로로 통합하되, 첫 단계에서 기존 로직을 한꺼번에 제거하지 않는다.

---

# 11. OrderItem 취소 수량 관리

`OrderItem.canceledQuantity INT NOT NULL DEFAULT 0` 추가를 권장한다.

불변식:

```text
0 <= canceledQuantity <= quantity
remainingQuantity = quantity - canceledQuantity
```

`OrderCancellationItem.quantity`는 **이번 요청 수량 snapshot**이다.

환불 확정 전에는 `OrderItem.canceledQuantity`를 증가시키지 않는다. `REQUESTED/PROCESSING` 수량은 별도 cancellation item에 존재한다.

새 요청 검증 시:

```text
availableToCancel
= orderItem.quantity
- orderItem.canceledQuantity
- 동일 OrderItem에 대해 REQUESTED/PROCESSING 중인 다른 cancellation quantity
```

따라서 판매자 승인 대기 중인 수량을 다른 요청이 중복 점유하지 못한다.

---

# 12. SellerOrder 상태 정책

- 일부 취소: 기존 `PAID` 또는 `PREPARING` 유지
- 전량 취소 완료: `CANCELLED`
- `REQUESTED`만 존재: 상태 변경 없음
- `PROCESSING` 중: 상태 변경 없음, 단 배송 시작은 차단
- `REJECTED/FAILED`: 기존 배송 상태 유지

전량 여부:

```text
SUM(orderItem.quantity - orderItem.canceledQuantity) == 0
```

`SellerOrder.cancel()`은 최종 전량 취소 확정 시에만 호출한다.

---

# 13. Payment 부분취소 상태 정책

`PaymentStatus.PARTIALLY_CANCELED` 추가를 권장하고 이번 설계에서 채택한다.

```text
remainingAmount == payment.amount  → PAID
0 < remainingAmount < payment.amount → PARTIALLY_CANCELED
remainingAmount == 0 → CANCELED
```

`Payment.CANCELING`은 기존 “전액 취소 1건 진행 중” 의미가 강하고 Payment 전체를 직렬화한다. 부분취소에서는 Payment status를 `CANCELING`으로 바꾸지 않고 `OrderCancellation.PROCESSING + PaymentCancellation.REQUESTED`로 진행 상태를 표현한다.

단 **Payment row lock으로 PG 취소 시작을 직렬화**하여 한 Payment에 동시에 두 개의 부분취소가 PG로 나가지 않게 한다. 첫 구현은 안전성을 위해 Payment당 active PG cancellation 1건만 허용한다.

Gateway enum에는 `PARTIALLY_CANCELED`를 추가한다. Toss `PARTIAL_CANCELED`를 더 이상 `UNKNOWN`으로 매핑하지 않는다.

---

# 14. Toss 부분취소 구조

2026-08-17 확인한 Toss 공식 Core API 기준:

```text
POST /v1/payments/{paymentKey}/cancel
Idempotency-Key: <same key on retry>

{
  "cancelReason": "...",
  "cancelAmount": 10000
}
```

- `cancelAmount` 생략: 전액 취소
- `cancelAmount` 지정: 부분취소
- 같은 멱등키 재요청은 중복 취소 방지
- Payment 응답의 `balanceAmount`로 남은 결제금액 확인
- `cancels[]`의 각 취소 거래에 `cancelAmount`, `transactionKey`, `cancelStatus`, `canceledAt`, `refundableAmount` 등이 존재
- 부분취소 후 provider payment status는 `PARTIAL_CANCELED`, 전액 소진 후 `CANCELED`
- 일부 결제수단/상황은 부분취소 제한이 있으므로 provider 명확 실패는 사용자에게 반환하고 DB 금액/재고는 변경하지 않는다.

현재 `TossCancelRequest(cancelReason)`은 `cancelAmount`를 받을 수 있게 확장해야 하고, provider-neutral `GatewayCancelCommand.amount`는 이미 존재하므로 adapter 경계는 유지 가능하다.

현재 `GatewayCancelResult.amount`는 Toss의 `totalAmount`를 담는다. 부분취소에서는 혼동을 피하기 위해 gateway result에 최소 다음을 명확히 분리해야 한다.

```text
originalAmount
remainingAmount
latestCancelAmount
latestCancelTransactionId
```

query 결과도 `cancels[]`를 통해 **우리 PaymentCancellation의 providerTransactionKey 또는 기대 cancel amount/idempotent 결과**를 식별할 수 있어야 한다.

---

# 15. Backend refund amount 계산식 / 규칙

Frontend는 금액을 보내지 않는다.

각 cancellation item:

```text
itemRefund = OrderItem.unitPrice * requestedQuantity
```

현재 `unitPrice = productPrice snapshot + additionalPrice snapshot`이므로 현재 상품 가격을 조회하지 않는다.

상품 환불 합계:

```text
productRefundAmount = Σ itemRefund
```

배송비:

```text
shippingRefundAmount =
  이번 취소 확정 후 sellerOrder remainingQuantity == 0
  AND 이전 COMPLETED cancellation에서 shipping fee 환불 이력 없음
    ? Σ sellerOrder.orderItems.shippingFee
    : 0
```

최종:

```text
refundAmount = productRefundAmount + shippingRefundAmount
```

필수 검증:

- refundAmount > 0
- 모든 OrderItem이 같은 sellerOrder
- sellerOrder가 해당 Order 소속
- 구매자가 Order owner
- 요청 수량 > 0
- 요청 수량 <= availableToCancel
- snapshot 곱셈 `Math.multiplyExact` 또는 동등 overflow 방어
- 합계 `Math.addExact` 또는 동등 overflow 방어
- `refundAmount <= paymentRemainingAmount`
- `sum(COMPLETED PaymentCancellation.amount) + refundAmount <= payment.amount`
- Payment currency 일치
- active PG cancellation 중복 금지

`Order.totalAmount`, `totalProductAmount`, `totalShippingFee`는 **원 주문 snapshot**으로 유지한다. 부분취소 때 원 주문 금액을 덮어쓰지 않는다. 화면에는 original / canceled / net paid를 별도 계산해 보여준다.

---

# 16. 재고 복원

현재 `OrderInventoryService.restore(orderId)`는 전체복원 전용으로 유지한다.

작은 확장:

```text
restore(orderId)                         ← 기존 전체 주문 호환
restoreQuantities(List<InventoryRestoreItem>)  ← 신규 부분복원
```

신규 입력은 `orderItemId + restoreQuantity`처럼 최소화한다.

구현 원칙:

- OrderItem snapshot에서 Product/Variant를 결정
- Product/Variant lock 순서는 기존과 동일하게 `productId → variantId`
- 옵션 없는 상품: Product stock 증가
- Variant: Variant stock 증가 후 해당 Product 총재고 sync
- `COMPLETED`로 전환하는 transaction 안에서 1회만 실행
- `OrderItem.canceledQuantity` 증가와 재고 복원을 같은 transaction에 둔다.

재고 복원 전에 cancellation이 이미 `COMPLETED`인지 확인하여 webhook/reconciliation 중복 호출을 no-op 처리한다.

---

# 17. 멱등성

두 층으로 나눈다.

### Business request

`OrderCancellation.clientRequestKey` unique.

동일 key 재요청:

- 동일 sellerOrder + 동일 item/quantity + 동일 reason: 기존 상태 반환
- payload 불일치: 400

### PG request

`PaymentCancellation.idempotencyKey` unique.

- 최초 PG 호출 전에 DB에 생성/commit
- timeout/5xx 재시도는 반드시 같은 key
- 새 key로 같은 refund를 재시도하지 않음

---

# 18. Transaction boundary

외부 Toss HTTP 호출을 DB transaction 안에서 오래 잡지 않는다.

### PAID 즉시취소

```text
TX-A start
  Order lock
  SellerOrder lock
  Payment lock
  OrderItem/cancellation 검증
  환불금 계산
  OrderCancellation PROCESSING 생성
  PaymentCancellation REQUESTED 생성
commit

Toss cancel HTTP

TX-B complete
  Order lock
  SellerOrder lock
  Payment lock
  OrderCancellation lock
  PaymentCancellation lock
  결과/잔액/transaction 검증
  OrderItem.canceledQuantity 증가
  정확 수량 재고 restore
  전량이면 SellerOrder CANCELLED
  Payment PARTIALLY_CANCELED 또는 CANCELED
  전 Order 전량 + balance 0이면 Order CANCELLED
  PaymentCancellation SUCCEEDED
  OrderCancellation COMPLETED
commit
```

### PREPARING 요청

요청 생성 transaction에는 PG 호출이 없다.

판매자 승인 시 start transaction으로 `REQUESTED → PROCESSING` 및 PaymentCancellation 생성 후 commit, 그 뒤 Toss 호출, 이후 complete transaction을 실행한다.

---

# 19. Lock order / race condition

현재 판매자 배송 전이는 이미:

```text
Order lock → SellerOrder lock
```

부분취소는 이를 보존하고 다음 순서를 표준으로 한다.

```text
1. Order
2. SellerOrder (id ASC; 이번 API는 1개)
3. Payment
4. OrderCancellation
5. PaymentCancellation
6. OrderItem (필요 시 id ASC)
7. Product (productId ASC)
8. ProductVariant (variantId ASC)
```

중요한 race:

### PREPARING 승인 vs SHIPPED

둘 다 `Order → SellerOrder` lock을 먼저 잡는다.

- 승인 transaction이 먼저: cancellation을 `PROCESSING`으로 만든다. `ship()` 서비스는 active `PROCESSING` cancellation이 있으면 출고 차단.
- ship transaction이 먼저: SellerOrder가 `SHIPPED`; 승인 transaction은 상태 재검증 후 승인 차단.

### Buyer request vs SHIPPED

요청 생성도 `Order → SellerOrder` lock 후 상태 재검증. SHIPPED가 먼저면 요청 생성 실패.

### 두 부분취소 동시 실행

Payment lock + active PaymentCancellation 제한으로 PG 취소는 직렬화. OrderItem available quantity도 lock 상태에서 재검증.

### 기존 전체취소 vs 부분취소

기존 전체취소 경로도 장기적으로 동일 cancellation orchestrator로 통합해야 한다. 통합 전 단계에서는 Payment에 active partial cancellation이 있으면 legacy full cancel을 차단하고 반대도 동일하게 차단한다.

---

# 20. timeout / 5xx / 결과 불명

부분취소에서도 원칙은 기존과 같다.

- timeout / connection reset / 5xx → `FAILED`로 확정하지 않음
- `OrderCancellation.PROCESSING`
- `PaymentCancellation.REQUESTED`
- 재고/취소수량/배송비 환불 상태 미반영
- scheduler가 Payment query

명확한 4xx/provider decline만 `FAILED` 처리 가능하되, 이미 처리되었을 가능성이 있는 오류코드는 query 우선 정책을 유지한다.

---

# 21. Webhook / reconciliation

현재 webhook은 query 재검증 패턴을 사용하므로 유지한다.

부분취소에서는 단순히 `Payment status == CANCELING` 여부로 취소 진행을 찾으면 안 된다.

새 탐색 기준:

```text
Payment에 REQUESTED 상태 PaymentCancellation 존재 여부
```

query가 `PARTIAL_CANCELED`이면:

1. `cancels[]`에서 해당 취소 거래를 식별
2. 기대 cancelAmount와 transactionKey/cancel status 검증
3. balanceAmount 검증
4. 해당 `OrderCancellation`만 complete

한 Payment에 active cancellation 1건 정책을 적용하면 매칭이 단순하고 안전하다.

reconciliation 후보도 `Payment.CANCELING`만 조회하지 말고 `PaymentCancellation.status=REQUESTED AND requestedAt <= ...` 기준으로 확장한다.

---

# 22. API 설계 초안

## Buyer

```http
POST /api/orders/{orderId}/cancellations
```

```json
{
  "clientRequestKey": "uuid",
  "sellerOrderId": 123,
  "reason": "단순 변심",
  "items": [
    { "orderItemId": 1001, "quantity": 1 }
  ]
}
```

동작:

- SellerOrder PAID → 즉시 PROCESSING/PG 취소
- PREPARING → REQUESTED 반환
- SHIPPED/DELIVERED → 400

조회:

```http
GET /api/orders/{orderId}/cancellations
GET /api/orders/{orderId}/cancellations/{cancellationId}
```

## Seller

```http
GET  /api/seller/orders/{sellerOrderId}/cancellations
PATCH /api/seller/orders/{sellerOrderId}/cancellations/{cancellationId}/approve
PATCH /api/seller/orders/{sellerOrderId}/cancellations/{cancellationId}/reject
```

거절 body:

```json
{ "reason": "이미 포장 및 출고 준비가 완료되었습니다." }
```

Seller ownership은 기존 SellerOrder API와 동일하게 Security + active Seller + sellerId ownership을 모두 검증한다.

기존 `POST /api/orders/{orderId}/cancel`은 호환 API로 당분간 유지하되 새 cancellation 서비스로 위임하는 migration을 권장한다.

---

# 23. 구매자 UI 흐름

주문 상세를 SellerOrder 그룹 단위로 표시하면서 각 OrderItem에:

```text
주문수량 / 취소완료수량 / 취소가능수량
```

을 노출한다.

PAID:

```text
[취소] → 상품/수량 선택 → 사유 → 예상 환불 내역 확인 → 즉시 처리
```

PREPARING:

```text
[취소 요청] → 상품/수량 선택 → 사유 → 요청 완료
→ 승인 대기 / 승인 처리 중 / 완료 / 거절 상태 표시
```

SHIPPED/DELIVERED:

```text
취소 버튼 없음
향후 반품/교환 영역으로 연결
```

예상 환불금은 UX용으로 Backend preview API를 추가할 수 있으나, 최종 실행 API에서 반드시 다시 계산한다. 1차 구현에서는 실행 응답에 Backend 계산 breakdown을 반환하는 방식으로 시작해도 된다.

---

# 24. 판매자 승인/거절 UI 흐름

판매자 주문 상세에 PREPARING cancellation request를 표시한다.

```text
취소 요청 상품
요청 수량
사유
요청 시각
예상 환불금(Backend 계산값)
[승인] [거절]
```

승인 클릭 시 double click 방지 UI를 적용하되 Backend 멱등성이 최종 방어다.

active `PROCESSING` cancellation이 있으면 출고 버튼을 비활성화하고 Backend도 출고를 차단한다.

---

# 25. SHIPPED 이후 반품/교환과의 경계

이번 도메인은 `SellerOrder.status in (PAID, PREPARING)`에서의 **주문 취소**만 담당한다.

`SHIPPED`, `DELIVERED`는 `OrderCancellation` 생성 자체를 거절한다.

후속 범위:

```text
ReturnRequest
ExchangeRequest
ReturnItem
회수 배송 / 반품 배송비 / 검수 / 재환불
```

이를 이번 cancellation 테이블에 억지로 넣지 않는다.

---

# 26. 기존 데이터 migration 필요 여부

필요하다. 단 이번 문서 단계에서는 실행하지 않는다.

예상 migration:

1. `order_items.canceled_quantity INT NOT NULL DEFAULT 0`
2. `order_cancellations` 신규 테이블
3. `order_cancellation_items` 신규 테이블
4. `payment_cancellations.order_cancellation_id BIGINT NULL`
5. `payment_cancellations.amount`는 기존 데이터 유지, 생성 로직만 실제 취소금액으로 변경
6. enum 문자열 컬럼은 `PARTIALLY_CANCELED` 길이 30 안에 들어가므로 컬럼 길이 변경 불필요

기존 완료 주문/전체취소 데이터는 `canceled_quantity` backfill이 필요하다.

- `orders.status=CANCELLED`이고 실제 기존 전체취소로 종료된 주문의 OrderItem은 `canceled_quantity=quantity`로 backfill 권장
- 단 legacy `ORDERED` 취소와 Payment 취소 모두 포함되는지 SQL 사전 검증 필요
- 신규 cancellation 이력 자체를 과거 데이터에서 억지로 생성하지 않아도 된다. PaymentCancellation은 기존 PG 거래 기록으로 보존한다.

운영 migration 도구는 별도 TODO인 versioned migration 도입과 함께 처리하는 것이 바람직하다.

---

# 27. 단계별 구현 로드맵

## Cancellation 1 — Domain / DB / 조회 기반

- OrderCancellation / OrderCancellationItem
- OrderItem.canceledQuantity
- repository lock/query
- response DTO에 취소수량/가능수량
- migration/backfill SQL + 검증 SQL
- 기존 기능 회귀 테스트

## Cancellation 2 — PAID 즉시 부분취소

- Backend 금액 계산기
- 부분 inventory restore
- Gateway partial cancel command/result 확장
- Toss cancelAmount
- `PARTIALLY_CANCELED` 매핑
- 부분취소 completion transaction
- SellerOrder 전량 판정
- 기존 전체취소를 새 경로와 공존시키되 상호 active guard

## Cancellation 3 — PREPARING 요청/승인/거절

- Buyer REQUESTED
- Seller 조회/approve/reject
- 승인 vs ship 동일 lock order
- PROCESSING 중 출고 차단

## Cancellation 4 — Frontend

- 구매자 상품/수량 취소 UI
- cancellation 상태 표시
- 판매자 승인/거절 UI
- 배송 단계별 버튼 정책

## Cancellation 5 — 안정성 통합

- PaymentCancellation 기반 reconciliation 후보 조회
- PARTIAL_CANCELED query 검증
- cancels[] transaction matching
- webhook partial cancellation 처리
- timeout/5xx 재시도
- 중복 webhook/중복 승인/동시 취소 테스트

## Cancellation 6 — 기존 전체취소 통합/정리

- legacy full cancel을 새 orchestrator로 위임
- 중복 코드 제거는 기능 검증 후 최소 범위로 수행
- 전체취소 회귀 테스트

기존 제안의 5단계보다 6단계로 나누는 이유는 **기존 전체취소 리팩토링을 마지막으로 미뤄 부분취소 개발 중 회귀 위험을 낮추기 위해서**다.

---

# 28. 테스트 시나리오

## 금액

- 단일 item 1/1 취소
- quantity 2 중 1 취소
- quantity 10에서 3 → 2 → 5 순차 취소
- negative additionalPrice 포함
- SellerOrder 일부 취소 배송비 0 환불
- 마지막 잔여 item 취소 시 배송비 전체 환불
- 이미 배송비 환불 후 중복 배송비 환불 금지
- Payment remainingAmount보다 큰 환불 차단

## 멀티셀러

- Seller A 일부 취소, B 불변
- Seller A 전량 취소, B 배송 계속
- A/B 모두 전량 취소 후 Order/Payment 최종 CANCELED

## 상태

- PAID 즉시취소 성공
- PREPARING 요청 생성
- PREPARING 승인 성공
- PREPARING 거절
- SHIPPED 생성/승인 차단
- DELIVERED 차단
- 일부 취소 후 SellerOrder PREPARING 유지
- 전량 취소 후 SellerOrder CANCELLED

## 동시성

- buyer double click same key
- buyer double click different key same quantity
- seller approve double click
- approve vs ship
- partial cancel vs legacy full cancel
- two seller partial cancels same Payment
- webhook vs HTTP response completion
- reconciliation vs webhook

## 장애

- Toss 200 PARTIAL_CANCELED
- Toss timeout but 실제 취소 성공
- Toss 5xx but 실제 취소 성공
- Toss 명확 4xx 실패
- query PARTIAL_CANCELED + expected cancel 발견
- query PARTIAL_CANCELED but expected transaction 불명 → 계속 PROCESSING
- duplicate webhook

## 재고

- Product exact quantity restore
- Variant exact quantity restore
- Product aggregate stock sync
- retry/webhook에서 2회 restore 금지

## 회귀

- payment confirm
- READY expiration
- CONFIRMING reconciliation
- 전체취소
- CANCELING reconciliation
- 장바구니 결제 후 안전 삭제
- 바로구매 cart 불변
- 판매자 PAID→PREPARING→SHIPPED→DELIVERED
- 구매자 deliveryStatus

---

# 29. 기존 기능 회귀 위험

가장 큰 위험 순서:

1. Toss `PARTIAL_CANCELED`를 기존 mapper가 UNKNOWN으로 처리하는 문제
2. 기존 전체취소가 Payment.CANCELING 하나에 의존하는 구조와 신규 부분취소의 충돌
3. 전체 `restore(orderId)`를 실수로 부분취소에 호출하는 문제
4. SellerOrder 전량 여부를 잘못 계산해 다른 상품 배송을 취소하는 문제
5. 배송비가 SellerOrder 컬럼이 아니라 OrderItem별 snapshot이라는 점을 놓치는 문제
6. webhook이 최신 REQUESTED PaymentCancellation을 잘못 매칭하는 문제
7. 판매자 ship과 승인 race
8. 기존 구매자 UI가 `Order.status=PAID`만 보고 “주문 전체 취소”를 노출하는 문제

따라서 기존 전체취소 코드를 처음부터 대규모 재작성하지 않고 신규 도메인을 옆에 추가한 뒤 마지막 단계에서 통합한다.

---

# 30. 운영 전 검증

기존 `DEVELOPMENT_STATUS.md`의 Toss 최종 staging TODO는 삭제/완료 처리하지 않는다.

부분취소 추가 후 staging에서 추가 검증:

- 내 상점용 Toss test_gck/test_gsk
- 실제 부분취소 1회/다회
- `PARTIAL_CANCELED` query
- `cancels[]` 누적 및 transactionKey
- `balanceAmount`
- 부분취소 webhook 재전송
- timeout 후 동일 Idempotency-Key 재시도
- 마지막 잔액 취소 후 `CANCELED`
- 멀티셀러 A만 취소 후 B 배송
- 부분 수량 재고 정확 복원
- 배송비 마지막 취소 시 1회만 환불
- 전체취소 기존 기능 회귀
- 결제수단별 부분취소 가능/불가 정책 확인

---

# 31. 구현 시 지켜야 할 불변식

```text
OrderItem.canceledQuantity <= OrderItem.quantity

Payment original amount
= provider remaining amount
+ SUM(successful PaymentCancellation.amount)

OrderCancellation.COMPLETED
=> 연결 PaymentCancellation.SUCCEEDED
=> requested quantity가 OrderItem.canceledQuantity에 반영됨
=> 동일 수량 재고 복원 완료

SellerOrder.CANCELLED
<=> 모든 OrderItem remainingQuantity == 0
   (cancellation 기능이 종료시킨 경우)

Order.CANCELLED (결제완료 주문)
=> 모든 SellerOrder remainingQuantity == 0
=> Payment remainingAmount == 0
=> Payment.CANCELED
```

DB와 provider 상태가 불명확하면 재고/취소수량을 먼저 확정하지 않는다.

---

# 32. 이번 설계에서 추가 결정이 필요 없는 항목

현재 요구사항과 실제 코드만으로 1차 구현 정책은 충분히 확정 가능하다.

- Shipment 추가하지 않음
- SellerOrder 단위로 cancellation을 분리
- item/quantity 부분취소 지원
- 배송비는 SellerOrder 전량 취소 시만 환불
- Payment는 Order 단위 유지
- PREPARING은 판매자 승인
- SHIPPED 이후는 반품/교환으로 분리
- PaymentCancellation은 PG 거래 기록으로 유지
- OrderCancellation 별도 도메인 도입
- PaymentStatus.PARTIALLY_CANCELED 도입
- 한 Payment의 PG cancellation은 첫 구현에서 직렬화

따라서 구현 시작 전에 사용자에게 추가 정책 질문이 필요한 항목은 현재 없다.
