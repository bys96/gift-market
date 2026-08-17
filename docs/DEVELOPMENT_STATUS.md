# Gift Market 개발 현황

## Cancellation 4-3A단계: 부분환불 Payment 도메인 기반

- `PaymentStatus.PARTIALLY_CANCELED`와 환불 가능 상태 predicate를 추가해 일부 환불 후에도 추가 취소가 가능한 상태를 표현한다.
- 기존 `PaymentCancellation` factory/API는 FULL 전체취소로 유지하고, PARTIAL 거래는 nullable `OrderCancellation` 연결과 거래별 amount·PG 멱등키를 별도로 기록한다.
- 동일 OrderCancellation에는 하나의 PaymentCancellation만 연결되도록 unique 제약과 Payment 잠금 기반 준비 서비스를 추가했다.
- SUCCEEDED 금액과 REQUESTED 예약금액을 Repository에서 각각 집계해 원 결제금액에서 차감한 `PaymentRefundBalance`를 계산한다.
- Toss `PARTIAL_CANCELED`를 provider-neutral `PARTIALLY_CANCELED`로 매핑하며 전체 CANCELED로 처리하지 않는다.
- 아직 Toss 부분취소 HTTP 호출, cancelAmount 전송, completion 연결, 부분환불 reconciliation/webhook은 구현하지 않았다.

### 다음 단계: Cancellation 4-3B — 실제 Toss 부분환불 실행 연결

- Transaction A에서 환불금 계산·잔액 검증·PaymentCancellation 준비를 확정하고 동일 PG 멱등키로 Toss `cancelAmount`를 호출한다.
- Transaction B에서 provider 거래·잔액을 검증한 뒤 Payment 상태와 PaymentCancellation을 확정하고 4-2 completion을 호출한다.
- timeout/5xx 응답 유실은 단건 조회, webhook 및 reconciliation으로 동일 부분환불 거래를 복구한다.

## Cancellation 4-1단계: 부분취소 환불 금액 계산 기반

- `OrderCancellationRefundCalculator`가 주문 당시 `OrderItem.unitPrice`, 수량, 배송비 snapshot만 사용해 상품·배송비·총 환불 예정액을 계산한다.
- SellerOrder에 남는 상품 수량이 없을 때만 해당 SellerOrder의 OrderItem별 원 배송비 합계를 환불하며, 다른 REQUESTED/PROCESSING 요청 수량은 확정 취소로 간주하지 않는다.
- 계산 결과는 item별 요청 수량·단가·환불액·취소 후 잔여수량을 포함하는 immutable DTO로 반환하고 DB에는 저장하지 않는다.
- Payment → Order → SellerOrder → OrderCancellation → OrderItem 비관적 잠금 순서와 상태 재검증으로 향후 PG 호출 직전에도 같은 계산기를 재사용할 수 있게 했다.
- 이번 단계에서는 Toss 부분취소, PaymentCancellation 생성, 상태 전이, `canceledQuantity` 증가 및 부분 재고 복원을 수행하지 않는다.

## Cancellation 4-2단계: 부분취소 확정 transaction 기반

- PG 부분환불 성공 이후 사용할 내부 `OrderCancellationCompletionService`를 추가했으며 외부 Controller나 기존 구매자/판매자 API에는 연결하지 않았다.
- PROCESSING 취소요청의 DB 수량만 사용해 `OrderItem.canceledQuantity`를 증가시키고 동일 수량의 Product/Variant 재고를 복원한다.
- 기존 전체 재고복원과 Product/Variant 잠금 및 Product 총재고 동기화 primitive를 공유하며, Payment부터 OrderItem까지 기존 잠금 순서를 유지한다.
- 모든 SellerOrder 상품의 잔여수량이 0일 때만 SellerOrder를 CANCELLED로 전환하고 Order/Payment 상태는 변경하지 않는다.
- COMPLETED 상태를 멱등성 장벽으로 사용해 중복 확정 시 재고와 취소수량이 다시 반영되지 않게 했다.
- 실제 Toss 부분취소와 연결되지 않았으므로 현재 사용자 요청만으로 completion이 실행되지는 않는다.

### 다음 단계: Cancellation 4-3 — Toss 부분환불 실행 연결

