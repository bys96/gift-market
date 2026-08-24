# Gift Market Payment Architecture

> 최종 갱신: 2026-08-24
>
> 이 문서는 현재 실제 Payment 구현을 기준으로 정리한 아키텍처 문서다. 과거 단계별 TODO보다 현재 코드가 우선한다.

## 1. 목표

Gift Market의 결제는 실제 운영을 고려해 다음을 보장한다.

- 주문 금액의 최종 권위는 Backend
- PG provider 교체 가능 경계 유지
- 중복 승인/중복 취소 방지
- 결제 결과 불명 상태 안전 처리
- 예약 재고 정확히 한 번 복원
- 전체취소와 부분취소 동시 지원
- webhook payload 맹신 금지
- 멀티셀러라도 Payment는 Order 단위 유지

## 2. 현재 구조

```text
Order
├─ Payment 1..N 확장 가능
│  ├─ PaymentCancellation N
│  └─ PaymentWebhookEvent N(논리적 연계)
│
└─ SellerOrder N
   ├─ OrderItem N
   └─ Shipment N
```

현재 일반 주문 흐름에서는 최신 Payment 한 건을 중심으로 처리하지만 Entity 관계는 결제 재시도/확장을 고려한다.

## 3. Payment 상태

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

의미:

- READY: 주문/재고 예약 후 PG 승인 전
- CONFIRMING: 승인 결과 불명 가능 구간
- PAID: 전액 승인, 환불 없음
- PARTIALLY_CANCELED: 일부 금액 환불 후 잔액 존재
- FAILED: 명확한 결제 실패
- EXPIRED: READY 유효시간 만료
- CANCELING: FULL 취소 결과 불명 가능 구간
- CANCELED: 잔액 0, 전액 취소됨

## 4. Order 상태와 역할 분리

Order는 전체 주문/결제 상태를 표현한다.

SellerOrder는 판매자 주문 처리 상태를 표현하고 Shipment는 실제 배송 실행/송장 이력을 표현한다. 최초 배송 정보의 source of truth는 `ORIGINAL_OUTBOUND Shipment`다.

부분취소 후 일부 상품이 남아 있으면:

```text
Order = PAID
Payment = PARTIALLY_CANCELED
SellerOrder = 기존 배송 상태 유지
```

모든 Payment 잔액이 0이라고 해서 멀티셀러 배송 상태를 Payment 하나로 추측하지 않는다. 취소 업무는 OrderCancellation/SellerOrder와 함께 처리한다.

## 5. 결제 준비

```text
사용자/CartItem/상품/Variant/배송지 검증
→ Product/Variant pessimistic lock
→ Backend 가격/배송비 계산
→ 재고 예약 차감
→ Order PENDING_PAYMENT
→ SellerOrder PENDING_PAYMENT
→ OrderItem snapshot
→ Payment READY
→ commit
```

### 멱등성

`clientOrderRequestKey`를 사용한다.

동일 key 재요청:

- 기존 Order/Payment 준비 결과 반환
- SellerOrder 중복 생성 없음
- 재고 추가 차감 없음

Frontend sessionStorage는 비민감 복구 정보만 저장한다.

## 6. 결제 승인 transaction

### Transaction A

```text
Payment/Order lock
→ ownership/merchantPaymentId/amount/expiry 검증
→ READY → CONFIRMING
→ commit
```

### PG 호출

```text
PaymentGateway.confirm(...)
→ TossPaymentGateway
→ TossPaymentClient
```

외부 PG 호출은 DB transaction 밖에서 수행한다.

### Transaction B

```text
provider 결과 재검증
→ Payment PAID
→ Order PAID
→ orderedAt 기록
→ SellerOrder PAID
→ 안전한 CartItem 후처리
→ commit
```

## 7. Provider-neutral Gateway

```text
PaymentService
→ PaymentGatewayRegistry
→ PaymentGateway
→ TossPaymentGateway
→ TossPaymentClient / TossPaymentMapper
```

Order/Payment domain service는 Toss 전용 DTO를 직접 다루지 않는다.

