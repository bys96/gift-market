# Gift Market 개발 현황

> 기준: 2026-08-14 현재 실제 소스 확인 결과

## 1. 현재 기술 기준

### Backend

- Java 21
- Spring Boot 4.1.0
- Spring Security / OAuth2 / OIDC / JWT
- Spring Data JPA / MySQL
- MinIO / jsoup / Gradle

### Frontend

- Next.js 16.2.11 App Router
- React 19.2.4 / TypeScript
- Zustand
- 일반 CSS
- Tiptap
- TanStack Query dependency 존재

Tailwind dependency와 `globals.css` import는 존재하지만 새 UI는 프로젝트
컨벤션에 따라 일반 CSS로 구현한다.

------------------------------------------------------------------------

## 2. 현재 Backend domain

``` text
com.giftmarket
├─ address
├─ admin
├─ auth
├─ cart
├─ global
├─ order
├─ product
├─ seller
└─ user
```

구현된 주요 기능:

- 구매자 상품 목록/상세, 상품 옵션/Variant/재고/상태
- 판매자 상품 등록·수정 및 Seller Center
- 판매자 신청과 관리자 승인 처리
- JWT Access Token + Refresh Token cookie 인증
- Google/Kakao OAuth2/OIDC
- MinIO presigned URL 기반 storage
- 장바구니 등록·수량 변경·단건/복수 삭제·구매 가능 여부 조회
- 주문 생성·목록·상세·취소 및 재고 복원
- 배송지 CRUD와 기본 배송지 정책

------------------------------------------------------------------------

## 3. 주문 현재 상태

### Backend API

``` text
POST  /api/orders                 # 장바구니 선택 주문
POST  /api/orders/direct          # 상품 상세 바로구매
GET   /api/orders
GET   /api/orders/{orderId}
PATCH /api/orders/{orderId}/cancel
```

### 장바구니 주문

- 기존 `OrderCreateRequest`와 CartItem 기반 주문 유지
- 요청 CartItem의 사용자 소유권 검증
- 중복/누락 CartItem 및 구매불가 상태 검증
- 주문 성공 시 선택 CartItem 삭제
- 실패 시 transaction rollback

### 바로구매 주문

`DirectOrderCreateRequest`:

``` text
productId
variantId (nullable)
quantity
recipientName
recipientPhone
postalCode
address
addressDetail
```

- 임시 CartItem을 만들지 않음
- 옵션 상품은 유효한 `variantId` 필수
- 옵션 없는 상품은 `variantId=null`
- 다른 상품의 Variant, 비활성 Variant, Variant 재고를 Backend에서 검증
- 성공·실패 시 기존 장바구니를 변경하지 않음

### 공통 주문 정합성

- Product/Variant pessimistic row lock
- 상품 판매 상태, 수량 및 재고 Backend 재검증
- 가격과 배송비는 Backend 현재 값으로 계산
- 클라이언트 계산 금액을 신뢰하지 않음
- 기존 `Order` / `OrderItem` 상품 및 배송정보 snapshot 구조 유지
- 옵션 주문 후 Product 총재고 동기화
- 취소 시 Product/Variant 재고 복원

현재 Payment domain과 PG 결제는 아직 없다.

------------------------------------------------------------------------

## 4. Frontend 주문 흐름

현재 화면:

``` text
/order
/my/orders
/my/orders/[orderId]
```

`/order`는 query 형태로 주문 원본을 구분한다.

- `cartItemIds=...`: Cart store 기반 장바구니 주문
- `productId=...&variantId=...&quantity=...`: 상품 API 기반 바로구매
- 장바구니 주문은 기존 CartItem 불일치/구매불가 검증 유지
- 장바구니 주문 성공·실패 후 `loadCart()` 재동기화
- 바로구매는 Cart store를 주문 원본으로 사용하지 않음
- 두 흐름 모두 주문 완료 후 `/my/orders/{orderId}`로 이동

상품상세 `바로 구매` 버튼은 선택한 Product/Variant/수량을 `/order`로
전달한다. 주문 확정 시 금액은 전달하지 않고 Backend에서 다시 계산한다.

------------------------------------------------------------------------

## 5. 배송지 관리 및 주문서 연동 현재 상태

### Backend API

``` text
GET    /api/addresses
POST   /api/addresses
PUT    /api/addresses/{addressId}
PATCH  /api/addresses/{addressId}/default
DELETE /api/addresses/{addressId}
```

정책:

- 회원당 최대 10개
- 첫 배송지 자동 기본배송지
- 기본배송지 변경·수정·삭제
- 기본배송지 삭제 후 남은 배송지 자동 승격
- 사용자 소유권 검증
- 입력 normalize와 transaction 적용

### Frontend 배송지 관리