- Toss `cancelAmount`, PaymentCancellation 부분환불 거래 기록과 PG 멱등성 키를 구현한다.
- Payment 부분취소 상태 및 누적 환불 잔액을 추가하고 Transaction A → 외부 PG → Transaction B completion 흐름을 연결한다.
- timeout/5xx/webhook/reconciliation에서 동일 부분환불 거래를 안전하게 복구한다.

## Cancellation 1~3단계: 상품/수량 취소 요청 및 판매자 승인

- `OrderCancellation`과 `OrderCancellationItem`으로 PG 거래 기록인 기존 `PaymentCancellation`과 주문 취소 업무를 분리했다.
- `OrderItem.quantity`는 유지하고 `canceledQuantity`로 확정 취소 누계를 관리하며, REQUESTED/PROCESSING 요청 수량까지 차감해 중복 수량 점유를 막는다.
- 구매자는 `POST /api/orders/{orderId}/cancellations`로 PAID/PREPARING SellerOrder의 상품과 수량을 취소 요청할 수 있다. 소유권·Order/SellerOrder/OrderItem 관계와 UUID 요청 키 멱등성을 Backend에서 검증한다.
- 요청 당시 SellerOrder가 PREPARING이면 `requiresSellerApproval=true`, PAID이면 `false`로 저장해 판매자 승인형과 향후 즉시 환불형을 구분한다.
- 판매자는 `/api/seller/orders/cancellations`에서 자기 승인형 요청만 pageable 조회하고 상세를 확인할 수 있다.
- PREPARING 승인형 REQUESTED는 판매자 승인 시 PROCESSING, 거절 시 REJECTED로 전환하며 처리 시각과 거절 사유를 기록한다. PAID 즉시 환불형은 판매자 API에서 조회·처리할 수 없다.
- REQUESTED 또는 PROCESSING 요청이 존재하는 PREPARING SellerOrder는 즉시형/승인형 구분 없이 배송 시작을 차단한다. 거절되거나 명확히 실패한 요청은 배송을 막지 않는다.
- 승인/거절과 배송 시작은 Payment → Order → SellerOrder → OrderCancellation 잠금 순서 및 SellerOrder 잠금 재검증으로 경합을 방어한다.
- 이번 단계에서는 Toss 부분취소, 환불금 계산, PaymentCancellation 생성, `canceledQuantity` 증가, 부분 재고 복원을 수행하지 않는다.
- `docs/sql/order-cancellation-stage3-approval-flow.sql`에 승인형 구분 컬럼의 수동 DDL과 기존 row 점검 SQL을 기록했다. 기존 row는 현재 SellerOrder 상태만으로 자동 추측하지 않는다.

### 다음 단계: Cancellation 4단계 — 부분환불 실행 기반

- 환불 금액 및 배송비 환불 금액을 OrderItem snapshot 기준으로 계산한다.
- PAID 즉시형 요청과 판매자 승인 완료 PROCESSING 요청을 공통 부분환불 실행 흐름으로 연결한다.
- Payment row lock과 PG 멱등키로 Toss 부분취소 시작을 직렬화하고 결과 불명 복구 기반을 마련한다.
- PG 취소 성공 확정 후에만 canceledQuantity 증가와 요청 수량만큼의 재고 복원을 같은 transaction에서 처리한다.

## SellerOrder 1~5단계: 판매자별 주문 처리 및 구매자 배송조회

