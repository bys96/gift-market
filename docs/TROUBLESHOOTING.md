# Gift Market 개발 트러블슈팅 기록

> 최종 갱신: 2026-09-07
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

# Exchange

## paymentKey 없는 교환배송비 결과 불명

교환배송비 승인 응답이 유실되면 `ExchangeShippingPayment.REQUESTED`에 paymentKey가 남지 않을 수 있다. paymentKey가 없다는 이유만으로 실패나 미결제로 확정하면 이미 성공한 결제를 놓치고 reservation을 잘못 해제할 수 있다.

reconciliation은 paymentKey 조회를 우선하고, 없으면 저장된 provider orderId로 조회한다. 404 또는 불명확한 응답은 REQUESTED와 target reservation을 유지한다. 24시간 만료도 provider 성공 가능성을 배제한 뒤에만 Exchange CANCELED, Payment EXPIRED와 reservation release를 같은 업무 결과로 확정한다.

## FAILED retry와 attemptSequence

Toss idempotency key는 같은 결제 시도에서는 반드시 재사용하지만, 명시적으로 실패한 요청에 같은 key를 다시 쓰면 과거 실패 응답이 재사용될 수 있다. 따라서 하나의 ExchangeRequest당 `ExchangeShippingPayment` row와 amount snapshot은 유지하면서, 명시 실패 뒤 새 사용자 시도에는 `attemptSequence`를 증가시키고 provider orderId/idempotency key를 함께 회전한다. REQUESTED 결과 불명에는 새 attempt를 만들지 않는다.

## Return/Exchange 수량 교차 점유

같은 OrderItem에 반품과 교환이 동시에 생성되면 각각의 검증만으로는 원 주문수량을 초과할 수 있다. 가용수량 계산은 `canceledQuantity`, `returnedQuantity`, `exchangedQuantity`와 활성 Return/Exchange 점유수량을 함께 차감한다. Order → SellerOrder → 정렬 OrderItem 잠금 뒤 DB 기준으로 다시 확인한다.

## target reservation의 release와 consume

교환 target 재고는 승인 시 판매 가능 재고에서 먼저 차감하고 `reservedQuantity`로 추적한다. 미결제 만료·취소는 실제 재고 복원과 `releasedQuantity`를 같은 transaction에서 반영한다. 교환품 발송은 재고를 다시 차감하지 않고 `consumedQuantity`만 확정한다.

```text
effectiveReserved = reservedQuantity - releasedQuantity - consumedQuantity
```

누적 bookkeeping과 상태 전이를 멱등 장벽으로 사용해 중복 예약·복원·소비를 막는다.

# Product Variant

## inactive Variant와 과거 주문 참조

옵션 그룹·값을 제거할 때 과거 Variant를 물리 삭제하면 `OrderItem.variant` 참조와 주문 이력이 깨질 수 있다. 제거된 조합은 `active=false`로 보존하고 현재 mapping만 정리한다. Buyer에는 active Variant만 노출하며 Product 총재고도 active Variant만 합산한다.

현재 옵션 구조와 동일한 `combinationKey`가 다시 만들어지면 `(product_id, combination_key)` unique를 기준으로 기존 inactive Variant ID를 재활성화한다. 예전 옵션 차원의 조합은 현재 구조와 key가 다르므로 잘못 재활성화되지 않는다. 주문 당시 옵션 표시는 `OrderItem.optionSnapshot`으로도 보존한다.

# Frontend build

## App Router useSearchParams와 prerender

Next.js production build의 정적 페이지 생성 단계에서 `useSearchParams() should be wrapped in a suspense boundary` 오류가 발생했다. query 처리 로직이나 SSR/cache 정책을 바꾸지 않고 `/products`, `/login`, `/order`, `/seller/products/new`의 query-dependent Client Content를 기존 page의 `Suspense` 경계 아래 배치했다.

검색·필터·pagination, 로그인 redirect, 주문 query와 기존 loading UX는 유지했다. `force-dynamic`, SSR 비활성화 또는 전체 page의 Client Component 전환은 사용하지 않았다. 이후 production build가 정상화됐고, 2026-09-07 최신 검증에서는 정적 페이지 34개 생성까지 성공했다.

