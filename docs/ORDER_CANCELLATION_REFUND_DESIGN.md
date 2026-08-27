# Gift Market 주문 부분취소 / 부분환불 설계

> 최종 갱신: 2026-08-25
>
> 최초 설계 문서를 현재 실제 구현에 맞게 갱신한 문서다. 아래의 "현재 구현"은 저장소 코드를 기준으로 한다.

## 1. 목표와 범위

Order 전체를 판매자별 Payment로 쪼개지 않고, 하나의 Payment 안에서 SellerOrder/OrderItem 단위의 부분취소·부분환불을 안전하게 처리한다.

핵심 원칙:

- 취소 업무와 PG 환불 transaction을 분리
- Frontend 금액 불신
- Backend 주문 snapshot 기준 환불액 계산
- PG 성공 확정 후에만 `canceledQuantity`와 재고 반영
- timeout/5xx/응답 유실을 실패로 단정하지 않음
- 중복 환불/중복 재고복원 방지
- 멀티셀러 SellerOrder 간 상태 독립성 유지
- SHIPPED 이후는 반품/교환과 분리
- Cancellation 자체에는 Shipment를 결합하지 않음. Return/Exchange는 별도 Shipment 도메인을 사용함

## 2. 현재 주문/결제 구조

```text
Order
├─ Payment
│  └─ PaymentCancellation N
│
├─ SellerOrder A
│  ├─ OrderItem
│  ├─ OrderItem
│  ├─ Shipment N
│  └─ OrderCancellation N
│      └─ OrderCancellationItem N
│
└─ SellerOrder B
   └─ ...
```

- `Order`: 한 번의 구매자 결제 거래
- `Payment`: Order 전체 결제
- `SellerOrder`: 판매자별 처리/배송 단위
- `OrderItem`: 상품/옵션/가격/배송비 snapshot
- `OrderCancellation`: 주문 취소 업무
- `OrderCancellationItem`: 이번 요청의 상품/수량
- `PaymentCancellation`: PG 취소 transaction

## 3. 현재 구현 완료 상태

현재 코드는 최초 설계의 Cancellation 1~5 범위를 구현했다.

완료:

- `OrderCancellation`
- `OrderCancellationItem`
- `OrderCancellationStatus`
- `OrderItem.canceledQuantity`
- PAID 즉시 부분취소
- PREPARING 판매자 승인/거절
- SHIPPED/DELIVERED 취소 차단
- SellerOrder 전체/OrderItem/일부 수량 취소
- Backend 환불 금액 계산
- SellerOrder 전량 취소 시 배송비 환불
- `PaymentCancellationType.PARTIAL`
- `PaymentStatus.PARTIALLY_CANCELED`
- Toss `cancelAmount`
- Toss `PARTIAL_CANCELED` mapping
- 부분취소 PG idempotency
- 부분 재고복원
- 부분환불 reconciliation
- Toss webhook 연계
- orphan PROCESSING recovery
- 구매자 cancellation UI
- 판매자 cancellation 관리 UI
- 누적 환불액/결제잔액 표시

미완료:

- 공개 staging 실제 부분취소/webhook 최종 통합 검증
- 운영자 FAILED/장기 PROCESSING 관측 및 수동 대응

Return/Exchange는 별도 도메인으로 구현 완료됐으며 Cancellation의 미완료 범위가 아니다.

## 4. 사용자 정책

| SellerOrder 상태 | 구매자 행동 | 처리 |
|---|---|---|
| PAID | 즉시취소 | 판매자 승인 없이 부분환불 실행 |
| PREPARING | 취소요청 | 판매자 승인/거절 |
| SHIPPED | 주문취소 불가 | 배송 완료 후 Return/Exchange 정책 적용 |
| DELIVERED | 주문취소 불가 | 별도 Return/Exchange 요청 |
| CANCELLED | 추가 취소 불가 | 종료 |

Frontend에서 버튼을 숨기는 것과 별개로 Backend가 상태를 최종 검증한다.

## 5. 부분취소 단위

