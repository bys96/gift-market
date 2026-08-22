# Gift Market 개발 트러블슈팅 기록

> 최종 갱신: 2026-08-22
>
> 실제 코드, 기존 설계 문서와 git 이력에서 확인되는 문제와 해결 구조를 보존한다. 최종 운영 runbook이 아니며 운영 staging 검증 전 항목을 완료로 간주하지 않는다.

# Payment / PG

## Toss 부분취소 결과 불명

### 상황 / 증상

부분환불 요청 중 timeout, connection reset, 5xx, 빈 응답 또는 응답 유실이 발생하면 호출자는 성공 여부를 알 수 없다.

### 원인 또는 위험

HTTP 실패가 실제 PG 취소 실패를 뜻하지 않는다. 즉시 FAILED로 확정하고 새 요청을 보내면 이미 성공한 거래를 중복 환불할 수 있다.

### 해결

명시적인 provider 거절만 FAILED로 처리한다. 결과 불명은 PaymentCancellation REQUESTED와 업무 요청의 진행 상태를 유지하고 provider 결제·취소 거래를 다시 조회한다.

### 현재 적용 구조

- 전체취소: CANCELING reconciliation
- OrderCancellation 부분환불: `PartialPaymentCancellationReconciliationService`
- Return 부분환불: `ReturnPaymentCancellationReconciliationService`
- 저장된 transaction key를 우선하고, 없으면 amount/reason/requestedAt 이후/DONE 후보가 유일할 때만 성공 확정
- 후보 충돌이나 복수 후보는 unresolved 유지
- 거래가 없고 provider 상태가 안전할 때만 같은 요청으로 재시도

### 관련 코드 / 문서

- `payment/service/PaymentCancellationReconciliationService`
- `payment/service/PartialPaymentCancellationReconciliationService`
- `payment/service/ReturnPaymentCancellationReconciliationService`
- `docs/PAYMENT_ARCHITECTURE_DESIGN.md`

## PG idempotency key 고정

### 상황 / 증상

결과 불명 요청을 재시도할 때마다 새 key를 만들면 PG가 서로 다른 취소 요청으로 인식할 수 있다.

### 해결 / 현재 적용 구조

PaymentCancellation에 idempotency key를 먼저 저장하고 외부 호출 전 DB transaction을 commit한다. OrderCancellation은 해당 PaymentCancellation에 저장된 key를 재사용하고, Return은 `RETURN-REFUND-{returnRequestId}` 형태의 고정 key를 사용한다. reconciliation도 저장된 amount/reason/key만 사용한다.

## PG 성공 후 내부 후처리 실패

### 상황 / 증상

PG 환불은 성공했지만 네트워크 응답 유실이나 내부 DB 후처리 실패로 commerce 상태가 완료되지 않을 수 있다.

### 해결

PG 호출과 내부 성공 확정을 transaction으로 분리하고 provider 거래를 재조회해 성공을 복구한다. Return은 PaymentCancellation SUCCEEDED를 먼저 확정한 뒤 별도 completion transaction에서 수량·재고를 반영한다.

### 현재 적용 구조

`PaymentCancellation SUCCEEDED + ReturnRequest REFUNDING`은 `ReturnCompletionRecoveryService`가 새 PG 호출 없이 다시 완료한다. 완료 transaction은 ReturnRequest COMPLETED를 멱등성 장벽으로 사용한다.

## Payment refund balance와 예약액

### 원인 또는 위험

성공액만 차감하면 동시에 준비된 두 환불이 원 결제금액을 초과할 수 있다.

### 해결 / 현재 적용 구조

```text
availableRefundAmount
= Payment.amount
- SUM(SUCCEEDED PaymentCancellation.amount)
- SUM(REQUESTED PaymentCancellation.amount)
```

Return 5 계산 시에는 아직 PaymentCancellation이 없는 계산 확정 Return snapshot도 예약액으로 고려한다. Return 6에서 실제 PaymentCancellation이 만들어지면 같은 Return snapshot을 다시 빼지 않도록 미예약 snapshot만 별도로 합산해 이중 차감을 막는다. 실제 PG 실행 준비 transaction에서 잔액을 다시 검증한다.