# DB / 개발환경

## Return 증빙 이미지 orphan

MinIO direct upload가 성공한 뒤 반품 생성 요청이 실패하면 DB에 연결되지 않은 `returns/{userId}/` object가 남을 수 있다. 반품 생성은 모든 선택 이미지 업로드 성공 후에만 실행하고 동일 화면 재시도에서는 업로드된 key를 재사용한다. 자동 삭제 작업은 아직 없으므로 운영 도입 전 prefix와 생성 시각을 기준으로 미참조 object를 정리하는 cleanup 정책이 필요하다.

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
- 현재: Return과 Exchange Buyer/Seller workflow·Frontend 완료, Return 정상 E2E와 BUYER 귀책 Exchange/Toss 6,000원 추가결제 정상 E2E 확인. SELLER 귀책 및 실제 timeout/5xx 장애 E2E, 공개 staging 전체 회귀는 미검증


# Seller / 권한

## ADMIN Seller Center redirect loop와 403 불일치

### 상황 / 증상

ADMIN도 실제 판매자로 등록해 상점을 운영할 수 있는 정책인데, Frontend 일부 경로는 ADMIN을 Seller Center로 보내고 다른 경로는 SELLER만 허용해 `/seller` ↔ `/seller/dashboard` redirect loop가 발생할 수 있었다. 또한 Frontend가 ADMIN을 허용해도 Backend `/api/seller/**`가 `ROLE_SELLER` 전용이면 ADMIN + ACTIVE Seller가 403을 받았다.

### 해결 / 현재 적용 구조

- Seller Center의 최종 접근 기준을 user role이 아니라 현재 사용자 소유 `ACTIVE Seller` 존재 여부로 통일
- `/api/seller/**`, `/api/sellers/**`는 authenticated matcher로 통과
- 실제 Service에서 ACTIVE Seller 존재, Seller ownership, 상품/주문/문의/클레임 ownership을 검증
- ADMIN이라는 이유만으로 Seller API를 허용하지 않음
- ADMIN 미등록은 동일 Seller 등록 폼을 거치고 Backend에서 신청 row 저장 후 같은 transaction으로 자동 승인
- ADMIN role은 유지하고 ACTIVE Seller만 생성
- 일반 관리자 승인과 ADMIN 자동승인은 `SellerApprovalService`를 재사용하며 `Propagation.MANDATORY`로 신청 transaction 안에서 실행
- 기존 SQL로 이미 생성된 `ADMIN + ACTIVE Seller`도 application 이력 없이 Seller Center 진입 가능

Frontend 일부 상세 page에는 `SELLER || ADMIN` 보조 guard가 남아 있을 수 있으나 최종 권한 source of truth는 공통 Seller Center layout과 Backend Service 검증이다.

# Frontend Pagination / 목록 상태

## Pagination page 번호 과다 렌더링

### 상황 / 증상

상품 리뷰에서 `pageWindowSize={totalPages}`처럼 전체 page 번호를 렌더링하거나 일부 화면이 공통 기본 정책을 override해 page 수가 커질수록 Pagination이 길어질 수 있었다.

### 해결 / 현재 적용 구조

공통 `components/common/Pagination.tsx` 기본 정책을 다음으로 통일했다.

```text
<<  <  3  4  [5]  6  7  >  >>
```

- 내부 page 0-based 유지
- 숫자 최대 5개
- 처음/이전/다음/마지막 버튼 제공
- 경계에서는 disabled
- URL Link / local `onPageChange` / summary / scroll 호환 유지
- 화면별 불필요한 `pageWindowSize` override 제거

## API 실패를 0건/0.0점으로 오인

### 상황 / 증상

리뷰 평균·개수, Buyer/Seller 문의 개수, Admin 판매자 신청 개수가 loading 또는 API error에서도 `0`, `0.0`처럼 보이면 실제 데이터가 없는 것과 서버 조회 실패를 구분할 수 없었다.

### 해결

정상 응답의 실제 0일 때만 0을 표시하고 loading/error/미조회 상태는 `-` 또는 기존 error UI로 구분한다.

