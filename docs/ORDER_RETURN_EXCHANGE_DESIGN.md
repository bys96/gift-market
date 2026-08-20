# Gift Market 주문 반품 / 교환 설계

> 최종 확정: 2026-08-20
>
> 기준 우선순위: 현재 실제 코드 > 최신 문서 > 인수인계 내용.
> 이 문서는 현재 `gift-market` 코드의 주문/결제/취소/재고/배송 구조를 유지하면서 실제 운영 가능한 반품·교환 기능을 추가하기 위한 확정 설계다.

## 1. 목표와 범위

SHIPPED 이후 기존 주문취소와 분리하여, DELIVERED 된 SellerOrder에 대해 부분 수량 반품과 교환을 안전하게 처리한다.

핵심 원칙:

- 기존 `OrderCancellation`과 반품/교환 도메인을 합치지 않는다.
- `Order / Payment / SellerOrder / OrderItem` 기존 구조를 유지한다.
- 멀티셀러에서는 하나의 반품/교환 요청이 반드시 하나의 SellerOrder 안에서만 동작한다.
- Frontend가 전달한 금액을 신뢰하지 않는다.
- 상품/배송비는 주문 당시 snapshot을 기준으로 계산한다.
- 반품은 판매자 회수/검수 후 환불한다.
- 반품 요청/승인 시점에는 판매 가능 재고를 복원하지 않는다.
- PG 환불은 기존 `PaymentCancellation + PaymentGateway` 구조를 재사용한다.
- timeout/5xx/응답 유실을 즉시 실패로 단정하지 않는다.
- 중복 환불/중복 재고복원/중복 요청을 방지한다.
- Seller ownership과 구매자 ownership을 Backend에서 최종 검증한다.
- 최초 배송/반품 회수/교환 회수/교환 재배송의 송장 이력을 보존하기 위해 `Shipment` 도메인을 도입한다.
- `SellerOrder`는 주문 처리 상태를, `Shipment`는 실제 배송 실행/송장 이력을 담당한다.
- 하나의 `SellerOrder`에는 여러 `Shipment`가 존재할 수 있다.
- 반품/교환 요청은 자신이 사용하는 회수/재배송 Shipment를 명시적으로 참조한다.
- 최초 배송 정보도 최종적으로 `Shipment`를 단일 진실 공급원(source of truth)으로 사용한다.

## 2. 현재 코드와 향후 확장 구조

```text
Order
├─ Payment
│  └─ PaymentCancellation N
│
├─ SellerOrder A
│  ├─ OrderItem N
│  ├─ Shipment N
│  │   ├─ ORIGINAL_OUTBOUND
│  │   ├─ RETURN_COLLECTION
│  │   ├─ EXCHANGE_COLLECTION
│  │   └─ EXCHANGE_OUTBOUND
│  ├─ OrderCancellation N
│  │   └─ OrderCancellationItem N
│  ├─ ReturnRequest N (미구현 예정)
│  └─ ExchangeRequest N (미구현 예정)
│
└─ SellerOrder B
   └─ ...
```

현재 코드에서 확인된 핵심 사항:

- 현재 구현 완료: Order / Payment / SellerOrder / OrderItem / OrderCancellation / Shipment
- 현재 미구현: ReturnRequest / ReturnRequestItem / ExchangeRequest / ExchangeRequestItem 및 관련 Service/UI

- `SellerOrderStatus`
  - `PENDING_PAYMENT`
  - `PAID`
  - `PREPARING`
  - `SHIPPED`
  - `DELIVERED`
  - `CANCELLED`
- `SellerOrder`
  - 현재 코드에는 `shippingCompany`, `trackingNumber`, `shippedAt`, `deliveredAt`가 직접 존재한다.
  - 송장/배송 실행 정보는 `Shipment`로 이전 완료했다.
  - `preparedAt`은 SellerOrder의 주문처리 lifecycle timestamp이므로 유지한다.
  - `SellerOrder.status`는 기존 `PAID → PREPARING → SHIPPED → DELIVERED` 상태 전이를 그대로 유지한다.
  - `shippingCompany`, `trackingNumber`, `shippedAt`, `deliveredAt`은 비파괴 migration과 rollback을 위한 legacy snapshot으로 당분간 유지하고 신규 로직의 검증 기준으로 사용하지 않는다.
  - 신규 출고/배송완료의 source of truth는 `ORIGINAL_OUTBOUND Shipment`이며 기존 API 응답도 Shipment를 우선 사용한다.
  - backfill 전 기존 행에 한해서만 legacy 읽기 fallback과 배송완료 시 lazy migration을 허용한다.
- `OrderItem`
  - 주문 당시 상품/옵션/가격 snapshot
  - `freeShipping`
  - `shippingFee`
  - `canceledQuantity`
- `Product`
  - `returnShippingFee`
  - `exchangeShippingFee`
  - 기본값: 반품 편도 3,000원 / 교환 왕복 6,000원
- `PaymentCancellation`
  - FULL/PARTIAL PG 취소 transaction
  - 고정 idempotency key
  - provider transaction key
  - REQUESTED/SUCCEEDED/FAILED 상태
- `OrderInventoryService`
  - Product/Variant pessimistic lock
  - 취소 수량 단위 재고복원 primitive 보유
- 기존 취소 환불 계산
  - `OrderItem.unitPrice` 기준
  - SellerOrder 전량 취소 시 `OrderItem.shippingFee` 합계 환불

## 3. Shipment 도입 설계

### 3.1 도입 이유

기존 `SellerOrder`의 단일 `shippingCompany / trackingNumber`만으로는 다음 실제 운영 이력을 동시에 보존할 수 없다.

```text
최초 배송        Seller → Buyer    송장 A
반품 회수        Buyer  → Seller   송장 B
교환 회수        Buyer  → Seller   송장 C
교환 재배송      Seller → Buyer    송장 D
```

교환 재배송 시 기존 SellerOrder 송장을 덮어쓰면 최초 배송 이력이 유실된다.
반대로 ReturnRequest/ExchangeRequest에 회사명과 송장번호를 각각 직접 두면 배송 정보가 여러 도메인에 분산된다.

따라서 배송 실행 이력을 `Shipment`로 분리한다.