한 번의 `OrderCancellation`은 하나의 SellerOrder 안에서 여러 OrderItem과 각각의 수량을 포함할 수 있다.

```text
OrderCancellation
├─ Item A × 1
└─ Item B × 2
```

`OrderItem.quantity`는 원 주문 수량 snapshot으로 유지한다.

```text
remainingQuantity = quantity - canceledQuantity
```

REQUESTED/PROCESSING 중인 요청 수량은 새 요청의 `availableCancellationQuantity`에서 추가로 차감한다.

## 6. Cancellation 업무 상태

`OrderCancellationStatus`:

```text
REQUESTED
PROCESSING
COMPLETED
REJECTED
FAILED
```

의미:

- `REQUESTED`: 판매자 승인 대기 또는 환불 실행 전 요청 상태
- `PROCESSING`: 환불 처리 진행 중
- `COMPLETED`: PG 환불과 주문/재고 반영 완료
- `REJECTED`: 판매자가 승인형 요청 거절
- `FAILED`: 명확한 실패 또는 자동 복구 불가능한 내부 준비 실패

`requiresSellerApproval` snapshot으로 PAID 즉시형과 PREPARING 승인형을 구분한다.

## 7. PaymentCancellation 역할

`PaymentCancellation`은 계속 PG 취소 transaction 기록이다.

`PaymentCancellationType`:

```text
FULL
PARTIAL
```

- FULL: 기존 전체 결제 취소
- PARTIAL: `OrderCancellation` 또는 배송 후 `ReturnRequest`와 연결되는 부분환불

Cancellation 범위에서는 하나의 `OrderCancellation`에 하나의 PaymentCancellation이 연결되도록 제약한다. Return 환불은 별도 nullable `returnRequest` FK와 UNIQUE를 사용하며, PARTIAL 업무 source는 `OrderCancellation XOR ReturnRequest`가 되도록 Backend에서 검증한다.

`FULL / PARTIAL`은 결제 취소 금액 범위이고 연결 FK는 취소/반품이라는 업무 원인을 나타낸다. Return 도입 후에도 Cancellation 상태와 승인 workflow는 기존 `OrderCancellation`이 담당한다.

업무 승인/거절 상태를 PaymentCancellation에 넣지 않는다.

## 8. Payment 부분취소 상태

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

부분환불 후:

```text
remainingAmount > 0
→ PARTIALLY_CANCELED

remainingAmount == 0
→ CANCELED
```

Order는 일부 상품이 취소되어도 원 전체 주문 snapshot과 배송 흐름이 남으므로 `PAID`를 유지한다. SellerOrder가 전량 취소될 때만 해당 SellerOrder를 `CANCELLED` 처리한다.

## 9. 환불 금액 계산

환불액은 `OrderCancellationRefundCalculator`가 DB snapshot으로 계산한다.

### 상품금액

```text
productRefundAmount
= Σ(OrderItem.unitPrice × requestedQuantity)
```

현재 `OrderItem.unitPrice`는 주문 당시 최종 단가 snapshot이며 Frontend 요청 금액은 사용하지 않는다.

### 배송비

현재 배송비 snapshot은 `OrderItem.shippingFee`다.

```text
이번 취소 후 SellerOrder에 잔여 상품 수량 존재
→ shippingRefundAmount = 0

이번 취소 후 SellerOrder 모든 상품 잔여 수량 0
→ shippingRefundAmount = Σ(original OrderItem.shippingFee)
```

환불 예정액:

```text
totalRefundAmount
= productRefundAmount + shippingRefundAmount
```

오버플로는 `Math.addExact` / `Math.multiplyExact`로 방어한다.

## 10. 환불 가능 잔액

`PaymentRefundBalanceService`는 PG 환불 가능 금액을 DB transaction 기록 기준으로 제한한다.

```text
availableRefundAmount
= payment.amount
- SUM(SUCCEEDED PaymentCancellation.amount)
- SUM(REQUESTED PaymentCancellation.amount)
```

PARTIAL 준비 시 이번 `refundAmount <= availableRefundAmount`를 검증한다.

