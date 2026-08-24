# Gift Market 주문 반품 / 교환 설계

> 최종 갱신: 2026-08-24
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
- 반품 증빙 이미지는 모든 사유에서 선택사항이며 0~5장을 허용한다.
- 증빙 이미지는 MinIO presigned URL로 직접 업로드하고 DB에는 objectKey와 sortOrder만 저장한다.
- `ReturnRequest 1:N ReturnRequestImage`로만 확장하며 이미지 유무는 상태 전이·환불·재고복원에 영향을 주지 않는다.
- Buyer/Seller 소유권 확인 후 만료되는 presigned GET URL을 응답하고 기존 반품은 `images=[]`로 조회한다.
- Exchange 증빙 이미지는 `ExchangeRequest 1:N ExchangeRequestImage`로 구현했으며, Return과 동일하게 0~5장 optional 검증, `exchanges/{userId}/` prefix, presigned 업로드/조회 Backend가 구현되어 있다.
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
│  ├─ ReturnRequest N (Backend 구현 완료)
│  └─ ExchangeRequest N (Exchange 1 foundation + Exchange 2 Buyer Backend 완료)
│
└─ SellerOrder B
   └─ ...
```

현재 코드에서 확인된 핵심 사항:

- 현재 구현 완료: Order / Payment / SellerOrder / OrderItem / OrderCancellation / Shipment / ReturnRequest / ReturnRequestItem / ReturnRequestImage, Return Backend와 Buyer/Seller Frontend
- 현재 검증 완료: 이미지 0~5장 optional 첨부를 포함한 실제 Return E2E
- 현재 Exchange 1 구현 완료: ExchangeRequest / ExchangeRequestItem / ExchangeRequestImage Entity와 Repository, OrderItem.exchangedQuantity, target snapshot·reservation 추적·Shipment 연결 foundation
- 현재 Exchange 2 구현 완료: Buyer 요청 생성/목록/상세 API, 기간·가격·Variant·신청 시 현재 재고 사전검사, Return/Exchange 양방향 수량 점유, pessimistic lock, clientRequestKey 멱등성, 이미지 Backend
- 현재 미구현: Seller 승인/거절, 실제 target 재고 reservation/release, PAYMENT_PENDING timeout, ExchangeShippingPayment, Exchange Shipment workflow, Buyer/Seller Frontend와 Exchange E2E

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
└─ outboundShipment 0..1
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

## 4. 현재 코드의 주문 snapshot 기준

현재 `OrderItem`에는 주문 당시 반품/교환 배송비 snapshot이 구현되어 있다.

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

기존 개발 DB 데이터의 backfill과 신규 주문 생성 시 snapshot 저장도 현재 코드와 SQL에 반영되어 있다.

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

## 6. 반품/교환 가능 기간

### 구매자 귀책

단순변심, 옵션 선택 착오 등 구매자 귀책은:

```text
deliveredAt + 7일 이내
```

신청 가능하게 한다.

교환 사유 기준으로 `CHANGE_OF_MIND`, `OPTION_MISTAKE` 등 BUYER 귀책은 `ORIGINAL_OUTBOUND.deliveredAt` 기준 7일 이내로 확정한다.

### 판매자 귀책

`DEFECTIVE`, `WRONG_ITEM`, `DAMAGED`, `DESCRIPTION_MISMATCH` 등 SELLER 귀책은 구매자 단순변심과 동일한 7일로 제한하지 않고 별도 법정/운영 기준을 적용한다. 아직 구체적인 장기 일수는 확정하지 않는다.

1차 구현에서는 Backend 정책을 별도 메서드로 분리하여 추후 운영/법무 정책 변경이 가능하게 한다.

최소 원칙:

- 구매자 귀책 7일 정책과 동일하게 하드코딩하지 않는다.
- `ORIGINAL_OUTBOUND Shipment.deliveredAt`이 없는 주문은 자동 반품/교환 신청을 허용하지 않는다. migration 안정화 기간에만 SellerOrder legacy 값 fallback을 고려한다.
- 관리자 CS 기능이 추가되면 예외 승인 경로를 둘 수 있게 한다.

`OTHER`는 판매자 승인 시 BUYER/SELLER 귀책을 확정한 뒤 해당 기간 기준을 적용한다. 귀책 확정 전 Frontend가 임의 기간으로 요청을 차단하지 않고 Backend가 최종 판단한다.

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

교환 가능 수량은 완료된 교환 수량을 별도로 반영한다.

```text
availableExchangeQuantity
= quantity
- canceledQuantity
- returnedQuantity
- exchangedQuantity (`OrderItem`의 COMPLETED 교환 누계 source of truth)
- activeReturnQuantity
- activeExchangeQuantity
```

Return과 Exchange가 동일 주문수량을 중복 점유하지 못하도록 요청 생성 transaction에서 SellerOrder와 정렬된 OrderItem을 pessimistic lock으로 잠근 후 최종 수량을 다시 계산한다. 기존 주문/취소/Return과 같은 정렬 lock order를 유지한다.

Return의 다음 상태는 활성 수량으로 본다.

```text
REQUESTED
APPROVED
COLLECTING
RECEIVED
INSPECTED
REFUNDING
```

Exchange의 활성 수량 상태는 `REQUESTED / APPROVED / PAYMENT_PENDING / COLLECTING / RECEIVED / INSPECTED / RESHIPPING`이다. `COMPLETED` 수량은 별도 중복 필드 없이 `OrderItem.exchangedQuantity`에 반영하고, `REJECTED / CANCELED / FAILED`는 점유량에서 제외한다.

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

현재 `PaymentCancellation`은 PARTIAL 업무 source를 다음 nullable FK로 구분한다.

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

### Transaction B — PG 성공 확정

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
→ ReturnRequest REFUNDING 유지
→ commit
```