# Cancellation

## 부분취소 수량 동시성

### 원인 또는 위험

같은 OrderItem에 대한 요청이 동시에 진행되면 `canceledQuantity`와 활성 REQUESTED/PROCESSING 점유수량 합계가 주문수량을 넘을 수 있다.

### 해결 / 현재 적용 구조

Payment/Order/SellerOrder와 관련 OrderItem을 비관적으로 잠그고 item ID를 정렬한다. 잠금 transaction 안에서 확정수량과 활성 요청 점유수량을 다시 조회하며, 완료 시 `OrderItem.confirmCancellation`이 최종 범위를 검증한다.

## 원 배송비 중복 환불

### 원인 또는 위험

SellerOrder에 여러 부분취소·반품이 순차 또는 동시 진행되면 마지막 전량 처리 요청이 원 배송비를 중복 포함할 수 있다.

### 해결 / 현재 적용 구조

취소는 SellerOrder 전량 취소 여부를 기준으로 주문 당시 `OrderItem.shippingFee` snapshot을 사용한다. Return 계산은 기존 성공/예약 PaymentCancellation과 원 배송비를 이미 포함한 다른 Return snapshot을 확인한다. Cancellation과 Return 양쪽을 함께 조회해 같은 SellerOrder 원 배송비가 다시 포함되지 않도록 한다.

# Shipment

## SellerOrder 배송 컬럼에서 Shipment 1:N으로 전환

### 상황 / 원인

SellerOrder의 회사명·송장번호 한 세트만으로는 최초배송, 반품회수, 교환회수와 교환 재배송 이력을 동시에 보존할 수 없다.

### 해결 / 현재 적용 구조

SellerOrder는 주문 처리 상태를 유지하고 Shipment가 실제 물류 이동을 담당한다. `ORIGINAL_OUTBOUND`, `RETURN_COLLECTION`, `EXCHANGE_COLLECTION`, `EXCHANGE_OUTBOUND` 타입을 구분하며 최초 배송 source of truth는 ORIGINAL_OUTBOUND다. Return은 자신에게 연결된 RETURN_COLLECTION을 참조한다.

## legacy dual-write와 fallback

### 원인 또는 위험

SellerOrder 배송 컬럼을 즉시 제거하면 기존 데이터, API 응답과 rollback 경로가 깨질 수 있다.

### 해결 / 현재 적용 구조

`shippingCompany`, `trackingNumber`, `shippedAt`, `deliveredAt`은 migration/rollback snapshot으로 유지한다. 신규 쓰기는 Shipment를 먼저 전이한 뒤 legacy snapshot 방향으로 동기화한다. 조회는 ORIGINAL_OUTBOUND를 우선하고 backfill 전 행에만 legacy fallback을 허용한다.

## ORIGINAL_OUTBOUND backfill 검증

개발 DB에서 확인된 기록:

- SellerOrder 32 → ORIGINAL_OUTBOUND DELIVERED
- SellerOrder 34 → ORIGINAL_OUTBOUND SHIPPED
- 누락, 중복, legacy 배송정보 불일치, 상태·timestamp 불일치 검증 4종 모두 0 rows

관련 SQL은 `shipment-original-outbound-backfill.sql`과 `shipment-original-outbound-verification.sql`이다.

# Return

## 반품 가능 수량 동시성

### 현재 적용 구조

```text
availableReturnQuantity
= quantity - canceledQuantity - returnedQuantity
- 활성 ReturnRequest 점유수량
```

REQUESTED/APPROVED/COLLECTING/RECEIVED/INSPECTED/REFUNDING을 활성 점유로 본다. Order → SellerOrder → 정렬된 OrderItem 잠금 뒤 DB 기준으로 다시 계산하며 Frontend 표시값은 신뢰하지 않는다.

## clientRequestKey 멱등성