이 구조로 동시에 두 요청이 원 결제금액을 초과해 예약/환불되는 것을 방어한다.

## 11. Toss 부분취소

Provider-neutral gateway를 유지한다.

```text
OrderCancellationRefundExecutionService
→ PaymentGateway
→ TossPaymentGateway
→ TossPaymentClient
```

현재 구현:

- `GatewayCancelCommand.cancelAmount`
- FULL이면 `cancelAmount=null`
- PARTIAL이면 `cancelAmount>0`
- Toss request body는 `cancelReason` + PARTIAL의 `cancelAmount`
- `Idempotency-Key` 재사용
- Toss 최신 payment query로 paymentKey/orderId/amount/currency 검증
- `isPartialCancelable` 검증
- Toss `PARTIAL_CANCELED` → 내부 `PARTIALLY_CANCELED`
- `cancels[]`의 transactionKey/cancelAmount/cancelReason/cancelStatus/refundableAmount 사용

PG 응답만 보고 상품/재고를 먼저 변경하지 않는다.

## 12. 정상 부분환불 transaction 경계

### Transaction A — 준비

개념상 순서:

```text
Payment lock
→ Order lock
→ SellerOrder lock
→ OrderCancellation lock
→ 관련 OrderItem 검증/lock
→ 환불액 재계산
→ Payment refund balance 검증
→ PaymentCancellation(PARTIAL) 준비
→ 고정 PG idempotency key 저장
→ commit
```

### 외부 PG

```text
Toss 단건 조회
→ 식별정보/부분취소 가능 여부 확인
→ cancelAmount + 동일 idempotency key 취소 호출
```

DB transaction을 열어 둔 채 외부 PG HTTP를 기다리지 않는다.

### Transaction B — 성공 확정

```text
Payment/Order/SellerOrder/OrderCancellation/PaymentCancellation 재잠금
→ PG 응답 식별정보/금액/잔액 검증
→ PaymentCancellation SUCCEEDED
→ Payment PARTIALLY_CANCELED 또는 CANCELED
→ OrderItem.canceledQuantity 증가
→ 취소 수량 재고 복원
→ SellerOrder 전량 취소 여부 판단
→ OrderCancellation COMPLETED
→ commit
```

## 13. 재고 복원

부분취소는 전체취소용 `restore(orderId)`를 사용하지 않는다.

현재 `OrderInventoryService.restoreCancellationItems(...)`를 사용한다.

- CancellationItem의 요청 수량만 복원
- Product/Variant 기존 잠금/재고 primitive 재사용
- Variant stock과 Product 총재고 기존 정책 유지
- `OrderCancellation.COMPLETED`를 멱등성 장벽으로 사용

PG 성공 확인 전에 재고를 복원하지 않는다.

## 14. SellerOrder 상태 정책

일부 상품/수량만 취소:

```text
SellerOrder 상태 유지
```

모든 OrderItem이 전량 취소:

```text
SellerOrder → CANCELLED
```

다른 판매자의 SellerOrder는 변경하지 않는다.

예:

```text
Order
├─ Seller A: 전량 취소 → CANCELLED
└─ Seller B: 배송 계속 → PREPARING/SHIPPED/DELIVERED
```

## 15. PREPARING 판매자 승인

PREPARING에서는 구매자 요청 즉시 PG를 호출하지 않는다.

```text
Buyer request
→ OrderCancellation REQUESTED
   requiresSellerApproval=true

Seller approve
→ PROCESSING
→ 공통 부분환불 executor

Seller reject
→ REJECTED
```

판매자 API는 자기 승인형 취소요청만 조회/처리한다.

## 16. 배송 시작과 승인 경합

취소 승인과 SHIPPED 전이는 동시에 확정되면 안 된다.

현재 배송/취소 처리에서 Order/SellerOrder 관련 row lock과 상태 재검증을 사용한다.

- 활성 REQUESTED/PROCESSING 취소가 있는 PREPARING SellerOrder는 배송 시작 차단
- 배송 시작이 먼저 SHIPPED를 확정하면 취소 승인 불가
- 승인/환불 흐름이 먼저 진행되면 배송 lifecycle이 활성 취소 상태를 보고 차단