### Transaction C — Return completion

```text
Payment → Order → SellerOrder → ReturnRequest
→ PaymentCancellation(금액이 0보다 큰 경우)
→ OrderItem → Product/Variant 순서 잠금
→ PG 환불 성공 또는 0원 환불 검증
→ returnedQuantity 증가
→ RESTOCKABLE만 재고 복원 및 restockedQuantity 기록
→ ReturnRequest COMPLETED
→ commit
```

PG 성공 확정과 내부 완료 후처리를 분리한다. Transaction C가 실패하면 `PaymentCancellation SUCCEEDED + ReturnRequest REFUNDING`을 남기고 completion recovery가 새 PG 호출 없이 멱등 재시도한다. `refundAmount == 0`은 PaymentCancellation 없이 `REFUNDING`에서 같은 completion을 수행한다.

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
- 성공 확인 시 PaymentCancellation을 먼저 SUCCEEDED로 확정하고 별도 completion을 실행
- 성공 확인 전 `returnedQuantity`/재고를 최종 반영하지 않음
- `SUCCEEDED + REFUNDING`은 completion recovery 대상

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

- `DELIVERED` SellerOrder이며 `ORIGINAL_OUTBOUND.deliveredAt`이 확인된 주문
- 부분 수량 교환
- 원 OrderItem과 동일 Product 안의 동일 Variant 또는 다른 Variant
- 다른 Product로 교환 불가. 다른 상품 요구는 반품 후 재구매로 안내
- target 현재 판매단가가 원 주문 `OrderItem.unitPrice`와 정확히 같은 경우에만 자동 교환
- 구매자 신청 시 target Product/Variant의 현재 재고가 교환 요청 수량 이상인 경우에만 요청 생성

`OrderItem.additionalPrice`와 `OrderItem.unitPrice`는 주문 당시 snapshot이다. 교환 시 target 현재 판매단가는 `Product.price + targetVariant.additionalPrice`로 계산하고 원 주문 `OrderItem.unitPrice`와 비교한다. Product 또는 Variant 가격이 주문 후 바뀌어 동일 옵션군이라도 동일 금액 조건을 만족하지 못하면 자동 교환하지 않는다. 가격 차액의 추가결제, 가격 차액의 부분환불, 주문금액 수정은 지원하지 않고 반품 후 재구매로 안내한다. 이는 교환을 주문금액 변경 기능으로 사용하지 않기 위한 정책이다.