## Buyer 상품문의 삭제 후 존재하지 않는 page 유지

### 상황 / 증상

마지막 page에 1개만 남은 문의를 삭제해 `totalPages`가 감소하면 현재 page가 범위를 벗어나 빈 목록에 남을 수 있었다.

### 해결

삭제 후 목록을 다시 조회하고 현재 page가 새 `totalPages` 범위를 벗어나면 마지막 유효 page로 이동한다. 유효한 page면 그대로 유지한다.

# Frontend cache / navigation

## `.next` stale cache로 존재하는 CSS import를 찾지 못함

### 상황 / 증상

실제 파일이 존재하는데 production/dev build에서 `Can't resolve './product/inquiry.css'` 같은 module resolution 오류가 발생한 사례가 있었다.

### 확인 / 해결

소스 import와 파일 경로가 실제로 일치하는지 먼저 확인한 뒤, 코드 문제가 아니라 stale `.next` cache로 판단되면 `.next`를 삭제하고 다시 build한다.

```bash
rm -rf .next
npm run build
```

파일이 실제로 없거나 대소문자 경로가 틀린 경우까지 cache 문제로 단정하지 않는다.

## 상품 상세 진입 시 이전 목록 scroll 위치가 남는 문제

목록의 `scroll=false` 필터/페이지 이동과 브라우저 뒤로가기 scroll 복원은 유지하면서, 다른 상품 ID의 상세 page에 새로 진입할 때만 최초 진입 기준 scroll top을 적용한다. 전역적으로 scroll restoration을 끄지 않는다.

# Storage / 사용자 데이터

## Profile objectKey 소유권 경계

### 위험

프로필 objectKey를 단순 UUID 경로로만 저장/삭제하면 다른 사용자의 key를 잘못 참조하거나 삭제 대상으로 오인할 여지가 있다.

### 현재 적용 구조

- 신규 프로필 key: `profiles/{userId}/{uuid}.{ext}`
- 저장/삭제 시 현재 사용자 prefix 검증
- path traversal/subpath 형태 차단
- legacy `profile/{uuid}`는 읽기 호환만 유지
- legacy key는 신규 저장 또는 자동 삭제 대상으로 사용하지 않음

## localStorage Wishlist의 사용자 간 공유/상태 노후화

### 위험

브라우저 localStorage만 source of truth로 사용하면 같은 브라우저에서 계정이 바뀔 때 wishlist가 섞일 수 있고, 상품의 최신 판매상태/가격과도 어긋날 수 있다.

### 현재 적용 구조

Wishlist를 user-scoped Backend API로 이전하고 서버를 source of truth로 사용한다. 비로그인 사용자는 로그인 흐름으로 보내며 상품 최신 상태는 Backend 조회 결과를 반영한다.

## `NEXT_PUBLIC_STORAGE_BASE_URL` 누락 시 이미지가 조용히 사라짐 — 아직 후속

현재 `resolveImageUrl()`은 objectKey인데 `NEXT_PUBLIC_STORAGE_BASE_URL`이 없으면 `null`을 반환한다. 배포 설정 오류가 실제 이미지 없음처럼 보일 수 있으므로, 배포 전 개발/운영 환경에서 설정 오류를 더 명확히 관측할 수 있는 방식으로 보완할 필요가 있다. 아직 해결 완료 항목으로 기록하지 않는다.

# Authentication / OAuth / 운영 Proxy

## Samsung Internet OAuth 로그인 후 Refresh Token 쿠키 유지 실패

### 상황 / 증상

운영 Frontend는 Vercel(`https://gift-market-test.vercel.app`), Backend는 Render(`https://gift-market-api.onrender.com`)에 배포되어 있다. 기존에는 브라우저가 Render를 직접 호출하는 cross-origin이자 cross-site 구조였다. Refresh Token은 HttpOnly Cookie로 관리하고 Access Token 재발급 요청에 `credentials: "include"`를 사용했다.

PC·모바일 Chrome에서는 로그인과 토큰 재발급이 정상이었지만 Samsung Internet에서는 OAuth 인증 성공 이후 Refresh Token Cookie가 기대대로 유지되지 않거나 Access Token 재발급을 통한 로그인 상태 복구가 실패했다.