- `/my/addresses` 목록·등록·수정·삭제·기본배송지 설정
- `N/10` 표시 및 10개 도달 시 추가 비활성화
- 전화번호 formatting
- `daum.Postcode` 주소 검색 layer
- Desktop/Mobile modal UI

### 주문서 연동

- 주문서 진입 시 `GET /api/addresses` 조회
- 기본 배송지 우선, 없으면 첫 배송지 자동 선택
- `배송지 변경` modal에서 저장 배송지 또는 새 배송지 선택
- 새 배송지 입력 시 기존 `daum.Postcode` 방식 사용
- `배송지에 저장`, 배송지명, 기본 배송지 설정 옵션
- 주소록 저장 시 `AddressRequest`의 더 엄격한 validation 적용
- 주소록 저장 실패 시 주문을 생성하지 않고 입력값과 오류 유지
- 저장 배송지 수정/삭제 책임은 `/my/addresses`에 유지
- Order는 Address FK가 아니라 주문 시점 배송정보 snapshot 유지

------------------------------------------------------------------------

## 6. Security 현재 상태

`SecurityConfig`에서 기존 공개 API, admin, seller, 공통 인증 matcher 순서를
유지하며 다음 경로가 명시적으로 `.authenticated()` 처리되어 있다.

``` text
/api/orders/**
/api/addresses/**
```

- 비로그인 접근은 Spring Security에서 `401 Unauthorized`로 차단
- 유효 JWT는 기존 `JwtAuthenticationFilter`가 user ID와 역할 설정
- 기존 admin/seller/auth/user/storage/cart 정책 유지
- Order/Address Service의 인증 및 소유권 검증 유지

------------------------------------------------------------------------

## 7. 주문 실패 메시지 및 예외 처리

`OrderException`은 `GlobalExceptionHandler`의 `ApiResponse.message`를 통해
Frontend `apiFetch`와 주문서에 전달된다.

장바구니 주문과 바로구매 메시지는 Backend 검증 문맥에서 구분한다.

| 상황 | 바로구매 | 장바구니 주문 |
|---|---|---|
| 구매할 수 없는 상품 | 현재 구매할 수 없는 상품입니다. | 현재 구매할 수 없는 상품이 포함되어 있습니다. 장바구니를 다시 확인해주세요. |
| 판매 중지 | 현재 판매가 중지된 상품입니다. | 현재 판매가 중지된 상품이 포함되어 있습니다. 장바구니를 다시 확인해주세요. |
| 상품 재고 부족 | 상품 재고가 부족합니다. 상품 정보를 다시 확인해주세요. | 상품 재고가 부족합니다. 장바구니를 다시 확인해주세요. |
| 잘못된 옵션 정보 | 선택한 옵션 정보를 확인할 수 없습니다. | 선택한 상품 옵션을 찾을 수 없습니다. |
| 비활성 옵션 | 선택한 옵션은 현재 구매할 수 없습니다. | 현재 구매할 수 없는 옵션이 포함되어 있습니다. 장바구니를 다시 확인해주세요. |
| 옵션 재고 부족 | 선택한 옵션의 재고가 부족합니다. | 선택한 상품 옵션의 재고가 부족합니다. 장바구니를 다시 확인해주세요. |
| 잘못된 수량 | 구매 수량을 다시 확인해주세요. | 구매 수량을 다시 확인해주세요. |

처리되지 않은 500 예외는 서버에 stack trace를 기록하고 사용자에게는
`서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.`만 반환한다.
SQL, 내부 ID, stack trace 같은 내부 정보는 사용자 응답에 노출하지 않는다.

------------------------------------------------------------------------

## 8. 검증 상태

- Backend `gradlew test`: 성공
- Backend 컴파일 및 ApplicationContext: 성공
- 변경 Frontend 파일 ESLint: 성공
- `npx tsc --noEmit`: 성공
- `git diff --check`: 성공
- 비로그인 `/api/orders`, `/api/addresses`: `401` 확인

Frontend 전체 build는 번들 컴파일과 TypeScript 검사까지 성공하지만 기존
`/login` 페이지의 `useSearchParams()` Suspense boundary 누락으로 정적 페이지
생성 단계에서 실패한다.

Domain별 service/integration 자동화 테스트는 아직 충분하지 않다. Payment
도입 전에 주문 생성, 재고 차감·복원, 배송지, 인증 실패 케이스 테스트를
확충해야 한다.

------------------------------------------------------------------------

# 9. 바로 다음 작업: Payment 도메인 설계 및 실제 결제 연동 준비

이 작업이 현재 최우선이다.

## 목표

현재 주문 생성과 결제 완료가 사실상 같은 단계인 구조를 분리하고, PG 연동
전에 Payment domain과 상태 전이, 금액 검증, 멱등성 기준을 확정한다.

우선 검토할 항목:

1. Payment entity/table 및 Order 연관관계
2. 결제 준비·승인·실패·취소 API contract
3. `OrderStatus`와 Payment 상태의 역할 및 전이
4. PG 결제금액과 Backend 주문금액 일치 검증
5. 결제 승인 callback/webhook 신뢰 및 검증 방식
6. 결제 준비·승인·취소 요청 idempotency
7. 주문 생성 후 결제 실패/이탈 시 재고와 CartItem 처리
8. 장바구니 주문과 바로구매의 결제 진입 공통화 범위
9. 결제 성공 이후에만 주문 완료로 확정하는 흐름
10. 운영 secret, callback URL, 로그의 민감정보 보호

## 반드시 보존할 현재 구조

- 장바구니 `POST /api/orders`와 바로구매 `POST /api/orders/direct`의 구분
- Product/Variant row lock과 Backend 재고 검증
- Backend 가격·배송비 계산 권위
- `Order` / `OrderItem` snapshot
- CartItem 및 Address 소유권 검증
- 저장 배송지 선택 UX
- cart/direct 사용자 오류 메시지 분리

Payment 설계 시 현재 주문 생성 시점의 재고 차감과 CartItem 삭제를 언제
확정할지 반드시 명시적으로 재검토한다.

------------------------------------------------------------------------

# 10. Payment 작업 시 먼저 읽을 파일

Backend:

``` text
giftmarket-api/src/main/java/com/giftmarket/order/controller/OrderController.java
giftmarket-api/src/main/java/com/giftmarket/order/service/OrderService.java
giftmarket-api/src/main/java/com/giftmarket/order/entity/Order.java
giftmarket-api/src/main/java/com/giftmarket/order/entity/OrderItem.java
giftmarket-api/src/main/java/com/giftmarket/order/entity/OrderStatus.java
giftmarket-api/src/main/java/com/giftmarket/order/dto/request/OrderCreateRequest.java
giftmarket-api/src/main/java/com/giftmarket/order/dto/request/DirectOrderCreateRequest.java
giftmarket-api/src/main/java/com/giftmarket/cart/service/CartService.java
giftmarket-api/src/main/java/com/giftmarket/global/config/SecurityConfig.java
giftmarket-api/src/main/java/com/giftmarket/global/exception/GlobalExceptionHandler.java
```

Frontend:

``` text
giftmarket-web/app/order/page.tsx
giftmarket-web/components/order/OrderSummary.tsx
giftmarket-web/components/order/OrderRecipientForm.tsx
giftmarket-web/lib/order-api.ts
giftmarket-web/types/order.ts
giftmarket-web/stores/cart-store.ts
```

------------------------------------------------------------------------

# 11. 이후 로드맵

1. Payment domain 및 상태 전이 설계
2. PG 제공사 선정과 결제 준비/승인 API 연동
3. 결제 성공/실패/취소와 OrderStatus 정합성
4. 주문·결제 idempotency
5. 결제 실패/이탈 주문 및 재고 복구 정책
6. webhook 검증과 운영 로그/모니터링
7. 주문/재고/배송지/결제 자동화 테스트 확충
8. 판매자 주문 관리
9. 관리자 주문/결제 운영 기능
10. 운영 환경 설정/배포/모니터링

------------------------------------------------------------------------

# 12. 현재 확인된 TODO / 주의사항

## Payment

아직 Payment entity/table, PG 연동, 승인·실패·취소 callback, 결제 멱등성이
구현되어 있지 않다. 현재 주문 생성 흐름을 최종 결제 완료 구조로 확정하지 않는다.

## 테스트

주문/재고/배송지/인증/결제의 service 및 integration test 확충이 필요하다.

## Frontend build

기존 `/login` 페이지의 Suspense boundary 문제를 별도 수정해야 한다.

## Tailwind

새 UI에 Tailwind utility class를 도입하지 않는다. dependency 정리는 별도
리팩토링으로 처리한다.

------------------------------------------------------------------------

# 13. Codex 새 세션 시작 프롬프트

``` text
AGENTS.md와 docs/DEVELOPMENT_STATUS.md를 먼저 읽고, 문서만 믿지 말고 현재 실제 코드도 확인해.

현재 최우선 작업은 Payment 도메인 설계 및 실제 결제 연동 준비야.

장바구니 주문, 바로구매, 저장 배송지 선택, 주문/배송지 Security, Backend 가격·재고 검증과 Order/OrderItem snapshot은 구현되어 있으므로 깨뜨리지 마.

먼저 현재 Order/OrderItem/OrderStatus, 장바구니 및 바로구매 생성 흐름, 재고 차감과 CartItem 삭제 시점을 확인해.

그 다음 PG 연동 전 필요한 Payment entity, 상태 전이, API contract, 멱등성, 결제 실패 시 재고/Cart 처리 정책을 설계해줘.
아직 코드는 수정하지 마.
```