- 전체 결제 단위인 `Order`/`Payment`는 그대로 유지하고, 같은 주문 안의 판매자별 처리 단위인 `SellerOrder`를 추가했다.
- `SellerOrder`는 `(order_id, seller_id)` 조합을 유일하게 보장하며 `PENDING_PAYMENT`, `PAID`, `PREPARING`, `SHIPPED`, `DELIVERED`, `CANCELLED` 상태를 사용한다.
- 배송사/운송장/상품준비·발송·배송완료 시각은 향후 배송 기능 연결을 위한 nullable 필드로만 준비했으며 `Shipment`와 배송 API/UI는 아직 없다.
- 기존 OrderItem 20건의 backfill과 정합성 검증을 완료했고 개발 DB의 `order_items.seller_order_id`를 `NOT NULL`로 전환했다.
- 신규 장바구니/바로구매 prepare transaction에서 주문 당시 `OrderItem.seller` 기준으로 판매자별 `SellerOrder PENDING_PAYMENT`를 한 건씩 만들고 모든 OrderItem을 생성 시점부터 연결한다.
- 동일 `clientOrderRequestKey` 재요청은 기존 Order/Payment 준비 결과를 반환하므로 SellerOrder도 중복 생성하지 않는다.
- Payment가 PAID로 확정되는 공통 transaction에서 SellerOrder를 `PAID`로 전환한다. 명확한 실패, READY 만료, 결제 전 내부 취소, PAID 전체 취소 시에는 재고·Order 처리와 같은 transaction에서 `CANCELLED`로 전환한다.
- CONFIRMING/CANCELING처럼 PG 결과가 불명확한 동안에는 SellerOrder를 종료 상태로 바꾸지 않는다.
- `docs/sql/seller-order-stage1-backfill.sql`에 `order_items.seller_id` 기준의 수동 backfill, 정합성 검증, 검증 완료 후 NOT NULL 전환 SQL을 준비했다. SQL은 자동 실행하지 않는다.
- 판매자는 `GET /api/seller/orders`에서 결제 완료 이후 자기 SellerOrder만 상태·주문번호·상품명으로 필터링해 pageable 조회할 수 있다. `PENDING_PAYMENT`는 노출하지 않는다.
- `GET /api/seller/orders/{sellerOrderId}`는 해당 판매자의 OrderItem과 출고에 필요한 배송지 snapshot만 제공한다.
- 배송 처리 API는 `PAID → PREPARING → SHIPPED → DELIVERED` 순서만 허용하며 상품준비, 배송사/운송장 등록, 수동 배송완료 시각을 SellerOrder에 기록한다.
- 배송 상태 변경은 Order를 먼저 잠근 뒤 SellerOrder를 잠가 Payment 전체 취소와 경합해도 취소된 주문이 배송 상태로 덮이지 않게 한다. Order/Payment 상태는 배송 처리로 변경하지 않는다.
- SellerOrder 1건당 배송 1건·송장 1개 정책이며 `Shipment`/분리배송/다중 송장은 아직 구현하지 않았다.
- 판매자센터 `/seller/orders`에서 자기 주문만 상태별로 필터링하고 주문번호·상품명 검색 및 서버 pagination으로 조회한다. Desktop은 표, Mobile은 카드형 행으로 표시한다.
- `/seller/orders/[sellerOrderId]`에서 해당 판매자의 상품 snapshot, 배송지, 배송사·운송장과 처리 시각을 확인하고 `PAID → PREPARING → SHIPPED → DELIVERED` 작업을 수행한다.
- 배송 처리 중 중복 클릭을 막고, 구매자 취소 등 외부 상태 변경으로 API가 실패하면 상세를 재조회해 Backend 최신 상태를 반영한다. `CANCELLED`에는 처리 버튼을 노출하지 않는다.
- 구매자 주문 목록 응답은 기존 `Order.status`를 유지하면서 DB에 저장하지 않는 파생 `deliveryStatus`를 추가한다. Order 결제/종료 상태를 우선하고 PAID 주문은 SellerOrder 상태를 집계해 결제완료·상품준비중·배송중·배송완료로 표시한다.
- 구매자 주문 목록은 Order별 추가 조회 대신 현재 목록의 Order ID 전체로 OrderItem과 SellerOrder를 각각 한 번씩 일괄 조회한다.
- 구매자 주문 상세 응답은 기존 `items` 호환 필드를 유지하면서 `sellerOrders`에 판매자명, 배송 상태, 배송사·운송장, 처리 시각과 해당 판매자의 상품만 그룹화해 제공한다.
- `/my/orders`는 대표 배송 상태를 표시하고 `/my/orders/[orderId]`는 판매자별 상품·배송 묶음을 표시한다. 전체 주문 취소는 기존 Order 단위 API와 조건을 그대로 사용한다.

## Payment 5-4: PAID 결제 전체 취소