교환 요청 시 구매자의 회수지와 교환품 재배송지를 각각 snapshot으로 저장한다.
기본값은 원 주문 배송지이지만 구매자가 허용 범위 내에서 별도 주소를 선택할 수 있게 확장 가능하도록 DTO/Entity를 설계한다.
배송지 원본 Address Entity FK만 저장하지 않고 요청 당시 문자열 snapshot을 보존한다.

교환 증빙 이미지는 `ExchangeRequest 1:N ExchangeRequestImage`로 둔다. Return과 동일하게 모든 사유에서 0~5장 optional이며 MinIO presigned PUT으로 직접 업로드하고 DB에는 objectKey와 순서만 저장한다. 조회 권한 확인 후 presigned GET URL을 응답하며 URL 자체는 저장하지 않는다. 이번 Exchange 구현에서 `ReturnRequestImage`를 공용 `ClaimImage`로 리팩토링하지 않는다.

교환 사유와 귀책은 ReturnReason/Responsibility와 같은 개념을 별도 Exchange 도메인에 적용한다. 귀책은 `BUYER / SELLER`이며 `OTHER`는 판매자 승인 시 확정한다. 이미지 유무는 귀책 판정이나 승인 가능 조건이 아니다.

### 19.1 신청 단계와 승인 단계의 책임

교환 신청 단계는 buyer ownership, DELIVERED/기간, target Product·Variant, 현재 가격 동일성, 현재 재고 사전검사, Return/Exchange 교차 수량, 이미지, clientRequestKey 멱등성을 검증한다.

신청 시 **현재 target stock >= 교환 요청 수량** 확인은 이미 품절되거나 수량이 부족한 옵션을 선택시키지 않기 위한 UX 사전검사다. 신청 당시 재고가 있었다는 사실은 판매자 승인 시점까지의 재고를 보장하지 않는다.

판매자 승인 transaction은 seller ownership과 `REQUESTED` 상태, target Product/Variant 상태와 실제 재고를 다시 검증하고 Product/Variant를 pessimistic lock으로 잠근다. 이후 기존 reservation bookkeeping 불변식을 확인하고 실제 stock 차감과 `ExchangeRequestItem.reserveTargetStock(...)`를 같은 transaction에서 수행한다. BUYER 귀책은 reservation 이후 `PAYMENT_PENDING`, SELLER 귀책은 reservation 이후 `COLLECTING`으로 전이한다. 신청 단계 검사 결과를 신뢰해 승인 단계 검증을 생략해서는 안 된다.

## 20. 교환 상태 머신

```text
REQUESTED
→ 판매자 승인 transaction에서 target 재고 재검증/lock/reservation

BUYER 귀책:
REQUESTED
→ target reservation
→ PAYMENT_PENDING
→ 교환배송비 결제 성공
→ COLLECTING

SELLER 귀책:
REQUESTED
→ target reservation
→ COLLECTING

공통:
COLLECTING
→ RECEIVED
→ INSPECTED
→ RESHIPPING
→ COMPLETED
```

분기:

```text
REQUESTED → REJECTED
PAYMENT_PENDING 24시간 미결제 → CANCELED + target reservation release
복구 불가능 오류 → FAILED
```

`APPROVED`는 현재 Entity 상태로 유지되지만 승인 workflow에서는 재고 lock·reservation과 다음 귀책별 상태 전이를 하나의 업무 transaction으로 묶는다. 향후 기타 취소 허용 범위는 결제/Shipment 구현 단계에서 실제 side effect를 함께 검토해 확정한다. 예약 또는 결제 이후 취소는 보상 transaction 없이 단순 상태 변경만 해서는 안 된다.

권장 enum:

```text
ExchangeRequestStatus
- REQUESTED
- APPROVED
- PAYMENT_PENDING
- COLLECTING
- RECEIVED
- INSPECTED
- RESHIPPING
- COMPLETED
- REJECTED
- CANCELED
- FAILED
```

`PAYMENT_PENDING`의 교환배송비 결제 유효시간은 24시간이다. 이 상태에는 승인 transaction에서 target reservation이 이미 잡혀 있다. 24시간 안에 결제가 완료되지 않으면 `ExchangeRequest`를 `CANCELED`로 종료하고 같은 업무 결과로 reservation을 release하여 실제 Product/Variant stock과 `releasedQuantity`를 정확히 한 번 복원한다. scheduler/retry가 중복 실행되어도 stock을 두 번 복원하면 안 된다. 24시간 값은 정책 상수 또는 설정 한 곳에서 관리한다.