Gateway는 승인, 단건 조회, 취소를 provider-neutral command/result로 노출한다.

현재 취소 command는 FULL/PARTIAL을 모두 지원한다.

```text
FULL    → cancelAmount = null
PARTIAL → cancelAmount > 0
```

## 8. Toss 연동 원칙

- Client Key: Frontend 공개 환경변수
- Secret Key: Backend Secret 환경변수
- Basic auth: secret + `:`
- confirm/cancel에 고정 DB idempotency key 사용
- connect/read timeout 명시
- Toss 원본 JSON 전체를 DB에 저장하지 않음
- 카드번호/CVC/token/secret 저장 금지

현재 결제위젯은 Toss Payments v2 `widgets()` 기반 구조를 유지한다.

개발 문서 예제용 test key와 실제 상점용 test key는 구분한다.

## 9. CONFIRMING 결과 불명

다음은 명확한 실패가 아니다.

- timeout
- connection reset
- Toss 5xx
- 응답 유실

이때 Payment를 FAILED로 바꾸거나 재고를 복원하지 않는다.

```text
Payment = CONFIRMING
Order = PENDING_PAYMENT
```

사용자 polling / scheduler / webhook에서 Toss 단건 조회로 복구한다.

DONE 확인 시 동일 완료 transaction을 재사용해 PAID로 확정한다.

## 10. READY 만료

`PaymentExpirationService` scheduler가 만료 후보를 제한 조회한다.

```text
Payment READY + expiresAt 경과
→ Payment EXPIRED
→ Order PAYMENT_EXPIRED
→ SellerOrder CANCELLED
→ 예약 재고 복원
```

transaction 안에서 상태를 재검증해 중복 복원을 방지한다.

## 11. 결제 reconciliation

`PaymentReconciliationService`가 장기 CONFIRMING을 조회한다.

- provider DONE → PAID 복구
- 명확한 provider 실패 → 실패 확정 가능
- 결과 불명 → 상태 유지

PG 조회를 통해서만 상태를 추론하고 로컬 시간 경과만으로 승인/실패를 확정하지 않는다.

## 12. Webhook

Endpoint:

```text
POST /api/payments/webhooks/toss
```

Security에서 이 path만 permitAll이다.

처리 원칙:

```text
webhook 수신
→ transmission id 기반 PaymentWebhookEvent 확보
→ 대상 payment 식별
→ Toss 단건 조회
→ 기존 상태전이/reconciliation 재사용
→ event terminal 상태 기록
```

`PaymentWebhookStatus`:

```text
PROCESSING
PROCESSED
IGNORED
REJECTED
RETRYABLE_FAILED
```

중복 전송은 DB unique/상태로 제어한다.

Webhook payload의 status를 그대로 Payment에 복사하지 않는다.

## 13. FULL 전체취소

### 도메인

`PaymentCancellationType.FULL`

기존 전체취소 API:

```text
PATCH /api/orders/{orderId}/cancel
```

### 정상 흐름

```text
Tx A
Payment/Order lock
→ PaymentCancellation(FULL) REQUESTED
→ Payment CANCELING
→ commit

PG
→ Toss 전체취소

Tx B
→ 취소 응답/잔액 검증
→ PaymentCancellation SUCCEEDED
→ Payment CANCELED
→ Order CANCELLED
→ SellerOrder CANCELLED
→ 전체 재고 복원
→ commit
```

CartItem은 자동 복구하지 않는다.

## 14. CANCELING reconciliation

전체취소 timeout/5xx/응답 유실 시 Payment는 CANCELING을 유지한다.

`PaymentCancellationReconciliationService`가 scheduler로 처리한다.

- provider 전체 CANCELED 확인 → 완료 transaction
- DONE이고 아직 취소 transaction이 없는 안전한 상태 → 저장된 동일 idempotency key로 재시도
- 모호한 상태 → 유지

새 retry마다 새 취소 요청/새 idempotency key를 만들지 않는다.

## 15. PARTIAL 부분취소

### 도메인 분리