- 기존 `PATCH /api/orders/{orderId}/cancel`을 유지하고 취소 사유와 클라이언트 취소 요청 키를 받는다.
- `PAID` 결제는 `CANCELING`으로 먼저 전환한 뒤 DB transaction 밖에서 Toss 전체 취소 API를 호출한다.
- Toss 응답의 결제 식별정보, 원 결제금액, 통화, `CANCELED`, 취소 가능 잔액 `0`을 모두 확인한 경우에만 `Payment CANCELED` / `Order CANCELLED`로 확정한다.
- 취소 확정 transaction에서 기존 `OrderInventoryService`로 재고를 정확히 한 번 복원하며 CartItem은 재생성하지 않는다.
- timeout/5xx 등 결과 불명은 `CANCELING`을 유지하고 재고를 복원하지 않는다. 사용자 재시도와 `PAYMENT_STATUS_CHANGED` webhook의 Toss 단건 조회로 복구한다.
- `PaymentCancellation`에 요청 키, PG 멱등 키, 금액, 사유, 처리 상태와 정제된 결과만 저장한다. 부분취소/부분환불은 아직 지원하지 않는다.

## Payment 5-5: 장기 CANCELING 자동 reconciliation

- `PaymentCancellation REQUESTED`, `Payment CANCELING`, `Order PAID`이며 취소 요청 후 설정된 지연시간이 지난 건만 오래된 순서로 제한 조회한다.
- 후보별로 Payment → Order → PaymentCancellation 순서로 짧게 잠그고 상태를 재확인한 뒤 transaction 밖에서 PG 단건 조회를 수행한다.
- Toss 조회가 전체 `CANCELED`이고 `balanceAmount=0`이면 5-4의 공통 취소 완료 transaction으로 Payment/Order/취소 이력과 재고를 한 번만 확정한다.
- Toss 조회가 `DONE`이면 새 취소 이력이나 멱등키를 만들지 않고 저장된 동일 취소 요청과 PG 멱등키로만 취소를 재시도한다.
- Toss 멱등키 공식 유효기간인 15일이 지난 요청은 새 키로 자동 재시도하지 않고 장기 체류 warning을 남긴다.
- timeout, 5xx, 알 수 없는 상태, `PARTIAL_CANCELED`는 `CANCELING`과 재고를 그대로 유지한다. 명확한 취소 거절만 기존 5-4 실패 전이로 `Payment PAID`, 취소 이력 `FAILED` 처리한다.
- READY 만료, CONFIRMING reconciliation, CANCELING reconciliation scheduler는 각각 자신의 상태만 처리한다.

## 운영 전 필수 검증 TODO

아래 항목은 localhost 자동화 테스트로 완료 처리하지 않는다. 정식 사용 및 운영 배포 전에 공개 테스트 환경에서 반드시 수행한다.

- [ ] 공개 HTTPS dev/staging URL 준비
- [ ] Toss `PAYMENT_STATUS_CHANGED` webhook 등록
- [ ] 실제 Toss 테스트 결제
- [ ] webhook HTTP 200 수신 확인
- [ ] `payment_webhook_events`가 `PROCESSED`인지 확인
- [ ] 동일 webhook 재전송 멱등성 확인
- [ ] Payment / Order `PAID` 확인
- [ ] 전체 결제 취소 테스트
- [ ] 취소 webhook 확인
- [ ] Payment `CANCELED` / Order `CANCELLED` 확인
- [ ] 재고가 정확히 한 번 복원되는지 확인
- [ ] Cart 정합성 확인
- [ ] timeout / 재시도 시나리오를 가능한 범위에서 확인
- [ ] 운영 키 전환 전에 Payment 전체 회귀 테스트

> 기준: 2026-08-16 실제 소스 및 Payment 1~4단계 브라우저 테스트 결과
>
> 문서와 코드가 다르면 실제 코드를 현재 상태의 기준으로 사용한다. Payment의
> 상세 설계 배경은 `docs/PAYMENT_ARCHITECTURE_DESIGN.md`를 참고한다.

## 1. 기술 및 프로젝트 기준

### Backend

- Java 21, Spring Boot 4.1.0
- Spring Security, OAuth2/OIDC, JWT
- Spring Data JPA, MySQL
- MinIO, jsoup, Gradle

### Frontend