단순 미결제 만료와 명시적인 결제 실패는 구분한다. 명시적 결제 실패 한 번만으로 ExchangeRequest를 즉시 `FAILED`로 만들지 않는다. 기존 Payment/PG 원칙처럼 재시도 가능 상태와 결과 불명 상태를 고려하며 구체적인 `ExchangeShippingPayment` 상태 머신은 후속 결제 단계에서 확정한다.

`FAILED`는 시스템·결제·데이터 정합성 문제로 정상 workflow를 계속할 수 없고 자동 recovery도 불가능한 경우로 제한한다. 다음은 `FAILED`가 아니다.

- 24시간 동안 교환배송비 미결제: `CANCELED`
- 판매자 거절: `REJECTED`
- target 재고 부족: 승인 차단, `REQUESTED` 유지
- `NON_RESTOCKABLE` 검수 결과: 원 상품 재고만 복원하지 않고 교환은 계속 진행 가능
- 회수 지연: 현재 상태 유지

특히 판매자 귀책 불량·파손 상품은 `NON_RESTOCKABLE`이어도 교환품 재배송을 정상 진행할 수 있다. `FAILED`를 일반 업무 예외 상태로 남발하지 않는다.

## 21. 교환 재고 정책

target 재고는 판매 재고의 단순 차감이 아니라 **교환용 예약(reservation)** 으로 취급한다. 예약된 수량은 일반 판매 가능 재고에서 제외하고 `EXCHANGE_OUTBOUND` 처리 시 실제 교환 출고로 소비한다.

```text
REQUESTED
→ 신청 시 현재 target 재고 사전검사만 수행
→ 아직 target 재고 예약 안 함

판매자 승인 공통:
→ target Product/Variant 상태 재검증
→ 교환 대상 Product/Variant lock
→ 현재 stock >= exchange quantity 재검증
→ 실제 target stock 차감
→ reservedQuantity 반영

BUYER:
→ PAYMENT_PENDING
→ 교환배송비 결제 성공
→ COLLECTING

SELLER:
→ COLLECTING
```

신청 시 재고 확인은 UX 사전검사이고 재고 보장이 아니다. 신청 당시 재고가 있었더라도 승인 시 부족할 수 있으므로 승인 transaction에서 반드시 다시 잠그고 검증한다. 승인 시 재고가 부족하면 stock 음수, 승인 성공, `PAYMENT_PENDING` 진입, reservation 기록을 모두 금지하고 `REQUESTED`를 유지한다. 자동 `REJECTED`로 바꾸지 않으며 판매자가 재고 확보 후 다시 승인할 수 있게 한다.

현재 `ExchangeRequestItem`의 누적 bookkeeping 의미는 다음과 같다.

```text
reservedQuantity = 승인 이후 교환용으로 확보한 누적 수량
releasedQuantity = 취소/만료/실패로 일반 판매재고에 복원한 누적 수량
consumedQuantity = EXCHANGE_OUTBOUND 실제 출고에 사용한 누적 수량

0 <= releasedQuantity + consumedQuantity <= reservedQuantity <= exchange quantity
effectiveReservedQuantity = reservedQuantity - releasedQuantity - consumedQuantity
```

실제 Product/Variant stock 차감은 Exchange 3에서 `reserveTargetStock(...)` bookkeeping과 같은 transaction에서 수행한다. 누적 수량과 effective reservation을 멱등 장벽으로 사용해 중복 예약·중복 복원·중복 출고 소비를 막는다. 대규모 공용 Inventory reservation 시스템은 1차 범위가 아니다.

이유:

- 회수/검수 완료까지 기다렸다가 재고를 잡으면 교환품 품절 가능성이 높다.
- 예약 이후 교환이 취소되거나 복구 불가능 오류로 실패하면 예약을 정확히 1회 해제해야 한다.

원 상품은 검수 결과 `RESTOCKABLE`일 때만 판매 가능 재고에 복원한다.

