# Gift Market 개발 트러블슈팅 기록

> 최종 갱신: 2026-08-28
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

검색·필터·pagination, 로그인 redirect, 주문 query와 기존 loading UX는 유지했다. `force-dynamic`, SSR 비활성화 또는 전체 page의 Client Component 전환은 사용하지 않았다. 이후 production build가 정상화됐고, 2026-08-28 최신 검증에서는 정적 페이지 34개 생성까지 성공했다.

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

# 최신 검증 메모

- 2026-08-28 최신 작업 보고: Backend **511/511**, Frontend lint/tsc/build 성공, 정적 페이지 34개.
- 실제 Secret 파일은 문서 점검 과정에서 읽거나 출력하지 않는다.
