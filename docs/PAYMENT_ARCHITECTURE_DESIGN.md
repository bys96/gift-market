# Gift Market Payment Architecture

## Exchange shipping payment

구매자 귀책 교환배송비는 주문 `Payment` 및 환불용 `PaymentCancellation`과 분리된 `ExchangeShippingPayment`로 관리한다. ExchangeRequest당 row와 amount snapshot은 하나다. 동일 REQUESTED 결과불명 재호출은 같은 Toss order id/idempotency key를 사용한다. 명시 실패 뒤 새 사용자 결제 시도는 Toss의 15일 멱등 응답 재사용을 피하도록 같은 row에서 `attemptSequence`를 증가시키고 attempt별 order id/idempotency key를 사용한다.

PG 호출은 transaction 밖에서 수행한다. 명시 거절만 FAILED로 기록하고 Exchange는 PAYMENT_PENDING을 유지한다. 네트워크/5xx/빈 응답은 REQUESTED와 reservation을 유지하며 paymentKey 조회 reconciliation 대상으로 둔다. 예외적으로 REQUESTED에 paymentKey가 없으면 Toss orderId 조회로 복구하며 404/불명은 REQUESTED를 유지한다. 만료 처리도 reconciliation이 성공/불명확을 배제하기 전에는 release하지 않는다. 만료 뒤 늦은 성공은 `COMPENSATION_REQUIRED`로 기록하고 Exchange를 되살리지 않는다.

> 소스 대조: 2026-11-09. 아래 결제 승인·전체/부분 취소·환불·교환배송비 workflow는 구현 완료 범위다. 실제 Toss 계약/결제수단·webhook·장애 복구 E2E는 확인 필요이며 과거 실행 기록과 구분한다. [개발 현황](./DEVELOPMENT_STATUS.md)을 기준으로 한다.
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

### 신규 거래 금액 검증과 기존 snapshot (2026-11-10)