실제 오픈마켓 운영 흐름도 회수와 재배송을 별도 단계/송장으로 관리한다.
특히 판매자 배송 교환은 일반적으로 회수 완료/검수 후 교환품 재배송 정보를 별도로 등록하는 흐름을 사용한다.

### 3.2 역할 분리

```text
SellerOrder
= 판매자별 주문 처리 상태

Shipment
= 실제 한 번의 물류 이동과 송장 이력

ReturnRequest
= 반품 업무 상태/검수/환불

ExchangeRequest
= 교환 업무 상태/교환품 재고/배송비/재배송
```

SellerOrder 상태를 Shipment 상태로 대체하지 않는다.

### 3.3 Shipment 기본 구조

권장 필드:

```text
Shipment
- id
- sellerOrder
- type
- status
- shippingCompany
- trackingNumber
- shippedAt
- deliveredAt
- createdAt
- updatedAt
```

권장 enum:

```text
ShipmentType
- ORIGINAL_OUTBOUND
- RETURN_COLLECTION
- EXCHANGE_COLLECTION
- EXCHANGE_OUTBOUND
```

```text
ShipmentStatus
- READY
- SHIPPED
- DELIVERED
- CANCELED
```

상태 전이는 `READY → SHIPPED → DELIVERED`와 `READY → CANCELED`로 제한한다.
`CANCELED`는 향후 준비된 회수/교환 송장을 실제 택배 인계 전에 취소하는 경우를 위한 상태다.
이미 `SHIPPED`인 Shipment는 취소하지 않으며 출고 이후 역물류는 Return/Exchange 업무와 별도 Shipment로 처리한다.

1차에서는 택배사 API 실시간 추적 상태까지 모델링하지 않는다.
`IN_TRANSIT`, `OUT_FOR_DELIVERY` 같은 세부 상태는 실제 택배사 연동 시 확장한다.

### 3.4 관계

```text
SellerOrder 1 : N Shipment

ReturnRequest
└─ collectionShipment 0..1

ExchangeRequest
├─ collectionShipment 0..1
└─ replacementShipment 0..1
```

Shipment는 항상 하나의 SellerOrder에 속한다.
반품/교환 요청이 Shipment를 참조할 때 해당 Shipment의 SellerOrder가 요청의 SellerOrder와 같은지 Backend에서 검증한다.

`Shipment`가 nullable `returnRequestId / exchangeRequestId`를 여러 개 직접 들고 업무 도메인을 역참조하는 구조는 사용하지 않는다.
업무 요청이 자신에게 필요한 Shipment FK를 참조하는 방향으로 단순하게 유지한다.

### 3.5 최초 배송 migration

현재 정상 동작 중인 SellerOrder 배송 기능을 깨지 않도록 Shipment 도입을 반품 Entity보다 먼저 완료한다.

최종 목표는:

```text
최초 출고 등록
→ Shipment(type=ORIGINAL_OUTBOUND) 단일 생성
→ SellerOrder.status = SHIPPED

최초 배송완료
→ ORIGINAL_OUTBOUND Shipment = DELIVERED
→ SellerOrder.status = DELIVERED
```

현재 API 응답의 `shippingCompany / trackingNumber / shippedAt / deliveredAt` 필드는 바로 삭제하지 않는다.
Frontend 회귀를 줄이기 위해 기존 응답 shape은 유지하고 Backend DTO가 `ORIGINAL_OUTBOUND Shipment`에서 값을 읽어 채운다.

Entity의 기존 배송 필드는 운영 rollback이 가능한 동안 legacy snapshot으로 dual-write한다.
신규 로직은 Shipment를 먼저 생성/전이한 뒤 SellerOrder 처리 상태를 전이하며, legacy 필드는 Shipment 값으로만 동기화한다.
DTO는 Shipment를 우선 사용하고 backfill 전 기존 행에만 legacy fallback을 적용한다.

ORIGINAL_OUTBOUND 단일 생성은 Order와 SellerOrder를 기존 순서대로 비관적 잠금한 뒤 존재 여부를 검사하여 직렬화한다.
반품/교환에서는 같은 SellerOrder와 type의 Shipment가 여러 건 생길 수 있으므로 `(seller_order_id, shipment_type)` 전체 UNIQUE 제약은 두지 않는다.

운영 backfill과 검증은 다음 수동 SQL을 사용한다.

- `docs/sql/shipment-original-outbound-backfill.sql`
- `docs/sql/shipment-original-outbound-verification.sql`

### 3.6 왜 지금 Shipment가 과설계가 아닌가

현재는 이미 다음 요구가 확정되어 있다.

- 반품 회수 송장
- 교환 회수 송장
- 교환 재배송 송장
- 최초 배송 송장 보존

즉 한 SellerOrder에서 두 개 이상의 배송 이력이 실제로 필요하다.
Shipment는 미래를 위한 추상화가 아니라 현재 반품/교환 요구를 정상적으로 표현하기 위한 도메인이다.

### 3.7 실제 오픈마켓 흐름과의 정합성

실제 판매자배송 교환은 일반적으로 다음 흐름을 사용한다.

```text
교환 요청
→ 회수 지시/회수 송장
→ 판매자 수거완료
→ 상품 검수
→ 교환배송비 처리 확인
→ 교환품 재배송 송장 등록
→ 재배송 완료
→ 교환 완료
```

스마트스토어도 수거 완료 후 재배송 처리와 재배송 배송정보 입력을 별도 단계로 관리한다.
쿠팡 판매자배송도 회수 상품이 판매자에게 도착한 후 교환품을 발송하는 흐름을 기본으로 안내한다.
따라서 회수 Shipment와 재배송 Shipment를 구분하는 현재 설계가 실제 운영 방식과 일치한다.

선출고/맞교환은 일부 물류 모델에서 가능하지만 Gift Market 1차 정책으로 자동 지원하지 않는다.
판매자 배송 오픈마켓의 보수적인 기본 흐름인 **회수 → 검수 → 재배송**을 사용한다.

### 3.8 배송 주소 경계

실제 서비스에서는 교환 회수지와 교환품 재배송지가 서로 다를 수 있다.
현재 Gift Market `Order`에는 최초 배송지 snapshot이 있지만 Seller/Product 쪽에 별도 반품지/물류지 주소 도메인은 아직 없다.