- Next.js 16.2.11 App Router, React 19.2.4, TypeScript
- Zustand, TanStack Query dependency, Tiptap
- 일반 CSS 기반 UI
- Tailwind dependency/import는 존재하지만 새 UI에서는 utility class를 사용하지 않음

주문·결제 금액과 재고의 최종 권위는 Backend에 있다. Frontend에서 계산하거나
PG redirect로 전달된 금액만으로 주문 또는 결제를 확정하지 않는다.

## 2. 현재 Backend domain

```text
com.giftmarket
├─ address
├─ admin
├─ auth
├─ cart
├─ global
├─ order
├─ payment
├─ product
├─ seller
└─ user
```

주요 구현 기능:

- 구매자 상품 목록/상세, 옵션/Variant, 재고 및 판매 상태
- 판매자 상품 등록·수정, Seller Center, 판매자 신청/관리자 승인
- JWT Access Token + Refresh Token cookie, Google/Kakao OAuth2/OIDC
- MinIO presigned URL 기반 storage
- 장바구니 등록·수량 변경·삭제·구매 가능 여부 조회
- 저장 배송지 CRUD, 기본 배송지 정책, 주문서 배송지 연동
- 장바구니 선택 주문과 상품 상세 바로구매
- Payment 준비·Toss 테스트 결제·Backend 승인·상태 조회

## 3. 주문 및 배송지

### 주문 API

```text
POST  /api/orders                 # 장바구니 주문/결제 준비
POST  /api/orders/direct          # 바로구매 주문/결제 준비
GET   /api/orders
GET   /api/orders/{orderId}
PATCH /api/orders/{orderId}/cancel
```

공통 정합성:

- 사용자 및 CartItem 소유권 검증
- Product/Variant pessimistic row lock
- 판매 상태, 옵션 상태, 수량, 재고를 Backend에서 재검증
- 가격과 배송비를 Backend 현재 값으로 계산
- `Order`/`OrderItem`에 상품·옵션·가격·배송지 snapshot 저장
- 장바구니 주문과 바로구매의 사용자 오류 메시지 분리
- 주문/배송지 API는 SecurityConfig에서 authenticated 처리

장바구니 주문:

- 선택한 CartItem만 주문 원본으로 사용
- 결제 준비 시 CartItem을 삭제하지 않음
- `OrderItem.sourceCartItemId`에 원본 CartItem ID snapshot 저장
- 결제 성공 후 product/variant/quantity가 결제 당시와 같은 CartItem만 삭제
- 결제 대기 중 변경되었거나 이미 삭제된 CartItem은 결제 성공을 방해하지 않음

바로구매:

- 임시 CartItem을 만들지 않음
- 옵션 상품은 유효한 `variantId` 필수, 옵션 없는 상품은 `variantId=null`
- `OrderItem.sourceCartItemId=null`
- 준비·성공·실패 모든 과정에서 Cart 불변

### 배송지

- 회원당 최대 10개, 첫 배송지 자동 기본 배송지
- 기본 배송지 변경·수정·삭제와 삭제 후 자동 승격
- 사용자 소유권 검증
- 주문서에서 저장 배송지 선택 또는 새 배송지 입력
- Daum Postcode 주소 검색
- 새 배송지 저장 시 Address DTO의 엄격한 validation 적용
- 배송지 저장 실패 시 주문 준비를 실행하지 않고 입력과 오류 유지
- Order는 Address FK 대신 주문 시점 배송정보 snapshot 유지

## 4. Payment 1단계: Domain 및 DB 구조

`com.giftmarket.payment`에 다음 구조가 구현되어 있다.

```text
payment/
├─ controller
├─ dto
├─ entity
├─ exception
├─ gateway
├─ infrastructure/toss
├─ repository
└─ service
```

주요 domain:

- `Payment`: Order와 N:1, 같은 주문에서 결제 재시도 확장 가능
- `PaymentStatus`: `READY`, `CONFIRMING`, `PAID`, `FAILED`, `EXPIRED`, `CANCELED`
- `PaymentProvider`: PG 중립 provider 구분
- `PaymentMethod`, `EasyPayProvider`: 카드/이체/간편결제 의미를 PG와 분리
- merchant 결제 ID, client 요청 키, confirm 멱등 키에 unique 제약
- provider payment/transaction 식별자, 금액, 통화, 승인/실패 시각 저장
- 카드번호, CVC, Secret, PG 원본 전체 JSON은 저장하지 않음