target 예약·예약 해제·출고 소비와 원 상품 복원은 서로 다른 재고 이벤트로 취급한다.

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
- `amount`, `status`, `merchantPaymentId`, `providerPaymentKey`
- `requestedAt`, `paidAt`, `failedAt`
- 결제 유효시간 계산을 위한 `expiresAt` 또는 동등한 정책 시각
- 고정 idempotency key와 reconciliation
- 결제 결과 불명 상태와 중복 승인 방지 등 기존 Payment 안정성 원칙 재사용
- `PaymentGateway` 추상화 재사용
- 원 주문 Payment 금액/상태를 변경하지 않음
- 기존 `PaymentCancellation`은 교환 배송비 추가결제에 재사용하지 않음

구매자 귀책 교환은 판매자 승인 transaction에서 target 재고를 먼저 예약한 뒤 `PAYMENT_PENDING`으로 전이한다. 24시간 이내 배송비 결제가 성공하면 이미 확보된 reservation을 유지한 채 `COLLECTING`으로 진행한다. 미결제 만료는 `CANCELED`이며 같은 업무 처리에서 reservation을 정확히 한 번 release한다.

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
→ EXCHANGE_OUTBOUND Shipment DELIVERED
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

판매자
GET    /api/seller/orders/returns
GET    /api/seller/orders/returns/{returnRequestId}
PATCH  /api/seller/orders/returns/{returnRequestId}/approve
PATCH  /api/seller/orders/returns/{returnRequestId}/reject
PATCH  /api/seller/orders/returns/{returnRequestId}/collect
PATCH  /api/seller/orders/returns/{returnRequestId}/receive
PATCH  /api/seller/orders/returns/{returnRequestId}/inspect

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
- target Variant 선택과 동일 Product 검증
- target Variant 재고 0은 품절 표시 및 선택 불가
- 현재 target 재고가 교환 요청 수량보다 적으면 해당 수량으로 신청 불가
- 현재 target 가격과 원 주문 unitPrice의 일치 여부 안내
- 회수지와 재배송지 snapshot 입력
- 증빙 이미지 0~5장 optional
- BUYER 귀책 승인 후 교환 배송비 추가결제
- 요청 상태, 회수 Shipment, 재배송 Shipment 조회

가격과 배송비는 안내용이며 Backend 응답과 검증을 최종값으로 사용한다.
Frontend에 표시된 재고는 실시간 보장을 의미하지 않으며 신청과 승인 단계의 Backend 검증이 최종 권위다. 이 UI는 아직 미구현이다.

### 판매자센터

- 기존 Seller Center 구조에 `교환 관리` 별도 메뉴
- 교환 요청 목록/상세
- 승인/거절
- OTHER 귀책 확정
- target 재고 확인 및 승인 시 선점
- 회수 진행
- 입고 확인
- 상품별 검수 결과
- 교환 회수 송장 등록
- 교환품 재배송 송장 등록
- 완료 조회

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

Shipment 기반 배송 구조, Return 전체, Exchange 1 foundation과 Exchange 2 Buyer 요청 Backend까지 완료됐다.

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

### Return 1 — 주문 snapshot 준비 (완료)

- `OrderItem.returnShippingFee`
- `OrderItem.exchangeShippingFee`
- 주문 생성 snapshot
- 기존 데이터 backfill
- 테스트

### Return 2 — Domain (완료)

- ReturnReasonType
- ReturnResponsibility
- ReturnRequestStatus
- ReturnInspectionResult
- ReturnRequest
- ReturnRequestItem
- `OrderItem.returnedQuantity`

### Return 3 — Repository / lock / 요청 생성 (완료)

- 구매자 ownership
- SellerOrder DELIVERED 검증
- 기간 검증
- 요청 가능 수량
- clientRequestKey 멱등성

### Return 4 — 판매자 workflow (완료)

- 승인
- 거절
- 회수
- 입고
- 검수

### Return 5 — 환불 계산 (완료)

- 상품금액
- 원 배송비
- 구매자/판매자 귀책
- 편도/왕복 반품비
- Payment 환불 가능 잔액

### Return 6 — PG 환불 (완료)

- PaymentCancellation ReturnRequest 연결
- PaymentGateway 재사용
- idempotency
- timeout/5xx
- reconciliation