따라서 1차 Shipment는 다음 책임에 집중한다.

```text
- 배송 종류
- 배송 상태
- 택배사
- 송장번호
- 출고/도착 시각
- SellerOrder 연결
```

반품/교환 신청에서는 구매자의 회수지 snapshot을 Claim(Request) 쪽에 보존할 수 있게 설계하고,
판매자의 반품지/출고지는 향후 자동 수거/택배사 API 연동 전에 `SellerLogisticsAddress` 또는 동등한 판매자 물류주소 도메인으로 추가한다.

현재 단계에서 존재하지 않는 판매자 물류주소 모델을 Shipment 도입과 동시에 억지로 만들지는 않는다.
수동 송장 등록 기반의 1차 운영에는 Shipment만으로 충분하고, 택배사 자동수거 연동 시 주소 모델을 확장한다.

## 4. 현재 코드에 필요한 최소 선행 변경

현재 `Product`에는 반품/교환 배송비가 존재하지만 `OrderItem`에는 주문 당시 snapshot이 없다.

반품/교환 구현 1단계에서 `OrderItem`에 다음 필드를 추가한다.

```text
returnShippingFee
exchangeShippingFee
```

주문 생성 시:

```text
OrderItem.returnShippingFee
= Product.returnShippingFee

OrderItem.exchangeShippingFee
= Product.exchangeShippingFee
```

이유:

- 판매자가 이후 상품의 반품/교환 배송비를 변경해도 과거 주문 금액이 바뀌면 안 된다.
- 현재 `productPrice`, `additionalPrice`, `shippingFee`, `freeShipping` snapshot 방식과 동일하다.
- 반품 요청 시점의 Product 현재값을 사용하지 않는다.

기존 개발 DB 데이터는 마이그레이션/개발 SQL에서 현재 Product 값을 기준으로 backfill한다. 운영 배포 이후에는 주문 생성 시점부터 snapshot을 반드시 저장한다.

## 5. 주문취소와 반품/교환 경계

현재 취소 정책은 그대로 유지한다.

| SellerOrder 상태 | 처리 |
|---|---|
| PAID | 기존 즉시취소 |
| PREPARING | 기존 판매자 승인형 취소 |
| SHIPPED | 기존 주문취소 불가 |
| DELIVERED | 기존 주문취소 불가, 반품/교환 가능 |
| CANCELLED | 반품/교환 불가 |

1차 반품/교환은 `DELIVERED` 상태에서만 신청 가능하게 한다.

`SHIPPED` 중 배송거부/회송은 실제 물류사 연동과 배송 상태 세분화가 필요하므로 이번 범위에 억지로 넣지 않는다.

```text
PAID / PREPARING
→ Cancellation

SHIPPED
→ 배송 완료 전 기존 취소/반품 신청 불가

DELIVERED
→ Return / Exchange
```

## 6. 반품 가능 기간

### 구매자 귀책

단순변심, 옵션 선택 착오 등 구매자 귀책은:

```text
deliveredAt + 7일 이내
```

신청 가능하게 한다.

### 판매자 귀책

상품 하자, 오배송, 표시 내용과 다른 상품 등 판매자 귀책은 구매자 단순변심보다 긴 기간을 허용한다.

1차 구현에서는 Backend 정책을 별도 메서드로 분리하여 추후 운영/법무 정책 변경이 가능하게 한다.

최소 원칙:

- 구매자 귀책 7일 정책과 동일하게 하드코딩하지 않는다.
- `ORIGINAL_OUTBOUND Shipment.deliveredAt`이 없는 주문은 자동 반품/교환 신청을 허용하지 않는다. migration 안정화 기간에만 SellerOrder legacy 값 fallback을 고려한다.
- 관리자 CS 기능이 추가되면 예외 승인 경로를 둘 수 있게 한다.

## 7. 반품 사유와 귀책

Frontend가 `BUYER / SELLER` 귀책 값을 임의로 보내게 하지 않는다.

구매자는 정해진 사유 코드를 선택하고 상세 사유를 입력한다.
Backend가 사유 코드에 따라 귀책을 결정한다.

예시:

```text
ReturnReasonType
- CHANGE_OF_MIND
- OPTION_MISTAKE
- DEFECTIVE
- WRONG_ITEM
- DAMAGED
- DESCRIPTION_MISMATCH
- OTHER
```

```text
ReturnResponsibility
- BUYER
- SELLER
```

기본 mapping:

```text
CHANGE_OF_MIND      → BUYER
OPTION_MISTAKE      → BUYER
DEFECTIVE           → SELLER
WRONG_ITEM          → SELLER
DAMAGED             → SELLER
DESCRIPTION_MISMATCH→ SELLER
OTHER               → 판매자/관리자 확인 필요
```

`OTHER`는 자동 환불까지 바로 진행하지 않고 판매자가 귀책을 확인하도록 한다.

교환도 동일한 귀책 모델을 재사용하되 Cancellation과 하나의 도메인으로 합치지는 않는다.

## 8. 반품 도메인

기본 구조:

```text
ReturnRequest
├─ ReturnRequestItem
├─ PaymentCancellation (환불 발생 시 0..1)
└─ 회수/검수 정보
```

### ReturnRequest

한 요청은 하나의 SellerOrder에만 속한다.

주요 필드 방향:

```text
id
order
sellerOrder
clientRequestKey
reasonType
reason
responsibility
status

productRefundAmount
originalShippingRefundAmount
returnShippingCharge
refundAmount

requestedAt
approvedAt
collectingAt
receivedAt
inspectedAt
refundingAt
completedAt
rejectedAt
canceledAt
failedAt

rejectedReason
collectionRecipientName
collectionPhone
collectionPostalCode
collectionAddress
collectionAddressDetail
collectionShipment
```

금액 필드는 환불 실행 전에 Backend가 계산한 값을 snapshot으로 확정한다.

### ReturnRequestItem

```text
id
returnRequest
orderItem
quantity
inspectionResult
restockedQuantity
```

한 요청에서 동일 `orderItem`을 중복 등록하지 않는다.

부분 수량 반품을 지원한다.

## 9. 반품 상태 머신