```text
OrderCancellation
= 어떤 상품/수량을 취소하는지

PaymentCancellation(PARTIAL)
= 실제 PG에서 얼마를 취소했는지
```

PaymentCancellation에 판매자 승인 workflow를 넣지 않는다.

현재 PARTIAL의 업무 원인은 nullable FK로 구분한다.

```text
OrderCancellation PARTIAL: orderCancellation != null, returnRequest == null
ReturnRequest PARTIAL:      orderCancellation == null, returnRequest != null
```

`FULL / PARTIAL`은 결제 취소 금액 범위이고 `OrderCancellation / ReturnRequest`는 업무 원인이다. Return 전용 PaymentCancellationType은 사용하지 않으며 각 업무 요청에는 PaymentCancellation이 최대 한 건만 연결된다.

### 상태

부분환불 성공 후 잔액 존재:

```text
Payment = PARTIALLY_CANCELED
Order = PAID
```

잔액 0:

```text
Payment = CANCELED
```

## 16. 부분환불 금액 권위

Frontend가 refundAmount를 결정하지 않는다.

`OrderCancellationRefundCalculator`:

```text
상품환불액
= Σ(OrderItem.unitPrice × requestedQuantity)
```

배송비:

```text
SellerOrder에 배송할 수량 남음 → 0
SellerOrder 전량 취소 → Σ(OrderItem.shippingFee)
```

`Math.multiplyExact` / `Math.addExact`로 overflow를 방어한다.

## 17. Payment 환불 잔액

`PaymentRefundBalanceService`:

```text
available
= payment.amount
- succeededRefundAmount
- reservedRefundAmount
```

- SUCCEEDED: 이미 확정된 환불
- REQUESTED: 진행 중이므로 예약된 환불액

새 PARTIAL 요청이 available을 넘으면 시작하지 않는다.

## 18. PARTIAL 준비

`PartialPaymentCancellationPreparationService`가 Payment lock 기반으로:

- 기존 연결 PaymentCancellation 멱등 확인
- refund amount 동일성 확인
- 현재 refund balance 확인
- PARTIAL PaymentCancellation 생성
- 고정 PG idempotency key 확보

동일 OrderCancellation에 PG transaction을 중복 생성하지 않는다.

## 19. Toss 부분취소

현재 Gateway/Toss adapter는 다음을 지원한다.

- `cancelAmount`
- `cancelReason`
- `Idempotency-Key`
- `isPartialCancelable`
- `balanceAmount`
- `cancels[]`
- cancellation `transactionKey`
- `PARTIAL_CANCELED`

부분취소 전에 Toss 최신 payment를 조회해 다음을 검증한다.

- providerPaymentKey
- merchantPaymentId
- original amount
- currency
- 현재 status
- partialCancelable

## 20. PARTIAL 정상 transaction 경계

```text
Tx A: 환불액 재계산 + PaymentCancellation 준비
→ commit

PG query/cancel

Tx B: PG transaction/잔액 검증
→ PaymentCancellation SUCCEEDED
→ Payment PARTIALLY_CANCELED/CANCELED
→ OrderCancellation completion
→ canceledQuantity 증가
→ 부분 재고복원
→ 필요 시 SellerOrder CANCELLED
→ commit
```

외부 PG 호출을 transaction 내부에 오래 유지하지 않는다.

## 21. PARTIAL 결과 불명

명확한 provider 4xx/부분취소 불가 응답은 실패로 종료할 수 있다.

다음은 unresolved로 유지한다.

- timeout
- connection reset
- 5xx
- empty response
- cancellation transaction 식별 불가

PaymentCancellation/OrderCancellation 상태만 보고 PG 성공을 추측하지 않는다.

## 22. PARTIAL reconciliation

`PartialPaymentCancellationReconciliationService` scheduler가 PARTIAL 후보를 조회한다.

provider transaction 식별:

1. 저장된 transactionKey 정확 매칭
2. 없으면 amount/reason/requestedAt/DONE 조건 유일 매칭
3. conflict/ambiguous → unresolved