### 원인 또는 위험

Frontend와 Backend가 다른 site에 있어 인증 흐름이 브라우저별 쿠키 정책, SameSite 처리, tracking prevention에 영향을 받을 수 있었다. 당시 운영 Refresh Cookie는 `Secure=true`, `SameSite=None` 설정이었다. `credentials: "include"`를 지정해도 브라우저의 cross-site 쿠키 제한을 해제하지는 않는다.

특정 쿠키 차단 정책이 직접 원인이었는지는 확정하지 않았다. 단순한 Samsung Internet 버그로 단정하지 않고, cross-site 인증 구조의 브라우저 정책 의존도를 줄이는 방향으로 해결했다.

기존 요청 흐름:

```text
Browser (https://gift-market-test.vercel.app)
→ https://gift-market-api.onrender.com/api/... 직접 호출

OAuth 시작: https://gift-market-api.onrender.com/oauth2/authorization/google
OAuth callback: https://gift-market-api.onrender.com/login/oauth2/code/google
```

### 해결 / 현재 적용 구조

브라우저별 예외 처리 대신 Production의 API/OAuth 요청을 Next.js rewrite 기반 same-origin proxy로 변경했다. 브라우저는 Vercel origin의 기존 경로를 사용하고 Vercel이 동일 경로의 Render Backend로 전달한다. `/backend` 등 별도 prefix는 추가하지 않았다.

rewrite는 서버 내부에서 요청을 전달하므로 브라우저의 요청 URL을 Render로 바꾸지 않는다. redirect는 브라우저에 다른 URL로 새 요청을 하도록 응답하는 방식이다. OAuth 제공자(Google/Kakao)로의 이동과 인증 성공 후 Frontend로의 redirect는 기존대로 유지된다.

`giftmarket-web/lib/api.ts`:

```typescript
const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim().replace(/\/+$/, "") ?? "";
```

- 환경변수 미설정 시 오류를 발생시키던 처리를 제거하고 빈 문자열을 사용한다.
- 값이 있으면 공백과 끝 `/`를 제거해 기존처럼 직접 호출 URL로 사용한다.
- Production에서는 빈 값으로 `/api/...` 상대경로를 사용하고 Local에서는 `http://localhost:8080`을 사용한다.
- `apiFetch`, `refreshAccessToken`, `credentials: "include"`, Authorization 처리와 JWT 로직은 유지한다.

`giftmarket-web/next.config.ts`는 `NODE_ENV === "production"`이고 `BACKEND_API_ORIGIN`이 설정된 경우에만 다음 rewrite를 생성한다. Backend origin의 공백과 끝 `/`도 정규화한다.

| 브라우저 요청 경로 | rewrite 목적지 |
| --- | --- |
| `/api/:path*` | `${BACKEND_API_ORIGIN}/api/:path*` |
| `/oauth2/:path*` | `${BACKEND_API_ORIGIN}/oauth2/:path*` |
| `/login/oauth2/:path*` | `${BACKEND_API_ORIGIN}/login/oauth2/:path*` |

기존 image remotePatterns, Storage URL 처리와 `dangerouslyAllowLocalIP` 설정은 유지한다. 로그인 페이지와 `AuthInitializer`는 기존 `API_BASE_URL` 조합을, OAuth callback 페이지는 기존 `apiFetch("/api/auth/token")` 호출을 그대로 사용한다.

### 환경변수 / OAuth Redirect URI

`NEXT_PUBLIC_API_BASE_URL`은 브라우저 요청의 기본 URL이고, `BACKEND_API_ORIGIN`은 Next.js/Vercel 서버의 rewrite 목적지다. 후자는 브라우저 공개용이 아니므로 `NEXT_PUBLIC_` 접두사를 사용하지 않는다.

Vercel Production에 추가:

```dotenv
BACKEND_API_ORIGIN=https://gift-market-api.onrender.com
```

Vercel Production의 `NEXT_PUBLIC_API_BASE_URL`은 삭제·미등록 또는 빈 값으로 설정한다. Render URL이 남으면 브라우저가 계속 Backend를 직접 호출한다. Storage/Toss 환경변수는 유지하고 설정 변경 후 재배포한다.