Order 상태는 기존 데이터 호환을 위한 `ORDERED`, `CANCELLED`와 결제 흐름의
`PENDING_PAYMENT`, `PAID`, `PAYMENT_FAILED`, `PAYMENT_EXPIRED`를 함께 지원한다.

### orderedAt 및 개발 DB 주의사항

- `PENDING_PAYMENT`: `orderedAt=null`
- `PAID`: PG 승인 확정 시각 기록
- 기존 `ORDERED`: 기존 `orderedAt` 유지

기존 개발 DB의 `orders.ordered_at`이 `NOT NULL`이면 Hibernate
`ddl-auto: update`만으로 nullable 변경이 반영되지 않을 수 있다.

```sql
ALTER TABLE gift_market.orders
MODIFY ordered_at DATETIME(6) NULL;
```

```sql
SHOW COLUMNS FROM gift_market.orders LIKE 'ordered_at';
```

`Null = YES`인지 확인한다. 이를 우회하기 위해 PENDING_PAYMENT에 임의 시각을
넣지 않는다. 운영 전 Flyway/Liquibase 같은 versioned migration 도입이 필요하다.

## 5. Payment 2단계: 주문/결제 준비

현재 주문 생성 API는 실제 주문 완료가 아니라 결제 준비를 수행한다.

```text
사용자·상품·배송지 검증
→ Product/Variant row lock
→ Backend 금액 계산
→ 재고 예약 차감
→ Order PENDING_PAYMENT
→ OrderItem snapshot
→ Payment READY
→ CartItem 유지
→ commit
```

- 장바구니/바로구매의 기존 검증·금액·snapshot 로직을 재사용
- 재고는 결제 준비 transaction에서 한 번 예약 차감
- `payment.reservation-minutes` 설정으로 Payment `expiresAt` 생성
- 자동 만료/복원은 아직 구현하지 않았음

### 주문 준비 멱등성

- Frontend가 주문 입력 fingerprint의 SHA-256 hash와 UUID
  `clientOrderRequestKey`를 sessionStorage에 저장
- 배송지 개인정보 원문은 fingerprint storage에 저장하지 않음
- 같은 사용자와 같은 key 재요청은 기존 PENDING_PAYMENT Order/READY Payment 반환
- Order/Payment 추가 생성 및 재고 추가 차감 방지
- key는 최초 요청 내용에 귀속됨

준비 응답에는 `orderId`, `orderNumber`, `paymentId`, `merchantPaymentId`,
Backend 확정 `amount`, `orderName`, 상태, 만료 시각이 포함된다.

## 6. Payment 3단계: Gateway 및 Backend 승인

PG 교체 경계:

```text
PaymentService
→ PaymentGatewayRegistry
→ PaymentGateway
→ TossPaymentGateway
→ TossPaymentClient / TossPaymentMapper
```

- Order/Payment service는 Toss DTO를 직접 사용하지 않음
- `PaymentGateway`는 현재 `confirm`, `getPayment`만 제공
- Toss 상태·수단·간편결제 값은 mapper에서 내부 enum/result로 변환
- Secret은 `TOSS_SECRET_KEY` 환경변수로만 주입
- connect/read timeout 명시
- Basic Authorization은 `Base64(TOSS_SECRET_KEY + ":")`
- confirm에 DB의 고정 `confirmIdempotencyKey`를 Toss `Idempotency-Key`로 사용

### Payment API

```text
POST /api/payments/{paymentId}/confirm
GET  /api/payments/{paymentId}
```

두 API 모두 로그인 사용자만 접근할 수 있고 Payment/Order 소유권을 검증한다.
Frontend의 paymentKey/orderId/amount는 DB 값과 비교하며 그대로 신뢰하지 않는다.

### 승인 상태 전이 및 transaction 경계

```text
Transaction A
Payment/Order lock 및 소유권·금액·merchantPaymentId·만료 검증
→ READY에서 CONFIRMING 전환
→ commit

Transaction 밖
→ Toss confirm

Transaction B
→ Toss 결과와 DB 금액·통화·식별자 재검증
→ Payment PAID
→ Order PAID
→ orderedAt 승인 시각 기록
→ 안전한 CartItem 후처리
→ commit
```