구현 변경 시 기존 lock order를 깨지 않는다.

## 17. 멱등성

### 구매자 요청

`OrderCancellation.clientRequestKey` unique.

같은 요청 키 재전송은 새 취소 업무를 중복 생성하지 않는다.

### PG 취소

`PaymentCancellation`의 PG idempotency key를 저장하고 동일 transaction 재시도에서 그대로 재사용한다.

새 retry에서 임의로 새 키를 생성하지 않는다.

### completion

이미 COMPLETED인 OrderCancellation에 대해 `canceledQuantity`나 재고를 다시 반영하지 않는다.

## 18. 결과 불명 처리

다음은 즉시 FAILED로 단정하지 않는다.

- timeout
- connection reset
- Toss 5xx
- empty/불완전 응답
- 취소 응답 유실

PARTIAL PaymentCancellation은 REQUESTED/PROCESSING 상태를 유지하고 reconciliation 대상이 된다.

## 19. 부분환불 reconciliation

`PartialPaymentCancellationReconciliationService`가 scheduler로 장기 요청을 조회한다.

### transaction 식별 우선순위

1. 저장된 provider transaction key 정확 매칭
2. key가 없으면 amount + reason + requestedAt 이후 + DONE 조건이 유일할 때만 매칭
3. 여러 후보/충돌이면 unresolved

성공 transaction이 식별되면 기존 completion transaction을 재사용한다.

거래가 아직 없고 provider payment가 PAID/PARTIALLY_CANCELED이며 부분취소 가능 상태라면 저장된 동일 amount/reason/idempotency key로만 안전하게 재시도한다.

## 20. webhook

Toss webhook은 최종 상태를 payload만으로 확정하지 않는다.

```text
webhook 수신
→ payment_webhook_events 중복 제어
→ Toss 단건 조회
→ Payment 승인/전체취소/부분취소 reconciliation 재사용
```

PARTIAL 진행 중 Payment가 있으면 `PartialPaymentCancellationReconciliationService`로 연결한다.

## 21. orphan recovery

판매자 승인 transaction 이후 PaymentCancellation 준비 자체가 내부 예외로 실패할 수 있다.

`PartialPaymentCancellationOrphanRecoveryService`는 별도 transaction에서:

```text
OrderCancellation == PROCESSING
AND 연결 PaymentCancellation 없음
```

인 경우에만 FAILED 처리한다.

이미 PaymentCancellation이 존재하면 자동 실패로 바꾸지 않는다.

## 22. API

### 구매자

```text
POST /api/orders/{orderId}/cancellations
GET  /api/orders/{orderId}/cancellations
```

요청은 sellerOrderId, 취소 사유, client request key, itemId/quantity 목록을 포함하는 구조다.

Backend가 대상 관계와 수량을 최종 검증한다.

### 판매자

```text
GET   /api/seller/orders/cancellations
GET   /api/seller/orders/cancellations/{cancellationId}
PATCH /api/seller/orders/cancellations/{cancellationId}/approve
PATCH /api/seller/orders/cancellations/{cancellationId}/reject
```

## 23. 구매자 UI

주문 상세 SellerOrder 카드에서:

- `availableCancellationQuantity > 0` 상품만 취소 선택 가능
- 여러 상품 선택 가능
- 수량 +/- 선택
- PAID: 즉시취소
- PREPARING: 취소요청
- SHIPPED/DELIVERED/CANCELLED: 취소 액션 없음
- 상태별 취소 이력 표시
- 전량 취소 상품도 주문 history 유지
- 원 결제금액 / 누적 환불액 / 현재 결제잔액 별도 표시

## 24. 판매자 UI

```text
/seller/orders/cancellations
/seller/orders/cancellations/{cancellationId}
```

- 상태 필터
- pagination
- 주문/상품/옵션 snapshot
- 원 주문수량
- 기취소수량
- 이번 요청수량
- REQUESTED 승인/거절
- PROCESSING 자동 복구 대기 안내
- terminal 상태 재처리 버튼 없음