Local은 기존 설정을 유지하며 `BACKEND_API_ORIGIN`이 필요 없다.

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

운영 OAuth callback도 Vercel을 거치도록 Render에 다음 환경변수를 추가한다. Spring Boot OAuth registration의 `redirect-uri` property를 override하며 Backend Java와 `application-example.yaml`은 변경하지 않았다.

```dotenv
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI=https://gift-market-test.vercel.app/login/oauth2/code/google
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_KAKAO_REDIRECT_URI=https://gift-market-test.vercel.app/login/oauth2/code/kakao
```

기존 `FRONTEND_URL=https://gift-market-test.vercel.app`은 유지한다.

| OAuth 설정 위치 | 등록할 Redirect URI |
| --- | --- |
| Google Cloud Console Authorized Redirect URI | `https://gift-market-test.vercel.app/login/oauth2/code/google` |
| Kakao Developers Redirect URI | `https://gift-market-test.vercel.app/login/oauth2/code/kakao` |

### 최종 요청 흐름

```text
Local API:
Browser → http://localhost:8080/api/... → Spring Boot

Production API:
Browser → https://gift-market-test.vercel.app/api/...
→ Next.js/Vercel Rewrite → https://gift-market-api.onrender.com/api/...
→ Spring Boot

Production OAuth (Google):
Browser → https://gift-market-test.vercel.app/oauth2/authorization/google
→ Vercel Rewrite → Spring Security → Google
→ https://gift-market-test.vercel.app/login/oauth2/code/google
→ Vercel Rewrite → Spring Security
→ https://gift-market-test.vercel.app/oauth/callback

Refresh Token 기반 Access Token 재발급:
Browser → POST https://gift-market-test.vercel.app/api/auth/token
        (HttpOnly Refresh Token Cookie 포함)
→ Vercel Rewrite → Render/Spring → Access Token 재발급
```

`OAuth2AuthenticationSuccessHandler`의 `frontendUrl + "/oauth/callback"` redirect와 `RefreshTokenCookieManager`의 HttpOnly Cookie 방식을 유지한다. Cookie에는 별도 Domain을 지정하지 않으며 Path는 `/api/auth`다. proxy 응답으로 설정된 쿠키는 브라우저 기준 Vercel origin의 `/api/auth` 요청에 사용된다. Secure/SameSite는 기존 Backend 설정을 따르며 이번 proxy 변경에서 Java/YAML이나 Cookie 정책을 바꾸지 않았다. Refresh Token을 localStorage/sessionStorage에 저장하지 않는다.

### 결과 / 검증

- 사용자 제공 배포 확인 결과: PC Chrome, 모바일 Chrome, Samsung Internet에서 로그인·토큰 재발급 정상.
- 구현 당시 검증: Frontend lint/build 성공, rewrite 조건 및 API URL 정규화 9개 케이스 통과.
- Frontend와 Backend 배포 서버를 합치지 않고 브라우저 기준 API/OAuth Backend 요청을 same-origin으로 전환했다.
- 기존 API 경로·HttpOnly Refresh Token·인증 구조를 유지하면서 브라우저별 cross-site Cookie 정책 차이에 대한 의존도를 줄였다.

### 관련 코드 / 문서

- `giftmarket-web/lib/api.ts`
- `giftmarket-web/next.config.ts`
- `giftmarket-web/.env.sample`
- `giftmarket-web/app/login/page.tsx`
- `giftmarket-web/components/auth/AuthInitializer.tsx`
- `giftmarket-web/app/oauth/callback/page.tsx`
- `giftmarket-api/src/main/java/com/giftmarket/auth/handler/OAuth2AuthenticationSuccessHandler.java`
- `giftmarket-api/src/main/java/com/giftmarket/auth/util/RefreshTokenCookieManager.java`
- `giftmarket-api/src/main/java/com/giftmarket/auth/controller/AuthController.java`

# Refresh Token Rotation 환경에서 동시 갱신 Race Condition으로 인한 간헐적 로그아웃 해결

### 상황 / 증상