- 이미 PAID면 PG를 다시 호출하지 않고 기존 성공 결과 반환
- CONFIRMING 재요청은 새 멱등 키를 만들거나 무조건 재승인하지 않고 Toss 조회
- timeout, connection reset, Toss 5xx 등 결과 불명은 CONFIRMING/PENDING_PAYMENT 유지
- 결과 불명 시 재고를 복원하거나 임의로 FAILED/PAID 처리하지 않음
- `GET /api/payments/{paymentId}` polling도 CONFIRMING이면 Toss 단건 조회
- Toss 조회가 `DONE`이면 동일 완료 검증을 거쳐 PAID로 복구

## 7. Payment 4단계: Toss 테스트 결제 Frontend

구현 파일:

```text
giftmarket-web/components/payment/TossPaymentWidget.tsx
giftmarket-web/lib/toss-payment.ts
giftmarket-web/lib/payment-api.ts
giftmarket-web/lib/payment-session.ts
giftmarket-web/app/payment/success/page.tsx
giftmarket-web/app/payment/fail/page.tsx
```

- 공식 Toss Payments v2 결제위젯 SDK loader 사용
- 공개 Client Key는 `NEXT_PUBLIC_TOSS_CLIENT_KEY`로만 주입
- Toss UI/launcher를 주문서에서 별도 component/lib로 격리
- 주문서에 배송지 → 주문 상품 → 결제수단 → 결제금액 → 결제 버튼 순서 제공
- 개발용 `결제 준비 완료`, 주문번호 안내, 별도 준비 버튼 제거
- `N원 결제하기` 한 번으로 validation → prepare → Backend amount 동기화 → 결제 요청
- 버튼 처리 상태와 더블클릭 방지
- 결제창 취소/닫기 후 READY 주문과 주문서 입력을 유지하고 재시도 가능
- 재시도 시 기존 clientOrderRequestKey로 중복 Order/Payment/재고 예약 방지

### success/fail

- `/payment/success`: query 검증 후 Backend confirm, CONFIRMING 제한 polling
- PAID 또는 이미 PAID이면 주문 상세로 이동하고 완료 session 정리
- success 새로고침에도 Backend confirm 멱등성으로 중복 승인 방지
- `/payment/fail`: Toss raw message를 노출하지 않고 code를 안전한 사용자 문구로 매핑
- fail 후 비민감 payment session의 `returnPath`로 주문서 재진입 가능
- 모바일 외부 TossPay 취소 복귀 시 SSR에서 `window/sessionStorage`를 읽지 않도록
  hydration 이후 조회
- payment-session utility도 서버에서는 빈 결과/no-op으로 동작
- sessionStorage에는 결제 복구용 비민감 식별자만 저장하며 배송지·카드정보는 저장하지 않음

## 8. 실제 검증 완료 상태

브라우저에서 Toss 테스트 환경으로 다음을 확인했다.

- 바로구매 카드 결제 성공
- 장바구니 주문 카드 결제 성공
- 간편결제 테스트 성공
- Toss confirm HTTP `200`, provider status `DONE`
- Payment `PAID`, Order `PAID`
- PG 승인 시각이 `orderedAt`에 기록됨
- 재고가 준비 시 한 번만 차감되고 승인 시 추가 차감되지 않음
- 장바구니 결제 성공 후 조건이 같은 CartItem 삭제
- 바로구매 후 Cart 불변
- 결제창 취소/닫기 후 주문서 유지 및 재시도
- 재시도 시 중복 주문·Payment·재고 차감 없음
- success 새로고침 시 중복 승인 없음
- 모바일 TossPay failUrl 복귀 시 SSR 오류 없이 취소 안내 및 재시도

자동 검증:

- Backend `gradlew test` 성공
- Backend 컴파일/ApplicationContext 성공
- Payment service 멱등·timeout·조회 복구·CartItem 후처리 테스트 포함
- Frontend `npx tsc --noEmit` 성공
- 변경 Frontend 파일 ESLint 성공
- `git diff --check` 성공

