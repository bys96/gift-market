# Gift Market

Java 21/Spring Boot 4.1.0과 Next.js 16.2.11/React 19.2.4로 구현한 오픈마켓형 쇼핑몰입니다. 현재 구현·운영 경계는 [개발 현황](./docs/DEVELOPMENT_STATUS.md)을 참조하세요. 문서와 코드가 다르면 **현재 코드가 기준**입니다.

## 현재 구현 범위

- 구매자: Google OIDC/Kakao OAuth2, JWT·Refresh Token, 회원정보·탈퇴, 배송지, 상품 검색·옵션, Wishlist, Cart, 주문·Toss 결제, 배송 조회, 전체/부분 취소, 반품, 동일가격 교환·배송비 추가결제, 구매확정, 문의·리뷰, 알림.
- 판매자: 신청·승인, SellerStore 설정, 상품·옵션·재고·미디어, 주문·배송·취소·반품·교환·문의 관리, Dashboard, 알림, 정산 조회.
- 관리자: 판매자 신청, 회원·판매자·상품 상태 관리, 주문·클레임 조회, 알림, 정산 조회·수동 생성·보류·해제·확정.
- 정산 v1: `SellerOrder → SettlementLedgerEntry → Settlement`. 결제 매출·수수료, 배송완료 후 정산 가능일, 취소·반품 환불/수수료 환입을 원장에 기록합니다. ADMIN 수동 generate가 정산 가능한 원장을 수집하며 활성 claim은 SellerOrder 전체를 제외합니다. `READY → ON_HOLD → READY`, `READY → CONFIRMED`만 허용합니다. **CONFIRMED는 금액 확정이지 지급 완료가 아닙니다.** 자동 정산 Scheduler와 실제 Payout은 없습니다.

## 주요 구조와 불변식

```text
Order 1:N SellerOrder 1:N OrderItem
                   ├─ Shipment N (ORIGINAL_OUTBOUND / RETURN_COLLECTION / EXCHANGE_COLLECTION / EXCHANGE_OUTBOUND)
                   ├─ OrderCancellation / ReturnRequest / ExchangeRequest N
                   └─ SettlementLedgerEntry N
Seller 1:N Settlement 1:N SettlementLedgerEntry
Order 1:N Payment 1:N PaymentCancellation (현재 일반 주문은 주 결제 중심)
Seller 1:1 SellerStore
```

주문 시 가격·배송비 snapshot을 보존하고, 금액·재고·소유권은 Backend가 최종 검증합니다. 외부 Toss 호출과 DB 완료 트랜잭션은 분리하며 결과 불명에는 webhook/reconciliation을 사용합니다. 옵션 변경 시 과거 주문이 참조하는 Variant를 물리 삭제하지 않습니다. 구체적인 결제·클레임 정책은 아래 설계 문서를 참조하세요.

## 화면

- 구매자: `/`, `/products`, `/products/[productId]`, `/cart`, `/order`, `/payment/success`, `/payment/fail`, `/my`, `/my/profile`, `/my/addresses`, `/my/wishlist`, `/my/orders`, `/my/orders/[orderId]`, `/my/inquiries`, `/notifications`, `/my/exchanges/payment/success`, `/my/exchanges/payment/fail`.
- 판매자: `/seller/apply`, `/seller/application`, `/seller/dashboard`, `/seller/products`, `/seller/orders`, `/seller/orders/cancellations`, `/seller/orders/returns`, `/seller/orders/exchanges`, `/seller/inquiries`, `/seller/settings`, `/seller/notifications`, `/seller/settlements`, `/seller/settlements/[settlementId]`. 상품·주문·클레임·문의 상세 화면은 해당 하위 route를 사용합니다.
- 관리자: `/admin`, `/admin/seller-applications`, `/admin/users`, `/admin/sellers`, `/admin/products`, `/admin/orders`, `/admin/cancellations`, `/admin/returns`, `/admin/exchanges`, `/admin/notifications`, `/admin/settlements`, `/admin/settlements/[settlementId]`. 일부 목록에는 하위 상세 route가 있습니다.
- `/terms`, `/privacy`, `/support`는 route가 있지만 내용은 운영용 확정 문안이 아닙니다.

## 대표 API

아래는 실제 Controller의 주요 route입니다. 요청·응답 필드와 세부 권한은 Controller/DTO/Service가 기준입니다. 페이지 번호는 0부터 시작합니다.

| 영역 | 주요 endpoint | 권한/비고 |
|---|---|---|
| 인증·회원 | `GET /api/auth/me`, `GET /api/users/me`, `DELETE /api/users/me` | 인증 사용자 |
| 상품 | `GET /api/products`, `GET /api/products/{productId}`, `/api/seller/products/**` | 판매자 쓰기는 소유권 검사 |
| 주문·결제 | `POST /api/orders`, `POST /api/orders/direct`, `GET /api/orders`, `GET /api/orders/{orderId}`, `POST /api/payments/{paymentId}/confirm` | 인증 사용자·주문 소유권 검사 |
| 취소 | `POST /api/orders/{orderId}/cancellations`, `PATCH /api/orders/{orderId}/cancel`, `POST /api/seller/orders/{sellerOrderId}/cancel`, `/api/seller/orders/cancellations/**` | 구매자/판매자 별 소유권 검사 |
| 반품·교환 | `/api/orders/{orderId}/seller-orders/{sellerOrderId}/returns`, `/api/orders/{orderId}/seller-orders/{sellerOrderId}/exchanges`, `/api/seller/orders/returns/**`, `/api/seller/orders/exchanges/**` | 상태별 메서드는 Controller 참조 |
| 알림 | `/api/notifications/**`, `/api/seller/notifications/**`, `/api/admin/notifications/**` | BUYER·SELLER·ADMIN 범위 분리 |
| 판매자 정산 | `GET /api/seller/settlements`, `GET /api/seller/settlements/summary`, `GET /api/seller/settlements/{settlementId}` | 현재 판매자 소유 자료만 |
| 관리자 정산 | `GET /api/admin/settlements`, `GET /api/admin/settlements/{settlementId}`, `POST /api/admin/settlements/generate`, `POST /api/admin/settlements/{settlementId}/hold`, `/release`, `/confirm` | ADMIN 전용 |