성공 transaction이 있으면 기존 Transaction B를 재사용한다.

없고 현재 provider 상태가 PAID/PARTIALLY_CANCELED이며 부분취소 가능하면 저장된 동일 idempotency key로 안전한 재시도를 허용한다.

## 23. PARTIAL webhook

Toss webhook에서 PARTIAL 진행 중 payment가 감지되면 `PartialPaymentCancellationReconciliationService`로 전달한다.

ReturnRequest에 연결된 진행 중 PARTIAL은 `ReturnPaymentCancellationReconciliationService`로 분기한다. webhook payload만으로 성공을 확정하지 않고 동일한 provider 단건 조회 결과를 각 업무 reconciliation에 전달한다.

scheduler와 webhook이 별도 완료 코드를 만들지 않고 같은 reconciliation/completion 로직을 사용한다.

## 24. 부분 재고복원

전체취소:

```text
OrderInventoryService.restore(orderId)
```

부분취소:

```text
OrderInventoryService.restoreCancellationItems(cancellationItems)
```

PARTIAL은 정확한 요청 수량만 복원한다.

PG 성공 확정 전에는 복원하지 않는다.

## 25. SellerOrder와 Payment의 독립성

부분취소 하나 때문에 Payment를 판매자별로 분리하지 않는다.

예:

```text
Payment original = 70,000
Seller A = 33,000 전량 취소
Seller B = 37,000 배송 진행

Payment remaining = 37,000
Payment status = PARTIALLY_CANCELED
Seller A = CANCELLED
Seller B = 배송 상태 유지
Order = PAID
```

## 26. Lock / race condition 원칙

돈/재고/배송 상태가 충돌하는 경로는 row lock과 terminal 상태 재검증을 사용한다.

특히:

- confirm vs expiration
- full cancel vs seller shipping
- partial approve vs shipping
- full cancel vs partial cancel
- webhook vs scheduler
- scheduler vs 사용자 HTTP

외부 PG HTTP 전에 필요한 DB 의도를 commit하고, 완료 transaction에서 다시 상태를 검증한다.

## 27. 보안

- TOSS_SECRET_KEY 문서/코드/로그 금지
- webhook path 외 `/api/payments/**` authenticated
- 사용자 Payment API ownership 검증
- PG raw sensitive payload 저장 금지
- provider error message를 사용자에게 그대로 노출하지 않음

## 28. Frontend 결제

주요 구조:

```text
giftmarket-web/components/payment/TossPaymentWidget.tsx
giftmarket-web/lib/toss-payment.ts
giftmarket-web/lib/payment-api.ts
giftmarket-web/lib/payment-session.ts
giftmarket-web/app/payment/success/page.tsx
giftmarket-web/app/payment/fail/page.tsx
```

- Toss v2 Payment Widget
- Backend prepare amount를 widget amount와 동기화
- success에서 Backend confirm
- CONFIRMING polling
- fail에서 안전한 사용자 문구
- 재진입용 비민감 session 정보만 저장

## 29. 현재 구현 파일군

### Entity

- `Payment.java`
- `PaymentCancellation.java`
- `PaymentWebhookEvent.java`
- `PaymentStatus.java`
- `PaymentCancellationStatus.java`
- `PaymentCancellationType.java`

### Gateway / Toss

- `PaymentGateway.java`
- `GatewayCancelCommand.java`
- `GatewayCancelResult.java`
- `GatewayPaymentQueryResult.java`
- `GatewayCancellationTransaction.java`
- `TossPaymentGateway.java`
- `TossPaymentClient.java`
- `TossPaymentMapper.java`

### Service

- `PaymentService.java`
- `PaymentTransactionService.java`
- `PaymentExpirationService.java`
- `PaymentReconciliationService.java`
- `PaymentCancellationService.java`
- `PaymentCancellationTransactionService.java`
- `PaymentCancellationReconciliationService.java`
- `PaymentWebhookEventService.java`
- `TossPaymentWebhookService.java`
- `PaymentRefundBalanceService.java`
- `OrderCancellationRefundExecutionService.java`
- `PartialPaymentCancellationPreparationService.java`
- `PartialPaymentCancellationTransactionService.java`
- `PartialPaymentCancellationReconciliationService.java`
- `PartialPaymentCancellationOrphanRecoveryService.java`
- `ReturnRefundExecutionService.java`
- `ReturnPaymentCancellationTransactionService.java`
- `ReturnPaymentCancellationReconciliationService.java`