Frontend 전체 build는 번들 컴파일과 TypeScript까지 성공하지만 기존 `/login`
페이지의 `useSearchParams()` Suspense boundary 누락으로 정적 생성 단계에서 실패한다.

## 9. 아직 구현하지 않은 Payment 운영 기능

다음 항목은 완료 기능이 아니며 Payment 운영 안정성 5단계 범위다.

- provider webhook 및 중복 event 처리
- PENDING_PAYMENT 자동 만료
- 만료/실패 예약 재고의 정확히 한 번 복원
- 장기 CONFIRMING reconciliation scheduler/batch
- Toss 조회 기반 정기 대사와 운영 알림
- 실제 결제 전체 취소/환불
- `PaymentCancellation` domain/table
- 부분 취소/부분 환불
- 관리자 결제 조회·재동기화·취소 관리
- 운영 키, 계약 결제수단 및 실결제 전환
- 운영 secret manager와 명시적 DB migration 도입

PAID 주문은 PG 취소 성공 전에 Order를 CANCELLED로 확정하거나 재고를 복원하면
안 된다. 현재 기존 ORDERED 주문 취소 호환 로직을 실제 PAID 환불 흐름으로
간주하지 않는다.

## 10. 다음 작업 후보

### 1순위: 관리자 주문관리

- 관리자 전체 주문·결제·판매자별 처리 상태 조회
- 주문번호/구매자/판매자/결제·배송 상태 검색과 pagination
- 운영자가 장기 PENDING/CONFIRMING/CANCELING 상태를 확인할 최소 관측 화면
- 상태 강제 변경보다 기존 reconciliation/취소 흐름을 재사용하는 안전한 운영 기능 우선

### 운영 배포 전: Payment staging 통합 검증

- 공개 HTTPS staging에서 Toss webhook 수신과 중복 이벤트 멱등성 검증
- 결제·전체취소·timeout/재시도·재고 복원 전체 회귀 테스트
- 아래 `운영 전 필수 검증 TODO`는 실제 검증 전까지 완료 처리하지 않음

### 이후: 상품 상세 옵션 선택 UI 개선

- 옵션 그룹/값 선택 흐름과 선택 상태 정보 위계 개선
- 구매 가능 Variant 조합, 품절/비활성 옵션 안내
- 가격·재고·수량·바로구매/장바구니 버튼 UX 정리
- Desktop/Mobile 반응형 및 기존 Product/Variant 검증 보존

### 이후: Payment 운영 안정성 5단계

- webhook event 검증과 멱등 처리
- PENDING_PAYMENT 만료 및 예약 재고 복원
- CONFIRMING reconciliation과 운영 대사
- 전체 취소/환불 domain 및 PG adapter 확장
- 관리자 결제 운영 기능
- 테스트 키에서 운영 계약·운영 키로 전환하기 위한 체크리스트

## 11. 기타 TODO / 주의사항

- `/login`의 `useSearchParams` Suspense boundary build 오류 별도 수정
- 주문/재고/배송지/결제 integration test 지속 확충
- 운영 전 `ddl-auto:update` 의존 제거 및 migration 도구 도입
- 새 UI에 Tailwind utility class를 도입하지 않음
- 실제 Secret/API Key를 코드·문서·로그에 기록하지 않음

## 12. 새 세션 시작 인계

```text
AGENTS.md와 docs/DEVELOPMENT_STATUS.md를 먼저 읽고 실제 코드도 확인해.

Payment 1~4단계와 Toss 테스트 카드/간편결제 성공은 완료되었다.
주문 준비 멱등성, 재고 예약, READY → CONFIRMING → PAID,
CONFIRMING Toss 조회 복구, CartItem 안전 삭제 및 바로구매 Cart 불변을 깨뜨리지 마.

SellerOrder 1~5단계와 기존 데이터 backfill/NOT NULL 전환,
판매자 주문관리·배송 상태 전이와 구매자 판매자별 배송조회가 완료되었다.
다음 개발 우선순위는 관리자 주문관리이며, 운영 전에는 Payment staging 통합 검증이 필수다.
기존 Payment 운영 전 실제 Toss 통합 테스트 TODO는 완료 처리하지 마.
```