```text
REQUESTED
→ APPROVED
→ COLLECTING
→ RECEIVED
→ INSPECTED
→ REFUNDING
→ COMPLETED
```

분기:

```text
REQUESTED → REJECTED
REQUESTED / APPROVED → CANCELED
REFUNDING → FAILED
```

권장 enum:

```text
ReturnRequestStatus
- REQUESTED
- APPROVED
- COLLECTING
- RECEIVED
- INSPECTED
- REFUNDING
- COMPLETED
- REJECTED
- CANCELED
- FAILED
```

의미:

- `REQUESTED`: 구매자 신청, 판매자 확인 대기
- `APPROVED`: 판매자 반품 승인/회수 준비
- `COLLECTING`: 반품 회수 진행
- `RECEIVED`: 판매자에게 반품 상품 도착
- `INSPECTED`: 수량/상태 검수 완료
- `REFUNDING`: PG 환불 처리 중
- `COMPLETED`: 환불 및 필요한 재고 반영 완료
- `REJECTED`: 유효한 사유로 반품 거절
- `CANCELED`: 구매자가 허용 시점에 반품 철회
- `FAILED`: 명확한 환불 실패 또는 자동 복구 불가능 상태

판매자가 `REQUESTED`를 임의로 거절할 수 있게만 두지 않고, 거절 사유를 필수 저장한다. 향후 관리자 CS에서 분쟁 확인이 가능해야 한다.

## 10. 반품 가능 수량

현재 `OrderItem`에는:

```text
quantity
canceledQuantity
```

가 존재한다.

반품 구현 시 완료 반품 수량 추적을 위해:

```text
returnedQuantity
```

를 추가한다.

기본 계산:

```text
remainingDeliveredQuantity
= quantity
- canceledQuantity
- returnedQuantity
```

새 반품/교환 요청을 받을 때는 여기에 활성 상태 요청 수량까지 차감한다.

```text
availableReturnQuantity
= quantity
- canceledQuantity
- returnedQuantity
- activeReturnRequestQuantity
- activeExchangeRequestQuantity
```

다음 상태는 활성 수량으로 본다.

```text
REQUESTED
APPROVED
COLLECTING
RECEIVED
INSPECTED
REFUNDING
```

COMPLETED/REJECTED/CANCELED/FAILED는 각 상태의 의미에 맞게 중복 계산 여부를 명확히 분리한다.

DB lock 상태에서 최종 검증하여 double click/동시 요청을 방지한다.

## 11. 반품 배송비 정책

Gift Market은 실제 오픈마켓에서 일반적으로 사용하는 편도/왕복 반품비 구조를 적용한다.

현재 Product 정책:

```text
returnShippingFee
= 반품 편도 배송비
= 기본 3,000원

exchangeShippingFee
= 왕복 배송비
= 기본 6,000원
```

실제 계산은 OrderItem snapshot 기준이다.

### 판매자 귀책

```text
반품 배송비 구매자 부담 = 0
```

SellerOrder 전체 반품이면 최초 결제한 원 배송비도 환불한다.

```text
부분 반품
→ 상품금액 환불
→ 원 배송비 환불 없음
→ 반품비 0

전체 반품
→ 상품금액 환불
→ 원 배송비 환불
→ 반품비 0
```

### 구매자 귀책 — 부분 반품

SellerOrder에 반품하지 않은 수량이 남는 경우:

```text
상품금액 환불
원 배송비 환불 없음
반품 편도비 차감
```

```text
returnShippingCharge
= 선택 반품 상품의 returnShippingFee snapshot 기준
```

### 구매자 귀책 — SellerOrder 전체 반품

이번 반품 완료 후 SellerOrder에 유효 수량이 하나도 남지 않는 경우:

```text
상품금액 환불
+ 최초 결제 원 배송비 환불
- 왕복배송비 차감
```

무료배송 주문은 최초 결제 배송비가 0원이므로 결과적으로 왕복배송비 전체가 구매자 부담이 된다.

예:

```text
무료배송 상품 30,000원
왕복배송비 6,000원
→ 최종 PG 환불 24,000원
```

유료배송 3,000원 상품 30,000원 예:

```text
상품금액 30,000
원 배송비 환불 +3,000
왕복배송비 -6,000
→ 최종 PG 환불 27,000원
```

즉 구매자가 최초 발송 편도비와 반품 회수 편도비를 부담하는 결과가 된다.

### 여러 OrderItem을 한 번에 반품할 때

같은 ReturnRequest는 기본적으로 하나의 회수 Shipment 단위로 본다.

상품별 반품/교환 배송비가 다를 수 있으므로 1차 정책은:

```text
부분 반품 편도비
= 요청에 포함된 OrderItem.returnShippingFee 중 최대값

SellerOrder 전체 반품 왕복비
= 요청에 포함된 OrderItem.exchangeShippingFee 중 최대값
```

수량 또는 OrderItem 개수만큼 배송비를 단순 합산하지 않는다.

향후 배송정책/묶음배송이 확장되면 배송비 계산 정책을 현재 Shipment 구조와 결합한 별도 배송정책 단위로 이전할 수 있다.

## 12. 반품 환불 금액 계산

Frontend 금액은 사용하지 않는다.

### 상품 환불액

```text
productRefundAmount
= Σ(OrderItem.unitPrice × returnQuantity)
```

### 원 배송비 환불액

```text
이번 반품 후 SellerOrder 유효 수량이 남음
→ originalShippingRefundAmount = 0

이번 반품 후 SellerOrder 유효 수량이 0
→ originalShippingRefundAmount = Σ(OrderItem.shippingFee)
```

기존 Cancellation 배송비 계산 정책과 동일한 기준을 유지한다.

### 최종 PG 환불액

```text
refundAmount
= productRefundAmount
+ originalShippingRefundAmount
- returnShippingCharge
```

필수 검증:

```text
productRefundAmount >= 0
originalShippingRefundAmount >= 0
returnShippingCharge >= 0
refundAmount >= 0
refundAmount <= Payment 환불 가능 잔액
```

`refundAmount > 0`이면 PG 부분환불을 수행한다.

`refundAmount == 0`이면 PG 호출 없이 반품 정산을 완료할 수 있지만, 상품/배송비 계산 snapshot과 완료 이력을 반드시 남긴다.

