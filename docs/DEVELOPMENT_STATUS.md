# Gift Market 개발 현황

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

### 1순위: 상품 상세 옵션 선택 UI 개선

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

다음 작업 후보는 상품 상세 옵션 선택 UI 개선이며,
그 이후 Payment 운영 안정성 5단계(webhook, 만료/재고 복원,
reconciliation, 실제 취소/환불, 관리자 운영)를 진행한다.
```