동일 key와 의미상 동일한 Order/SellerOrder/사유/회수주소/item·수량 payload면 기존 ReturnRequest를 반환한다. 같은 key를 다른 payload에 재사용하면 충돌로 거부한다. DB UNIQUE race도 기존 결과 확인 경로로 처리한다.

## 환불 계산은 OrderItem snapshot 사용

현재 Product 가격이나 배송비는 주문 이후 바뀔 수 있다. 반품 상품금액과 원/반품/교환 배송비는 `OrderItem.unitPrice`, `shippingFee`, `returnShippingFee`, `exchangeShippingFee`만 사용한다. 곱셈과 합산은 exact 연산으로 overflow를 방어한다.

## 전체반품 판정과 계산 확정 Return

전체반품은 현재 요청 완료를 가정한 SellerOrder 모든 item의 잔여수량으로 판단한다. canceledQuantity, returnedQuantity, 현재 Return 수량과 계산 확정된 다른 Return 수량을 반영한다. 다른 활성 요청만 보고 원 배송비를 중복 지급하지 않도록 SellerOrder와 Payment를 잠근 상태에서 snapshot·PaymentCancellation 기록을 함께 확인한다.

## returnedQuantity와 restockedQuantity 분리

returnedQuantity는 실제 반품 완료 수량이고 restockedQuantity는 그중 다시 판매 가능한 수량이다. 모든 item은 완료 시 returnedQuantity가 증가하지만 RESTOCKABLE만 Product/Variant 재고를 복원하고 restockedQuantity를 기록한다. NON_RESTOCKABLE은 환불되더라도 판매 재고에 넣지 않는다.

## PG 성공 후 Return completion 실패

PG 성공 transaction과 Return completion을 분리한다. 후처리 실패 시 PaymentCancellation SUCCEEDED와 ReturnRequest REFUNDING이 남으며 completion recovery가 재시도한다. COMPLETED이면 수량과 재고를 다시 반영하지 않는다.

## 0원 환불

refundAmount가 0이면 PG 호출과 PaymentCancellation을 만들지 않는다. `ReturnRequest REFUNDING + refundAmount == 0`을 completion 조건으로 사용하며 returnedQuantity, RESTOCKABLE 재고와 COMPLETED 처리는 일반 환불과 동일하다.

# DB / 개발환경

## ddl-auto:update와 수동 SQL의 중복 실행

개발환경에서 Hibernate가 이미 컬럼이나 제약을 추가한 뒤 같은 ALTER SQL을 실행하면 duplicate column/constraint 오류가 발생할 수 있다. `docs/sql`은 자동 실행 파일이 아니라 스키마 확인·백업 후 사용하는 수동 migration 참고본이다. 운영 전에는 Flyway/Liquibase 같은 versioned migration으로 전환해야 한다.

MySQL Safe Update Mode Error 1175 사례의 정확한 실행 쿼리와 해결 절차는 현재 저장소에서 확인되지 않아 이 문서에 추정 기록을 추가하지 않았다.

# 개발 진행 메모

- Payment/Toss 결제 승인, 전체취소와 CANCELING reconciliation 구축
- OrderCancellation 부분수량·부분환불, 판매자 승인/거절과 orphan recovery 안정화
- SellerOrder 단일 배송 snapshot에서 Shipment 1:N 구조로 비파괴 전환 및 개발 DB backfill 검증
- OrderItem에 반품/교환 배송비와 returnedQuantity snapshot 구조 추가
- Return 구매자 요청·조회와 판매자 승인/거절·회수·입고·검수 구현
- Return 환불 예정금액 snapshot, 배송비 중복 방지와 refund balance 검증 구현
- Return PARTIAL PG 환불, 결과 불명 reconciliation와 webhook 연결
- Return returnedQuantity, RESTOCKABLE 재고복원, completion recovery와 COMPLETED 구현
- 현재: Return Backend 1~7 완료, Return Frontend와 Exchange 미구현, 운영 staging PG/반품 E2E 미검증