SecurityConfig는 `/api/admin/**`를 ADMIN 전용으로, `/api/seller/**`를 인증 필요로 설정합니다. Seller API는 role만으로 허용하지 않고 Service에서 Seller row·상태·소유권을 재검증합니다. 정산 조회는 `ACTIVE`/`SALES_SUSPENDED` Seller를 허용하며 쓰기 정책과 구분합니다. 공통 `ApiResponse`, Bean Validation, Global Exception Handler를 사용합니다.

## 실행과 설정

- Backend: `cd giftmarket-api` 후 `./gradlew bootRun` (Windows는 `gradlew.bat`). 설정 키는 [`application-example.yaml`](./giftmarket-api/src/main/resources/application-example.yaml)을 참조합니다.
- Frontend: `cd giftmarket-web` 후 `npm install`, `npm run dev`. 공개 변수 이름은 [`giftmarket-web/.env.sample`](./giftmarket-web/.env.sample)을 참조합니다.
- 로컬 DB는 MySQL, object storage는 `storage.provider`에 따라 MinIO 또는 S3입니다. Toss·OAuth·storage·DB credential은 환경변수로만 제공합니다.
- Frontend의 production same-origin `/api`, `/oauth2`, `/login/oauth2` rewrite는 `next.config.ts`의 `BACKEND_API_ORIGIN`을 사용합니다. 개발용 공개 `NEXT_PUBLIC_API_BASE_URL` 및 이미지용 `NEXT_PUBLIC_STORAGE_BASE_URL`은 sample을 참조합니다.

배포 구조는 Vercel Frontend와 Render Backend, MySQL 및 외부 object storage입니다. `giftmarket-api/Dockerfile`은 Java 21 layered jar 및 AppCDS archive를 빌드합니다. 런타임 `/health` endpoint가 있습니다. 실제 Render/Vercel 환경변수 값과 연결 상태는 저장소만으로 재검증할 수 없으며, 운영값을 이 문서에 기록하지 않습니다. KST `LocalDateTime`/MySQL `DATETIME(6)` 운영 정책에는 런타임 JVM `-Duser.timezone=Asia/Seoul`, JDBC URL `connectionTimeZone=%2B09:00&forceConnectionTimeZoneToSession=true`를 함께 설정합니다. Hibernate `jdbc.time_zone`은 별도로 지정하지 않습니다. 경위는 [트러블슈팅](./docs/TROUBLESHOOTING.md)을 참조하세요.

## DB와 배포 경계

개발 기본 설정은 `JPA_DDL_AUTO:update`이며, 운영은 `ddl-auto=validate`와 수동 SQL 선적용 방식을 사용해 왔습니다. [`docs/sql`](./docs/sql/)은 자동 실행 migration이 아니며 환경·적용 순서를 확인하고 사용해야 합니다. `settlement-v1-foundation.sql`에는 정산 두 테이블의 FK/unique/index가 정의됩니다. Flyway/Liquibase 같은 versioned migration 체계, 백업·복구·모니터링은 후속 운영 과제입니다.

## 검증

```bash
cd giftmarket-api
./gradlew test

cd ../giftmarket-web
npx tsc --noEmit
npm run lint
npm run build
```

테스트 수·빌드 결과는 실행 시점에 확인합니다. 이 문서는 현재 테스트 성공을 주장하지 않습니다.

## 문서

- [개발 현황](./docs/DEVELOPMENT_STATUS.md): 완료·부분 구현·미구현·운영 검증 경계
- [결제 설계](./docs/PAYMENT_ARCHITECTURE_DESIGN.md), [취소 설계](./docs/ORDER_CANCELLATION_REFUND_DESIGN.md), [반품·교환 설계](./docs/ORDER_RETURN_EXCHANGE_DESIGN.md)
- [알림](./docs/NOTIFICATION_DESIGN.md), [상품 상세 미디어](./docs/PRODUCT_DESCRIPTION_MEDIA.md), [관리자 운영 정책](./docs/admin-operation-policy.md)
- [정산 v1](./docs/SETTLEMENT_V1.md): 원장·집계·상태·조회/관리 API와 지급 제외 경계
- [트러블슈팅](./docs/TROUBLESHOOTING.md), [수동 SQL](./docs/sql/)
- [이전 문서 동기화 기록](./DOCS_UPDATE_NOTES.md): 과거 시점의 변경 이력이며 현재 상태는 개발 현황/코드가 우선