`refundAmount < 0`이 되는 반품은 자동 완료하지 않는다. 추가 수납이 필요한 CS 케이스로 분리한다.

오버플로는 기존 취소 계산과 동일하게 `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact` 계열로 방어한다.

## 13. PaymentCancellation 재사용

반품 환불도 새로운 PG 환불 테이블을 만들지 않고 기존 `PaymentCancellation`을 사용한다.

`PaymentCancellationType`은 그대로 유지한다.

```text
FULL
PARTIAL
```

반품 환불은 원 결제의 부분환불 transaction이므로:

```text
PaymentCancellationType.PARTIAL
```

을 사용한다.

다만 현재 `PaymentCancellation`은 PARTIAL에 대해 `OrderCancellation`만 직접 연결한다.

반품 구현 시 최소 변경:

```text
PaymentCancellation
- orderCancellation nullable 유지
- returnRequest nullable 추가
```

DB 제약:

```text
return_request_id UNIQUE
```

PARTIAL 생성 시 업무 source는 정확히 하나만 연결되도록 Backend에서 검증한다.

```text
OrderCancellation XOR ReturnRequest
```

새로운 `PaymentCancellationType.RETURN` 같은 값을 만들지 않는다.

`FULL/PARTIAL`은 PG 취소 금액 범위를 의미하고, 업무 종류는 FK 관계로 구분한다.

## 14. 기존 결제 안정성 재사용 범위

반품 환불은 기존 Cancellation Service를 그대로 억지로 호출하지 않는다.

재사용 대상:

- `PaymentGateway`
- `PaymentGatewayRegistry`
- Toss cancel API
- 고정 idempotency key 정책
- `PaymentRefundBalanceService`
- PG payment query 검증
- timeout/5xx uncertain 처리 원칙
- reconciliation 방식
- webhook 중복 처리 원칙

반품 전용 업무 Service가 기존 결제 하위 primitive를 사용한다.

```text
ReturnRefundExecutionService
→ PaymentGateway
→ TossPaymentGateway
```

Toss DTO를 Return/Seller Service에 직접 노출하지 않는다.

## 15. 반품 환불 transaction 경계

기존 부분취소 lock order와 최대한 동일하게 유지한다.

### Transaction A — 환불 준비

```text
Payment lock
→ Order lock
→ SellerOrder lock
→ ReturnRequest lock
→ 관련 OrderItem lock
→ ReturnRequestItem 검증
→ 반품 금액 재계산
→ Payment refund balance 검증
→ PaymentCancellation(PARTIAL) 준비
→ 고정 PG idempotency key 저장
→ commit
```

### 외부 PG

```text
Toss 최신 payment 조회
→ 식별정보/환불가능 상태 검증
→ 동일 idempotency key + refundAmount로 취소 호출
```

외부 HTTP 호출 중 DB transaction을 유지하지 않는다.

### Transaction B — 성공 확정

```text
Payment
→ Order
→ SellerOrder
→ ReturnRequest
→ PaymentCancellation
→ OrderItem
순서 재잠금

PG 응답 검증
→ PaymentCancellation SUCCEEDED
→ Payment PARTIALLY_CANCELED 또는 CANCELED
→ returnedQuantity 증가
→ 재판매 가능 수량만 재고 복원
→ ReturnRequest COMPLETED
→ commit
```

## 16. 환불 결과 불명 / reconciliation

반품도 기존 부분취소와 동일한 원칙을 적용한다.

```text
timeout
5xx
응답 유실
```

은 곧바로 환불 실패로 처리하지 않는다.

- 같은 `PaymentCancellation.idempotencyKey` 재사용
- Toss 최신 payment/cancels 조회
- provider transaction key / 금액 / 사유 검증
- 성공 확인 후에만 ReturnRequest 완료
- 성공 확인 전 `returnedQuantity`/재고를 최종 반영하지 않음

운영 전 최종 E2E에서 기존 취소 reconciliation과 함께 반품 환불도 검증한다.

## 17. 반품 검수와 재고 복원

취소와 가장 큰 차이점이다.

```text
반품 요청
반품 승인
수거 시작
상품 도착
```

단계에서는 재고를 복원하지 않는다.

검수 결과를 ReturnRequestItem별로 저장한다.

권장 enum:

```text
ReturnInspectionResult
- RESTOCKABLE
- NON_RESTOCKABLE
```

정책:

```text
RESTOCKABLE
→ 검수 완료 후 환불 성공 확정 transaction에서 판매 가능 재고 복원

NON_RESTOCKABLE
→ 환불은 정책에 따라 가능
→ 판매 가능 재고에는 복원하지 않음
```

판매자 귀책 불량 상품이라고 해서 자동으로 판매 가능 재고에 넣지 않는다.

기존 `OrderInventoryService`의 Product/Variant lock primitive를 재사용하되 `OrderCancellationItem` 전용 메서드를 반품에 억지로 사용하지 않는다.

예:

```text
restoreReturnItems(...)
```

처럼 ReturnRequestItem 수량과 `RESTOCKABLE` 결과를 기준으로 복원한다.

`restockedQuantity`를 기록하여 중복 재고복원을 방어한다.

## 18. SellerOrder 상태와 반품 상태 관계

반품 때문에 기존 `SellerOrderStatus`를 추가하지 않는다.

배송이 이미 완료된 주문은:

```text
SellerOrder.status = DELIVERED
```

를 유지한다.

반품 진행 상태는 `ReturnRequest.status`가 담당한다.

전체 반품 완료 후에도 SellerOrder를 `CANCELLED`로 바꾸지 않는다.

이유:

- `CANCELLED`는 배송 전 주문취소 의미로 이미 사용 중이다.
- 배송 완료 후 반품된 주문과 배송 전에 취소된 주문은 운영/통계상 구분되어야 한다.

Frontend에서는 SellerOrder 상태와 ReturnRequest 상태를 조합해 사용자 표시 문구를 만든다.

## 19. 교환 범위

교환은 반품 도메인을 억지로 재사용하지 않고 별도 도메인으로 둔다.

```text
ExchangeRequest
└─ ExchangeRequestItem
```

1차 교환 지원 범위:

- DELIVERED SellerOrder
- 부분 교환
- 수량 일부 교환
- 동일 상품 내 Variant 교환 지원
- 교환 대상 Variant 가격이 원 주문 unitPrice와 동일한 경우 자동 교환 가능
- 가격 차이가 있는 Variant 교환은 자동 차액정산하지 않음
- 가격 차이가 있으면 반품 후 재구매 안내
- 옵션 없는 상품은 동일 상품 교체 가능

이 구조는 색상/사이즈 교환 같은 일반 쇼핑몰 사용 패턴을 지원하면서 차액 결제/부분환불 복잡성을 피한다.

교환 요청 시 구매자의 회수지와 교환품 재배송지를 각각 snapshot으로 저장한다.
기본값은 원 주문 배송지이지만 구매자가 허용 범위 내에서 별도 주소를 선택할 수 있게 확장 가능하도록 DTO/Entity를 설계한다.
배송지 원본 Address Entity FK만 저장하지 않고 요청 당시 문자열 snapshot을 보존한다.

## 20. 교환 상태 머신

```text
REQUESTED
→ APPROVED
→ COLLECTING
→ RECEIVED
→ INSPECTED
→ RESHIPPING
→ COMPLETED
```

분기:

```text
REQUESTED → REJECTED
REQUESTED / APPROVED → CANCELED
```

권장 enum:

```text
ExchangeRequestStatus
- REQUESTED
- APPROVED
- COLLECTING
- RECEIVED
- INSPECTED
- RESHIPPING
- COMPLETED
- REJECTED
- CANCELED
- FAILED
```

## 21. 교환 재고 정책

교환 받을 Variant가 필요한 경우 승인 시점에 재고를 선점한다.

```text
REQUESTED
→ 아직 교환품 재고 차감 안 함

APPROVED
→ 교환 대상 Product/Variant lock
→ 교환 수량 재고 예약/차감
```

이유:

- 회수/검수 완료까지 기다렸다가 재고를 잡으면 교환품 품절 가능성이 높다.
- 승인 이후 실제 교환이 취소/거절되면 선점 재고를 정확히 1회 복원해야 한다.

원 상품은 검수 결과 `RESTOCKABLE`일 때만 판매 가능 재고에 복원한다.

교환품 차감과 원 상품 복원은 서로 다른 재고 이벤트로 취급한다.

## 22. 교환 배송비 정책

### 판매자 귀책

```text
교환 배송비 = 0
```

구매자에게 추가 결제를 요구하지 않는다.

### 구매자 귀책

```text
교환 배송비
= 교환 대상 OrderItem.exchangeShippingFee snapshot 기준
```

기본 6,000원 왕복배송비다.

여러 OrderItem을 한 교환 요청으로 묶는 경우 하나의 교환 요청을 하나의 회수/재배송 묶음으로 처리하므로:

```text
exchangeShippingCharge
= 요청 포함 OrderItem.exchangeShippingFee 중 최대값
```

으로 한다.

수량만큼 곱하지 않는다.

## 23. 구매자 귀책 교환 배송비 결제

교환에는 상품 환불이 없으므로 반품처럼 환불액에서 6,000원을 차감할 수 없다.

원 주문 `Payment`에 추가 금액을 억지로 끼워 넣지 않는다.

현재 Payment는 Order 원 결제 lifecycle 전용이므로 다음 원칙을 적용한다.

```text
판매자 귀책 교환
→ 추가결제 없음

구매자 귀책 교환
→ 교환 배송비 별도 결제 필요
```

교환 구현 단계에서 별도 `ExchangeShippingPayment` 성격의 작은 결제 도메인을 설계한다.

요구사항:

- ExchangeRequest와 1:1
- 별도 merchant payment id
- 고정 idempotency key
- READY / CONFIRMING / PAID / FAILED / EXPIRED 등 기존 Payment 안정성 원칙 재사용
- `PaymentGateway` 추상화 재사용
- 원 주문 Payment 금액/상태를 변경하지 않음

구매자 귀책 교환은 배송비 결제 성공 전 `APPROVED` 이후 물류 진행을 시작하지 않는다.

이 추가결제는 반품 1차 구현과 분리하고 교환 구현 단계에서 진행한다.

## 24. 반품/교환 Shipment 흐름

반품과 교환의 송장정보를 Request Entity의 문자열 필드로 중복 저장하지 않는다.
Shipment를 생성하고 요청이 해당 Shipment를 참조한다.

### 반품

```text
ReturnRequest APPROVED
→ collectionShipment 준비 가능

회수 송장 등록
→ Shipment(type=RETURN_COLLECTION, status=SHIPPED)
→ ReturnRequest COLLECTING

판매자 입고 확인
→ Shipment DELIVERED
→ ReturnRequest RECEIVED
```

반품 회수가 택배사 자동수거가 아니라 구매자 직접 발송인 경우에도 동일한 Shipment 구조를 사용한다.
택배사 연동 여부만 다르고 데이터 모델은 바꾸지 않는다.

### 교환

```text
교환 회수 송장
→ Shipment(type=EXCHANGE_COLLECTION)

회수 완료/검수 완료
→ 교환품 재배송 가능

교환 재배송 송장
→ Shipment(type=EXCHANGE_OUTBOUND)
→ ExchangeRequest RESHIPPING

재배송 완료
→ replacementShipment DELIVERED
→ ExchangeRequest COMPLETED
```

1차 정책은 판매자 배송 상품 기준으로 **회수 완료 및 검수 후 재배송**한다.
선출고/맞교환은 재고와 분쟁 위험이 커서 자동 정책으로 지원하지 않고 향후 관리자 CS 예외 처리 범위로 둔다.

## 25. 교환 완료 후 SellerOrder 상태

교환 진행 중/완료 후에도:

```text
SellerOrder.status = DELIVERED
```

를 유지한다.

교환 업무 상태는 `ExchangeRequest.status`가 담당한다.

SellerOrder에 `EXCHANGING` 같은 상태를 추가하지 않는다.

## 26. 중복 반품/교환 방지

Backend에서 반드시 검증한다.

- 이미 취소된 수량
- 이미 완료 반품된 수량
- 활성 반품 요청 수량
- 활성 교환 요청 수량
- 이미 전량 반품된 SellerOrder
- 동일 `clientRequestKey`