- 로그인 후 새로고침할 때 간헐적으로 로그아웃된다.
- `/api/auth/token`이 HTTP 200을 반환하지만 응답 `data`가 `null`이다.
- 빠른 연속 새로고침에서 한 요청이 `aborted`/`unknown` 상태가 된 뒤 세션이 풀릴 수 있었다.

### 원인 분석

1. OAuth Callback과 `AuthInitializer`가 각각 Refresh Token 갱신을 호출했다.
2. Refresh Token Rotation으로 첫 요청이 기존 토큰을 폐기한 뒤, 동시 요청이 이전 토큰을 사용하면서 race condition이 발생했다.
3. Frontend 중복 호출을 제거한 뒤에도 빠른 F5나 멀티탭처럼 서로 다른 document에서 동시 요청은 발생할 수 있다.
4. Refresh 성공 후 브라우저가 `Set-Cookie` 응답을 받기 전에 navigation으로 요청이 취소되면, 서버는 회전했지만 브라우저에는 이전 Refresh Token이 남을 수 있다.

### Frontend 해결 / 현재 적용 구조

- OAuth Callback과 `AuthInitializer`의 인증 초기화 흐름을 공통 `initializeAuth`로 통합하고 하나의 Promise를 공유한다.
- `/api/auth/token`은 자동 재시도하지 않는다. Rotation 성공 후 응답만 유실된 경우 재시도하면 race가 커질 수 있기 때문이다.
- `/api/auth/me`는 네트워크 오류 또는 HTTP 502/503/504일 때만 제한적으로 한 번 재시도한다.
- 일시적 서버 오류에서는 즉시 인증 상태를 삭제하지 않고 초기화 실패 상태를 유지한다.

### Backend 해결 / 현재 적용 구조

- Refresh Token row 조회에 `PESSIMISTIC_WRITE` 잠금을 적용해 같은 사용자의 동시 갱신을 직렬화한다.
- 회전 직전의 Refresh Token hash와 만료 시각을 10초 grace period 동안 보존한다.
- grace 요청에서는 저장된 현재 Refresh Token 원문을 복호화해 Access Token과 함께 현재 Refresh Cookie를 다시 발급한다.
- 현재 Refresh Token 원문은 평문이 아니라 AES-GCM으로 암호화해 저장한다.
- 암호화 키는 JWT 서명 키와 분리된 `REFRESH_TOKEN_ENCRYPTION_KEY` 환경변수를 사용한다.

### DB 변경

운영 환경이 `ddl-auto: validate`이므로 다음 컬럼을 운영 DB에 명시적으로 추가했다.

- `previous_token_hash VARCHAR(64)`
- `previous_token_expires_at DATETIME(6)`
- `token_value_encrypted VARCHAR(512)`

첫 배포에서는 `previous_token_expires_at` 등 신규 컬럼이 없어 schema validation이 실패했다. 배포 전에 운영 DB에 nullable 컬럼 DDL을 적용해야 한다.

### Render 배포 이슈

- Render Environment Variable에 `REFRESH_TOKEN_ENCRYPTION_KEY`를 추가한다.
- Render Secret File로 사용하는 `application.yaml`에도 다음 매핑이 필요하다.

```yaml
app:
  jwt:
    refreshTokenEncryptionKey: ${REFRESH_TOKEN_ENCRYPTION_KEY}
```

- 로컬 `application.yaml`은 gitignore 대상이므로 로컬 파일만 수정해서는 Render Secret File이 갱신되지 않는다.

### 결과 / 검증

일반 새로고침, 빠른 연속 새로고침, grace period 이후 새로고침, 멀티탭 동시 갱신에서도 서버가 같은 rotation 상태를 기준으로 처리하고 세션을 유지할 수 있는 구조로 개선했다.

# 최신 검증 메모

- 2026-09-07 최신 작업 보고: Backend **711 tests / 710 success / 1 environment-dependent failure** (contextLoads JDBC metadata/dialect 오류), Frontend lint/tsc/build 성공, 정적 페이지 34개.
- 실제 Secret 파일은 문서 점검 과정에서 읽거나 출력하지 않는다.