### Return 7 — 재고/완료 (완료)

- RESTOCKABLE만 복원
- returnedQuantity
- 중복복원 방지

### Return Backend 테스트 (완료)

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

### Return Frontend (완료)

- 구매자 주문상세
- 반품 신청
- 반품 진행상태
- 판매자 반품관리
- CSS

### Exchange 1 — Domain foundation (완료)

- ExchangeRequest / ExchangeRequestItem / ExchangeRequestImage
- OrderItem.exchangedQuantity
- target snapshot과 reserved/released/consumed bookkeeping
- Shipment nullable 관계와 foundation DDL

### Exchange 2 — Buyer 요청 Backend (완료)

- 구매자 요청 생성·조회 Service/API
- 기간·가격·가용 수량·멱등성 검증
- 신청 시 target 현재 재고 사전검사
- Return/Exchange 양방향 수량 점유
- Exchange 이미지 0~5장 Backend

### Exchange 3 이후 (미구현)

- 판매자 승인/거절과 OTHER 귀책 확정
- 승인 transaction의 target 재고 재검증/lock/reservation
- PAYMENT_PENDING 24시간 만료와 reservation release
- 구매자 귀책 배송비 추가결제
- 회수/입고/검수와 재배송 Shipment 생성
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
- 교환 target 재고 예약/해제/출고 소비
- 상품상세/정책 페이지에 교환 가능 기간 고지
- 상품상세/정책 페이지에 교환배송비와 추가결제 24시간 기한 고지
- 가격 차이 Variant 및 다른 Product 교환 불가 고지
- 회수→입고→검수→재배송 교환 절차 고지
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
9. ExchangeRequest는 collectionShipment와 outboundShipment를 각각 참조한다.
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
22. 교환은 동일 Product 내 동일 Variant 또는 현재 판매단가가 원 주문 unitPrice와 같은 Variant까지만 자동 지원한다.
23. 가격 차이가 있는 교환은 반품 후 재구매로 처리한다.
24. 구매자 귀책 교환 배송비는 별도 추가결제로 처리하며 원 주문 Payment를 변형하지 않는다.
25. 기존 취소/결제 lock order와 안정성 패턴을 최대한 재사용한다.
26. Shipment 도입 후에도 기존 결제/취소/주문 조회 API를 불필요하게 변경하지 않는다.
27. 교환 증빙 이미지는 귀책과 무관하게 0~5장 optional이며 ExchangeRequestImage에 objectKey만 저장한다.
28. 구매자 귀책 교환은 ExchangeShippingPayment 결제 완료 후 회수를 시작하고 PaymentCancellation을 재사용하지 않는다.
29. 신청 시 현재 target 재고 확인은 UX 사전검사이며 승인 시점까지 재고를 보장하지 않는다.
30. target 재고는 귀책과 무관하게 판매자 승인 transaction에서 재검증·lock 후 교환용으로 예약한다. 취소/만료/실패 시 정확히 1회 해제하고 재배송 출고 시 소비한다.
31. BUYER 귀책은 reservation 후 PAYMENT_PENDING으로 전이하고, SELLER 귀책은 reservation 후 COLLECTING으로 전이한다.
32. PAYMENT_PENDING은 24시간 후 미결제 시 CANCELED와 reservation release를 함께 완료하며 FAILED는 자동 복구 불가능한 시스템·정합성 오류로 제한한다.
```

Shipment Domain / Repository, 기존 최초 배송 전환, 개발 DB backfill/검증, Return Backend 1~7, Buyer/Seller Return Frontend, Return 증빙 이미지와 실제 Return E2E까지 완료됐다. 전체 Backend 자동 테스트 기준 기록은 279개 성공이다.
Exchange 1 도메인 foundation과 Exchange 2 Buyer 요청 생성/조회 Backend, 신청 시 target 현재 재고 사전검사, 양방향 수량 점유와 이미지 Backend까지 완료됐다. 실제 reservation/release, Seller workflow, PAYMENT_PENDING timeout, ExchangeShippingPayment, Shipment workflow, Buyer/Seller Frontend는 아직 미구현이고 실제 교환 E2E도 미검증이다.