`ReturnRequest.clientRequestKey`, `ExchangeRequest.clientRequestKey`는 UNIQUE로 둔다.

요청 생성 시 관련 SellerOrder/OrderItem을 lock하고 사용 가능 수량을 다시 계산한다.

## 27. 구매자/판매자 권한

구매자:

```text
Order.user.id == authenticatedUser.id
```

를 Backend에서 검증한다.

판매자:

```text
SellerOrder.seller.id == authenticatedSeller.id
```

를 Backend에서 검증한다.

Frontend에서 숨겨진 버튼은 보안수단으로 취급하지 않는다.

다른 SellerOrder의 OrderItem을 ReturnRequestItem/ExchangeRequestItem에 넣을 수 없게 Backend에서 검증한다.

## 28. API 방향

정확한 path/DTO는 구현 단계에서 기존 Controller convention을 다시 확인한 뒤 확정한다.

개념 API:

```text
구매자
POST   /api/orders/{orderId}/seller-orders/{sellerOrderId}/returns
GET    /api/orders/{orderId}/returns
GET    /api/returns/{returnRequestId}
POST   /api/returns/{returnRequestId}/cancel

판매자
GET    /api/seller/returns
GET    /api/seller/returns/{returnRequestId}
POST   /api/seller/returns/{returnRequestId}/approve
POST   /api/seller/returns/{returnRequestId}/reject
POST   /api/seller/returns/{returnRequestId}/collect
POST   /api/seller/returns/{returnRequestId}/receive
POST   /api/seller/returns/{returnRequestId}/inspect

교환도 동일한 buyer/seller 분리 convention 사용
```

실제 구현 시 기존 구매자 주문 API / 판매자 주문 API naming에 맞춰 최종 확정한다.

## 29. Frontend 방향

### 구매자 주문 상세

DELIVERED SellerOrder에서:

```text
반품 신청
교환 신청
```

노출.

신청 UI:

- OrderItem 선택
- 수량 선택
- 사유 선택
- 상세 사유
- 예상 환불액/배송비 표시

단 예상 금액은 안내용이며 Backend 응답을 최종값으로 사용한다.

### 판매자센터

- 반품 요청 목록
- 반품 상세
- 승인/거절
- 회수 진행
- 입고 확인
- 상품별 검수 결과
- 환불 진행/결과
- 반품/교환 회수 송장 등록
- 교환품 재배송 송장 등록
- 최초/회수/재배송 배송 이력 조회

기존 seller CSS entry/domain 분리 구조를 유지한다.

## 30. 기존 기능 회귀 보호

반품/교환 구현 중 다음 구조를 불필요하게 변경하지 않는다.

- 로그인/OAuth/JWT
- 판매자 권한
- 상품 조회/등록/수정
- Cart
- 바로구매
- 주문 prepare
- Payment READY/CONFIRMING/PAID
- webhook
- 전체취소
- 부분취소
- PREPARING 취소 승인/거절
- 기존 부분환불
- 기존 SellerOrder 배송상태
- 기존 재고 예약/복원

`PaymentCancellation`에 ReturnRequest 연결을 추가할 때 기존 `OrderCancellation` unique/FK와 서비스 동작을 반드시 회귀 테스트한다.

## 31. 구현 순서

Shipment 기반 배송 구조와 개발 DB migration은 완료됐다. 다음 단계부터 반품을 구현하고, Return 기본 흐름 안정화 후 교환으로 간다.

### Shipment 1 — Domain / Repository (완료)

- `Shipment`
- `ShipmentType`
- `ShipmentStatus`
- `ShipmentRepository`
- SellerOrder 1:N 관계
- ORIGINAL_OUTBOUND 단일 생성 정책

### Shipment 2 — 기존 최초 배송 migration (완료)

- 판매자 기존 출고 API를 ORIGINAL_OUTBOUND Shipment 기반으로 변경
- 배송완료 API를 Shipment 기반으로 변경
- SellerOrder 상태전이 유지
- 기존 buyer/seller response shape 유지
- Frontend 회귀 없이 tracking 정보 노출
- 기존 테스트 수정/추가

### Shipment 3 — 기존 데이터 migration (개발 DB 완료)

- `seller_orders.shipping_company / tracking_number / shipped_at / delivered_at` 기존 값으로 ORIGINAL_OUTBOUND Shipment backfill
- 데이터 검증 후 Shipment를 배송정보 source of truth로 전환
- 중복 필드 제거는 migration 검증 후 별도 단계에서 수행

개발 DB backfill 결과:

- SellerOrder 32 → ORIGINAL_OUTBOUND / DELIVERED
- SellerOrder 34 → ORIGINAL_OUTBOUND / SHIPPED
- 누락, 중복, legacy 배송정보 불일치, 상태·timestamp 불일치 검증 모두 0 rows
- SellerOrder legacy 배송 컬럼은 삭제하지 않고 migration/rollback snapshot 및 호환 fallback으로 유지

### Return 1 — 주문 snapshot 준비

- `OrderItem.returnShippingFee`
- `OrderItem.exchangeShippingFee`
- 주문 생성 snapshot
- 기존 데이터 backfill
- 테스트

### Return 2 — Domain

- ReturnReasonType
- ReturnResponsibility
- ReturnRequestStatus
- ReturnInspectionResult
- ReturnRequest
- ReturnRequestItem
- `OrderItem.returnedQuantity`

### Return 3 — Repository / lock / 요청 생성

- 구매자 ownership
- SellerOrder DELIVERED 검증
- 기간 검증
- 요청 가능 수량
- clientRequestKey 멱등성

### Return 4 — 판매자 workflow

- 승인
- 거절
- 회수
- 입고
- 검수

### Return 5 — 환불 계산

- 상품금액
- 원 배송비
- 구매자/판매자 귀책
- 편도/왕복 반품비
- Payment 환불 가능 잔액

### Return 6 — PG 환불

- PaymentCancellation ReturnRequest 연결
- PaymentGateway 재사용
- idempotency
- timeout/5xx
- reconciliation

### Return 7 — 재고/완료

- RESTOCKABLE만 복원
- returnedQuantity
- 중복복원 방지

### Return 8 — Backend 테스트