금액 컬럼은 기존 `Long/BIGINT`를 유지하고 원 단위 정수로 처리한다. 신규 상품 등록·수정과 신규 주문에는 `MoneyLimits.MAX_AMOUNT = 10,000,000원`을 적용한다. 이는 Gift Market의 서비스 상한이며 PG 공통 승인 한도가 아니다. [Toss 결제수단 정책](https://docs.tosspayments.com/guides/v2/get-started/payment-methods)은 계좌이체 1일 1,000만원 등 수단별 조건을 안내하고, [코어 API](https://docs.tosspayments.com/reference)는 주문 금액과 승인 요청 금액의 일치를 요구한다. 실제 가맹점 계약과 수단별 사용 가능 한도는 별도 운영 확인 대상이다.

| 대상 | 적용 범위 |
| --- | --- |
| 신규 상품 기본가·옵션 적용 단가 | 1원~1,000만원 |
| 신규 배송비·반품 배송비·교환 배송비 | 0원~1,000만원; 기존 무료배송·기본값 유지 |
| 옵션 추가금 | -1,000만원~1,000만원; 최종 단가는 반드시 1원~1,000만원 |
| 신규 주문 합계 | 상품 수량별 금액 + 기존 CartItem별 배송비 합계, 최대 1,000만원 |
| 기존 주문 환불·정산 | 신규 상한을 소급하지 않고 원 snapshot·환불 잔액·최초 수수료율 기준 유지 |
| 결제 승인 요청 amount | 양의 JS 안전 정수 이하; 기존 준비 결제를 위해 신규 주문 상한을 DTO에 소급하지 않음 |

입력 DTO의 Bean Validation과 상품·SKU Service 검증을 함께 사용하며 Backend가 최종 기준이다. 상품 기본정보만 수정할 때는 기존 활성 SKU의 새 최종 단가도 검사한다. 상품·SKU 통합 수정은 기존 SKU가 잠시 불일치할 수 있으므로 같은 transaction의 교체 SKU 검증으로 최종값을 확인한다. 불완전한 임시저장 JSON은 실제 상품 등록·수정 시 다시 검증한다.

`MoneyMath`는 단가 합산·수량 곱셈·장바구니/주문 합산에 exact 연산을 사용하고 오버플로·음수/0 단가를 400 오류로 거부한다. 신규 주문은 모든 금액과 합산 수량을 검증한 뒤 재고를 예약한다. 기존 Product → Variant 잠금 순서와 CartItem별 배송비 정책은 유지한다. JSON의 소수 값을 Long/Integer로 절삭하지 않으며 잘못된 요청 본문도 공통 ApiResponse의 400으로 반환한다.

정수 요청 필드는 `1000.0` 대신 `1000` 형태의 JSON을 사용한다. 프론트 입력은 Number 변환 전에 정수 문자열인지 검사하여 작은 소수부가 정수로 반올림되는 것도 거부한다. 금액 입력을 자리 수로 잘라 다른 유효 금액으로 바꾸지 않는다.

독립 쿠폰·포인트 할인 계산은 현재 구현되어 있지 않다. 음수 옵션 추가금 정책을 보존하며 별도 할인 정책을 새로 만들지 않는다. 취소·반품·정산 계산은 이미 사용 중인 exact 연산과 수수료 원 미만 절사·누적 환입 계산을 유지한다. 정산 원장의 음수 환불과 양수 수수료 환입에는 상품 입력의 비음수 제약을 적용하지 않는다.

Frontend는 동일 상한과 안전 정수 검증을 사용하고 범위 밖 주문은 금액/결제 위젯을 표시하지 않고 재확인을 안내한다. 기존 주문·원장을 수정하거나 금액을 clamp하는 migration은 없다. 기존 범위 초과 상품은 새 주문 전에 가격·배송비 검토가 필요하다. 이미 생성된 준비 주문·이력과 JS 안전 정수를 넘는 과거 값의 실제 분포는 운영 DB에서 별도 대사해야 하며 이번 작업에서 조회·수정하지 않았다. 기존 승인 결제의 환불·정산은 신규 상한 대신 원 snapshot을 따른다.

## 2. 현재 구조

```text
Order
├─ Payment 1..N 확장 가능
│  ├─ PaymentCancellation N
│  └─ PaymentWebhookEvent N(논리적 연계)
│
└─ SellerOrder N
   ├─ OrderItem N
   ├─ Shipment N
   └─ ExchangeRequest N
      └─ ExchangeShippingPayment 0..1
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

`OrderService.findExistingPreparation`은 같은 요청 키·사용자의 PENDING_PAYMENT/READY 결과를 반환한다. Frontend 주문 페이지는 반환된 주문 상세 snapshot을 현재 상품·수량·금액과 비교하고 불일치 시 기존 취소 API로 준비 주문을 취소한 뒤 재시도를 안내한다. 배송정보는 `PATCH /api/payments/{paymentId}/preparation` → `PaymentTransactionService.updateReadyPreparation`에서 사용자·상태·만료를 검증하고 갱신한다. 이 비교/재시도 흐름 전체가 OrderService 하나에 구현된 것은 아니다. 최초 주문의 금액·재고·소유권은 서버 계산을 최종값으로 한다. 일반 주문의 수령인 snapshot은 선물 수락/거절 또는 수신자 주소 입력 링크 기능이 아니다.

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

### 복구 배치 순환 조회와 동시 실행 (2026-11-10)

승인·전체취소·부분취소·반품 환불·교환 배송비 복구는 `ReconciliationBatchExecutor`를 공통 사용한다. `afterId < id <= throughId`와 ID 오름차순으로 조회하며, 전체취소도 PaymentCancellation ID로 순회하고 함께 조회한 Payment ID로 기존 복구를 호출한다. 최초 지연 시간과 상태·관계 조건은 유지한다.

- 순회 시작 시 테이블 최대 ID를 상한으로 고정한다. 더 큰 신규 ID는 다음 순회에서 처리하여 신규 유입이 기존 미해결 요청의 재방문을 무한정 미루지 않게 한다.
- 미해결·타임아웃·개별 오류도 커서를 전진시킨다. 끝에 도달하거나 조회 건수가 배치 크기보다 작으면 다음 실행에서 새 순회를 시작한다. 배치가 가득 찼지만 뒤에 대상이 없으면 빈 조회 한 번으로 순회를 종료한다.
- 실행당 최대 한 배치만 조회·시도한다. 증가하는 OFFSET, 전체 후보 적재, 같은 실행에서 끝까지 반복 조회는 없다. 결과는 최대 batch-size이며 새 순회에서만 최대 ID 조회가 추가된다. 실제 SQL이 읽는 행 수까지 상수라는 보장은 아니므로 backlog 증가 시 기존 인덱스와 실행 계획을 측정한다.
- 영구 `nextRetryAt`에는 새 컬럼·인덱스가 필요하다. 이번에는 audit/PG 요청 시각을 바꾸지 않고 기존 설정 주기의 순환 재시도를 선택했다. 요청별 지수 backoff·실패 횟수·영구 체크포인트는 추가하지 않는다.

`ReconciliationBatchLock`은 JVM 내부 중복 진입과 동일 MySQL 서버의 동일 종류 배치 동시 실행을 차단한다. `GET_LOCK(..., 0)`으로 기다리지 않고 획득한 실행만 조회·PG 처리를 수행하고 같은 JDBC 세션에서 `RELEASE_LOCK`한다. 업무 transaction과 별개의 세션 잠금이므로 PG 호출 중 Payment/Order 행 잠금을 유지하지 않는다. 기존 개별 복구의 잠금 순서·멱등성 키·완료 transaction은 유지한다. webhook·사용자 요청과의 경합은 기존 정합성 검증이 담당한다.

배치 동안 풀 연결 하나를 사용하므로 업무 transaction용 연결 여유도 필요하다. 병렬 배치 수보다 충분한 풀 여유를 유지한다. 획득 결과가 불명확하거나 해제에 실패하면 연결을 abort하여 잠금이 남은 세션을 풀에 반환하지 않는다. MySQL 이외 환경에서는 이 배치를 실행하지 않는다. H2 단일 거래 테스트와 실제 MySQL 세션 잠금 검증은 구분한다.

커서는 인스턴스 메모리에만 있고 업무 대상은 DB에 남는다. 재시작 시 0부터 재순회하며 완료 거래는 기존 검증으로 중복 반영하지 않는다. 여러 인스턴스의 커서는 독립적이므로 순차 재방문까지 없애는 영구 exactly-once 체크포인트는 아니다. 한 순회 전에 재시작을 계속 반복하면 뒤쪽 처리가 지연될 수 있다. 정상 실행 중에는 한 순회의 뒤쪽 대상까지 도달함을 검증한다.

PG 조회 실패는 UNCERTAIN, 식별 불가·모호한 거래는 UNRESOLVED, 멱등성 유효기간 경과는 기존 retry-skipped 로그로 구분하고 자동 성공/실패로 바꾸지 않는다. 응답 유실 뒤 확실한 성공은 기존 완료 경로로 복구한다. 운영자는 거래 ID·대기 시간과 `Reconciliation batch scanned`의 종류·커서·상한·조회 건수를 확인해 대사한다. 세션 장애 후에도 경제 이벤트 중복 방지는 PG 멱등성과 DB 완료 검증에 의존한다.

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
- 결제창을 닫은 뒤 배송정보만 바뀌면 READY Payment와 PENDING_PAYMENT Order를 잠그고 배송 snapshot을 갱신한 후 기존 Payment를 재사용
- 상품·Variant·수량·금액 checkout snapshot이 달라지면 stale PENDING_PAYMENT Order와 READY Payment를 취소하고 예약 재고를 복원한 뒤 새 주문을 준비
- success callback에서 `sessionStorage`는 보조 정보로만 사용하고, 유실되어도 callback의 `merchantPaymentId`와 현재 인증 사용자로 서버 Payment를 조회
- 서버 Payment가 READY일 때 callback 금액을 검증한 뒤 confirm하고, CONFIRMING이면 중복 confirm 없이 polling/reconciliation을 기다림
- confirm network error·5xx는 즉시 결제 실패로 확정하지 않고 서버 상태를 다시 조회하며, READY confirm 재시도는 최대 1회로 제한
- 1.5초 간격 최대 10회 polling 중 PAID가 확인되면 주문 상세로 자동 이동
- polling timeout은 실패가 아니라 결과 확인 지연으로 안내하고 이때만 주문내역 이동 버튼을 제공
- FAILED, EXPIRED, CANCELED는 결과 확인 지연과 구분해 명확한 실패로 종료
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
- `ExchangeShippingPayment.java`
- `ExchangeShippingPaymentStatus.java`

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
- `ExchangeShippingPaymentService.java`
- `ExchangeShippingPaymentTransactionService.java`
- `ExchangeShippingPaymentReconciliationService.java`
- `ExchangePaymentExpirationService.java`
- `ExchangePaymentExpirationTransactionService.java`

## 30. 운영 통합 회귀 체크리스트

기존 AWS 이전 기록에는 Production HTTPS, Toss 결제 연결과 webhook 확인이 남아 있다. 이번 문서 작업에서 재확인하지 않았으므로 현재 상태는 확인 필요다. 아래는 배포·결제 설정 변경 시 다시 확인할 회귀 체크리스트이며 timeout/5xx 등 장애 주입 항목은 계속 운영 검증 대상으로 둔다.

- 공개 HTTPS 환경
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
- 결제 설정 변경 전 전체 회귀

## 31. 결제 확장 및 남은 운영 과제

- Flyway/Liquibase 등 versioned migration
- 관리자 결제/취소 관측 UI
- 장기 CONFIRMING/CANCELING/PARTIAL PROCESSING 경보
- 실패 환불 운영 runbook
- 실제 상점 계약 결제수단별 부분취소 가능 여부 검증
- 운영 secret manager

### Exchange 배송비 추가결제 (구현 완료)

교환은 환불이 없으므로 구매자 귀책 교환 배송비를 원 주문 `Payment`에서 차감하거나 기존 `PaymentCancellation`로 처리하지 않는다. `ExchangeRequest`와 1:1인 별도 `ExchangeShippingPayment` aggregate로 처리한다.

현재 주요 필드는 `amount`, `status`, `providerOrderId`, `providerPaymentKey`, `idempotencyKey`, `attemptSequence`, `requestedAt`, `succeededAt`, `failedAt`, `expiredAt`이다. 결제기한은 ExchangeRequest의 `paymentDueAt` snapshot을 사용한다.

BUYER 귀책은 판매자 승인 transaction에서 target Product/Variant를 재검증·pessimistic lock하고 실제 stock 차감과 reservation bookkeeping을 완료한 뒤 `PAYMENT_PENDING`으로 전이한다. 즉 교환배송비는 target reservation 완료 후 결제한다. 24시간 이내 추가결제가 성공하면 reservation을 유지한 채 `COLLECTING`으로 진행한다.

24시간 미결제 만료는 ExchangeRequest `CANCELED`와 결제 `EXPIRED`이며, 같은 업무 결과로 reservation을 release하고 실제 stock과 `releasedQuantity`를 정확히 한 번 복원한다. 단순 결제 실패 한 번은 ExchangeRequest를 `FAILED`로 만들지 않는다. 결제는 `FAILED`로 기록하고 같은 aggregate row에서 `attemptSequence`를 증가시켜 새 provider order id/idempotency key로 다시 시도한다.

`REQUESTED`는 PG 성공 여부가 불명확할 수 있는 상태다. 같은 attempt의 식별자를 유지하고 paymentKey 조회를 우선하며, paymentKey가 없으면 provider orderId 조회로 reconciliation한다. 만료 처리도 성공 또는 결과 불명 가능성을 배제하기 전에는 reservation을 release하지 않는다. 만료 후 늦은 성공은 Exchange를 되살리지 않고 결제를 `COMPENSATION_REQUIRED`로 기록한다. 0원은 PG 호출 없이 추적 가능한 `SUCCEEDED` row로 남긴다. SELLER 귀책은 결제를 만들지 않고 승인 transaction의 target reservation 후 `COLLECTING`으로 진행한다.

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


## 33. 과거 회귀 기록

- 2026-08-28 당시 작업 보고 기준 Backend 전체 suite는 **511/511 성공**이었다.
- 같은 시점 Frontend `npm run lint`, `npx tsc --noEmit`, `npm run build`가 성공했다. 현재 결과나 페이지 수는 실행 시점에 다시 확인한다.
- 이 문서의 Payment/PG 구조에는 2026-08-28 Seller/Pagination 마감 작업으로 인한 API/DB 변경이 없다.