## 25. SHIPPED 이후 경계

SHIPPED/DELIVERED는 `OrderCancellation` 생성/승인 범위가 아니다. 배송 후 클레임은 구현된 별도 도메인이 담당한다.

```text
ReturnRequest
ExchangeRequest
```

반품배송비, 회수, 검수, 재배송, 재입고 여부는 cancellation과 별도 정책으로 설계한다.

## 26. Shipment 경계

부분취소 도메인은 배송 전 PAID/PREPARING 범위이므로 Shipment를 직접 생성하거나 변경하지 않는다.

현재 저장소에는 반품/교환 물류를 포함하는 다음 구조가 구현되어 있다.

```text
SellerOrder 1 : N Shipment
```

최초 배송은 `ORIGINAL_OUTBOUND Shipment`가 source of truth다. SellerOrder legacy 배송 컬럼은 migration/rollback snapshot으로만 유지한다. Return/Exchange는 기존 OrderCancellation을 확장하지 않고 `RETURN_COLLECTION / EXCHANGE_COLLECTION / EXCHANGE_OUTBOUND` Shipment를 각 업무 요청에서 참조한다.

## 27. 데이터 / migration

현재 cancellation 관련 Entity가 요구하는 주요 DB 변경:

- `order_cancellations`
- `order_cancellation_items`
- `order_items.canceled_quantity`
- `order_cancellations.requires_seller_approval`
- `payment_cancellations.type`
- `payment_cancellations.order_cancellation_id`
- PARTIAL 관련 unique/index

저장소에는 개발 DB 수동 검증/DDL을 위한 `docs/sql` 파일들이 존재한다.

운영 전에는 `ddl-auto:update` 의존을 제거하고 Flyway/Liquibase 등 versioned migration을 도입해야 한다.

## 28. 테스트 기준

자동 테스트에서 지속 확인해야 할 항목:

- canceledQuantity 범위
- 동일 item 중복 요청 수량 차단
- PAID 즉시형
- PREPARING 승인/거절
- 승인 vs SHIPPED race
- refund amount 계산
- SellerOrder 전량 취소 배송비
- 부분 재고복원
- completion 멱등성
- Payment refund balance
- PARTIAL PaymentCancellation 생성/unique
- Toss mapping
- 부분환불 정상 completion
- timeout/5xx unresolved
- reconciliation transaction matching
- orphan recovery
- webhook 중복/부분취소 연계
- 기존 FULL 취소 회귀

## 29. 운영 전 필수 검증

실제 공개 staging에서 반드시 확인:

- 상점용 Toss test client/secret key
- HTTPS webhook
- 실제 결제
- 실제 부분취소 1회
- 동일 Payment 부분취소 여러 회
- SellerOrder 일부 상품 취소
- SellerOrder 전량 취소 + 배송비 환불
- 멀티셀러 한 SellerOrder만 취소
- 부분취소 후 `PARTIALLY_CANCELED`
- 최종 잔액 0 후 `CANCELED`
- webhook 중복 재전송
- timeout/5xx/retry/reconciliation
- 재고 1회 복원
- canceledQuantity 1회 증가
- 전체취소와 부분취소 혼합 회귀

## 30. 현재 남은 후속 작업

Cancellation 자체의 핵심 기능은 구현 완료 상태다.

다음 별도 범위:

1. 운영 staging 최종 검증
2. FAILED/장기 PROCESSING 관리자 관측/수동 대응
3. Return/Exchange를 포함한 공개 staging 회귀 검증
4. 관리자 주문/결제 운영 기능

기존 부분취소 코드를 “미구현” 전제로 다시 만들지 않는다.
# 구매확정과 취소 수량

- `OrderItem.confirmedQuantity`는 Buyer 취소 가능 수량에서 제외한다.
- 취소 요청과 구매확정은 동일한 OrderItem pessimistic lock을 사용하여 같은 수량을 동시에 소비하지 않는다.
- 진행 중 취소 요청 수량은 구매확정 가능 수량에서도 제외한다.