- 부분 수량
- 전체 반품
- 무료배송
- 유료배송
- 구매자 귀책
- 판매자 귀책
- 멀티셀러
- 중복 요청
- 환불잔액
- timeout/reconciliation

### Return 9 — Frontend

- 구매자 주문상세
- 반품 신청
- 반품 진행상태
- 판매자 반품관리
- CSS

### Exchange 1 이후

반품 완료 후:

- ExchangeRequest Domain
- 교환 대상 Variant
- 재고 선점
- 구매자 귀책 배송비 추가결제
- 회수/검수
- 재배송 송장
- 구매자/판매자 UI

## 32. 테스트 기준

Backend 각 큰 단계 후:

```bash
./gradlew test
```

Frontend 단계 후:

```bash
npm run lint
npm run build
```


Shipment 핵심 테스트:

- PREPARING SellerOrder만 최초 OUTBOUND 출고 가능
- 동일 최초 배송 중복 생성 방지
- 출고 시 SellerOrder SHIPPED 전이
- 최초 Shipment 배송완료 시 SellerOrder DELIVERED 전이
- 교환/반품 Shipment가 최초 송장을 덮어쓰지 않음
- Return/Exchange가 다른 SellerOrder Shipment를 참조하지 못함
- 기존 buyer/seller 주문 응답의 배송정보 회귀 없음

반품 핵심 테스트:

- DELIVERED 아닌 주문 차단
- 타 구매자 주문 차단
- 타 판매자 SellerOrder 차단
- canceledQuantity 초과 요청 차단
- 활성 반품/교환 수량 중복 차단
- 부분 반품 수량 계산
- 무료배송 전체반품 왕복비
- 유료배송 전체반품 원배송비 환불 + 왕복비 차감
- 부분반품 편도비
- 판매자 귀책 배송비 0
- 환불 가능 잔액 초과 차단
- PG 성공 전 재고 미복원
- RESTOCKABLE만 복원
- 동일 clientRequestKey 멱등성
- 동일 PG idempotency key 재사용
- timeout/5xx 결과 불명 복구
- webhook 중복

## 33. 운영 전 최종 통합 검증 TODO

기존 TODO를 유지하며 반품/교환을 추가한다.

- 실제 Toss 상점용 test key
- 공개 HTTPS staging
- 결제
- webhook
- 중복 webhook
- READY expiry
- CONFIRMING reconciliation
- 전체취소
- 부분취소
- PREPARING 취소 승인/거절
- 부분환불
- timeout / 5xx
- 멀티셀러
- Cart 정합성
- 재고 1회 복원
- ORIGINAL_OUTBOUND Shipment 기반 실제 출고
- Shipment 배송완료
- legacy fallback 제거 전 staging 검증
- dual-write 제거 전 migration/rollback 안정성 검증
- 반품 환불
- 반품 환불 timeout/reconciliation
- 반품 검수/재고 복원
- 구매자 귀책 무료배송 전체반품 왕복비
- 교환
- 교환 배송비 추가결제
- 교환 재고 선점/복원
- 운영 키 전환 전 전체 회귀

## 34. 최종 확정 사항

현재 코드와 실제 오픈마켓 반품/교환 운영 흐름을 기준으로 다음을 확정한다.

```text
1. Cancellation과 Return/Exchange는 별도 도메인이다.
2. 반품/교환은 1차에서 DELIVERED 이후만 처리한다.
3. Shipment를 정식 배송 도메인으로 추가하며 `READY → SHIPPED → DELIVERED`, `READY → CANCELED` 전이를 사용한다.
4. SellerOrder 1건에는 여러 Shipment가 존재할 수 있다.
5. ORIGINAL_OUTBOUND / RETURN_COLLECTION / EXCHANGE_COLLECTION / EXCHANGE_OUTBOUND을 구분한다.
6. SellerOrder는 주문 처리 상태를, Shipment는 실제 물류 이동과 송장 이력을 담당한다.
7. 기존 최초 배송 API response shape은 유지하되 데이터 원천은 ORIGINAL_OUTBOUND Shipment로 이전하고 legacy 컬럼은 migration/rollback snapshot으로만 유지한다.
8. ReturnRequest는 collectionShipment를 참조한다.
9. ExchangeRequest는 collectionShipment와 replacementShipment를 각각 참조한다.
10. 반품/교환 요청은 회수지와 재배송지 정보를 요청 당시 snapshot으로 보존한다.
11. 교환은 기본적으로 회수/검수 완료 후 재배송한다.
12. SellerOrder는 반품/교환 중에도 DELIVERED를 유지한다.
13. Product의 returnShippingFee/exchangeShippingFee를 주문 시 OrderItem에 snapshot한다.
14. 구매자 귀책 부분반품은 편도 반품비를 부담한다.
15. 구매자 귀책 SellerOrder 전체반품은 왕복배송비를 부담한다.
16. 무료배송 전체반품도 구매자 귀책이면 왕복배송비를 부담한다.
17. 판매자 귀책 반품/교환 배송비는 구매자 부담 0원이다.
18. 전체반품 원 배송비 환불 여부는 기존 Cancellation의 SellerOrder 전량 기준을 재사용한다.
19. 반품 환불은 기존 PaymentCancellation(PARTIAL) + PaymentGateway를 재사용한다.
20. 반품 요청/승인 시 재고를 복원하지 않는다.
21. 회수/검수 후 RESTOCKABLE 수량만 판매 가능 재고에 복원한다.
22. 교환은 동일 상품 내 동일 금액 Variant까지만 자동 지원한다.
23. 가격 차이가 있는 교환은 반품 후 재구매로 처리한다.
24. 구매자 귀책 교환 배송비는 별도 추가결제로 처리하며 원 주문 Payment를 변형하지 않는다.
25. 기존 취소/결제 lock order와 안정성 패턴을 최대한 재사용한다.
26. Shipment 도입 후에도 기존 결제/취소/주문 조회 API를 불필요하게 변경하지 않는다.
```

Shipment Domain / Repository, 기존 최초 배송 전환, 개발 DB backfill/검증까지 완료됐다.
다음 구현은 `OrderItem` 반품/교환 배송비 snapshot을 추가하는 Return 1 단계이며, 그 후 Return 도메인으로 진행한다.