## 30. 운영 전 최종 통합 검증

아래는 실제 staging에서 완료 전까지 TODO다.

- 공개 HTTPS staging
- 상점용 Toss test key
- 실제 결제
- PAYMENT_STATUS_CHANGED webhook
- webhook HTTP 200
- `payment_webhook_events` PROCESSED
- 중복 webhook 멱등성
- READY 만료
- CONFIRMING 결과 불명 복구
- FULL 전체취소
- FULL CANCELING reconciliation
- PARTIAL 1회
- PARTIAL 여러 회
- PARTIAL webhook
- PARTIAL timeout/5xx reconciliation
- 멀티셀러 SellerOrder 단독 취소
- Payment PARTIALLY_CANCELED / CANCELED 잔액 검증
- 재고 1회 복원
- Cart 정합성
- 운영키 전환 전 전체 회귀

## 31. 남은 운영 과제

- Flyway/Liquibase 등 versioned migration
- 관리자 결제/취소 관측 UI
- 장기 CONFIRMING/CANCELING/PARTIAL PROCESSING 경보
- 실패 환불 운영 runbook
- 실제 상점 계약 결제수단별 부분취소 가능 여부 검증
- 운영 secret manager

### Exchange 배송비 추가결제 (설계 확정, 미구현)

교환은 환불이 없으므로 구매자 귀책 교환 배송비를 원 주문 `Payment`에서 차감하거나 기존 `PaymentCancellation`로 처리하지 않는다. `ExchangeRequest`와 1:1인 별도 `ExchangeShippingPayment`를 구현한다.

최소 필드는 `amount`, `status`, `merchantPaymentId`, `providerPaymentKey`, `requestedAt`, `paidAt`, `failedAt`과 만료 판단용 `expiresAt` 또는 동등한 정책 시각이며 고정 idempotency key, 결과 불명 상태와 reconciliation을 고려한다.

BUYER 귀책은 판매자 승인 transaction에서 target Product/Variant를 재검증·pessimistic lock하고 실제 stock 차감과 reservation bookkeeping을 완료한 뒤 `PAYMENT_PENDING`으로 전이한다. 즉 교환배송비는 target reservation 완료 후 결제한다. 24시간 이내 추가결제가 성공하면 reservation을 유지한 채 `COLLECTING`으로 진행한다.

24시간 미결제 만료는 `FAILED`가 아니라 `CANCELED`이며, 같은 업무 결과로 reservation을 release하고 실제 stock과 `releasedQuantity`를 정확히 한 번 복원한다. 단순 결제 실패 한 번을 ExchangeRequest 즉시 `FAILED`로 확정하지 않으며 재시도 가능/결과 불명 상태는 후속 `ExchangeShippingPayment` 상태 머신에서 정한다. SELLER 귀책은 추가결제 없이 승인 transaction의 target reservation 후 `COLLECTING`으로 진행한다. `ExchangeShippingPayment`는 아직 미구현이며 기존 `PaymentCancellation`을 재사용하지 않는다.

## 32. 변경 시 지켜야 할 회귀 기준

Payment 변경에서 다음을 동시에 보호한다.

- 주문 prepare 멱등성
- 재고 예약 1회
- confirm 멱등성
- CartItem 안전 삭제
- 바로구매 Cart 불변
- READY expiration
- CONFIRMING reconciliation
- webhook 중복 처리
- FULL cancel
- FULL cancel reconciliation
- PARTIAL cancel
- PARTIAL reconciliation
- 부분 재고복원
- SellerOrder 배송 lifecycle

부분취소를 이유로 기존 FULL 취소 코드를 전면 재작성하지 않는다.
