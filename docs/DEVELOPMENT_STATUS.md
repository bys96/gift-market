# Gift Market 개발 현황

> 이 문서는 현재 Backend, Frontend, 설정과 수동 SQL을 기준으로 한 구현 상태다. 문서와 코드가 충돌하면 코드가 최종 기준이다. 향후 작업은 [`ROADMAP.md`](./ROADMAP.md)에서 관리한다.

## 전체취소 정책 및 완료 정합성 수정 (2026-11-10)

- 일반 전체취소는 미결제의 모든 SellerOrder가 PENDING_PAYMENT이거나, 결제 완료의 모든 SellerOrder가 PAID인 경우로 제한한다. 구매확정·기처리 수량과 진행 중 취소/반품/교환이 있으면 차단한다. PREPARING의 승인 정책과 배송 후 반품·교환 정책을 우회하지 않는다.
- 전체취소와 판매자 준비·출고/배송완료·직접취소 생성은 Payment → Order → SellerOrder 잠금 순서를 맞추고 CANCELING 중 판매자 변경을 차단한다. 요청/응답·ERD는 유지한다. 상세 기준은 [취소 설계](./ORDER_CANCELLATION_REFUND_DESIGN.md)를 따른다.
- FULL 완료 시 판매자별 COMPLETED OrderCancellation/상품 수량 이력을 만들고 canceledQuantity·재고·상태·정산 환불 및 수수료 환입을 같은 transaction에서 반영한다. 기존 요청 키와 원장 근거 키를 사용해 ERD·API 변경 없이 연결한다. 최초 매출 원장이 없는 과거 거래는 음수 원장을 만들지 않는 기존 정책을 유지한다.
- MySQL 회귀는 정산 서비스를 실제 실행하며 단일·복수 판매자, 옵션별 수량, 주문 snapshot 금액·배송비·수수료, 중복 취소, PG 응답 유실·DB 오류 후 조회 및 reconciliation 복구를 검증한다. 실제 Toss·운영 DB·운영 E2E와는 구분한다.
- 최종 `gradlew test mysqlIntegrationTest bootJar` 성공: Backend 기본 전체 테스트 1,121개, 격리 MySQL 통합 테스트 40개가 실패·스킵 없이 통과했고 실행 JAR도 빌드했다. 기존 부분취소·반품·교환 회귀를 포함한다.
- 과거에 이미 CANCELLED/SUCCEEDED로 저장된 누락 자료의 대사·보정과 정책 위반 상태에서 시작된 취소 거래는 별도 운영 점검 대상이다. 이번 변경에 자동 backfill은 포함하지 않는다.

## 점검 기준 (2026-11-09)

- **완료**: 해당 범위의 Backend·Frontend 주요 흐름과 관련 테스트가 구현됨. 운영 검증 완료를 뜻하지 않는다.
- **부분 구현**: 일부 흐름은 구현되어 있으나 제품 범위 안에 빠진 기능이 있다.
- **미구현**: 조사한 Entity·DTO·Service·Controller·화면에서 해당 흐름을 확인하지 못했다.
- **확인 필요**: 저장소 밖 설정·운영 적용·외부 연동·실기기 실행 증거가 필요하다. 구현 상태와 별도로 표시한다.

이번 작업은 문서와 소스 대조만 수행했다. 테스트·빌드·운영 접속·배포·SQL 실행은 하지 않았다. 아래 과거 실행 결과와 현재 소스 확인을 구분한다.

## 최근 검색 변경 — 코드 반영 / 운영 배포 확인 필요

- 운영 공개 검색 API에서 동일한 `침구` 요청의 3건 → 0건 → 3건 변동을 확인했다. 변경 없는 인덱스 정기 스캔이 `ready=false`로 전환해 상품명 검색인 MySQL fallback으로 바뀌는 경로를 수정했다. 상세 근거와 배포 범위는 [`TROUBLESHOOTING.md`](./TROUBLESHOOTING.md)의 해당 기록을 참고한다.
- 최종 `gradlew test bootJar meiliIntegrationTest`가 통과했다. Backend 전체 기본 회귀, JAR 빌드 및 격리된 실제 Meilisearch 동의어·반복 검색 테스트를 검증했다. Frontend 검색 회귀 테스트, lint, TypeScript 검사, production build도 통과했다.
- Frontend 전체 Node 테스트는 27건 중 26건 통과, 기존 `tests/login-redirect.test.mjs:87`의 `isLoggingOut is not defined`로 1건 실패했다. 검색 수정 범위 밖이므로 변경하지 않았다.
- 위 항목은 2026-11-06 작업 이력이다. 현재 배포된 API의 commit·image 반영 여부는 확인 필요다.
- 현재 `product/search/`에는 Meilisearch 후보 검색·MySQL fallback과 UTF-8 한국어 동의어 자동 적용, 관리자 settings/reindex API가 있다. Gemini 임베딩 호출·의존성·상품 임베딩 모듈은 현재 실행 소스에서 확인되지 않는다. 운영의 과거 테이블·설정 정리 여부는 별도 확인 필요다.
- 자동완성 `SearchBox`는 추천 검색어 목록만 렌더링한다. API의 `products` 응답 필드는 유지되며 상품 카드 중복 UI를 표시하지 않는다.
- `HeaderSearchSync`는 `/products?keyword=…`를 기준으로 PC·모바일 헤더 입력값을 공유·동기화한다. 다른 경로는 빈 값이며 URL 변경 때 SearchBox key를 바꿔 목록·진행 중 요청을 초기화한다. 타이핑만 할 때는 key가 유지된다. 필터·정렬·페이지 변경 및 뒤로/앞으로 이동 테스트가 있다.
- 검색 결과 페이지는 `/products`이며 전용 `/search` route는 없다. Backend 검색 수정은 API, 헤더/UI 변경은 Vercel Frontend 반영 대상이다. 실제 운영 반영은 확인 필요다.

## 소스 대조 요약 (2026-11-09)

아래 완료는 관련 코드의 주요 흐름이 존재한다는 뜻이며 운영 검증 완료를 뜻하지 않는다. **검증 필요**는 구현 여부와 별개로 실제 실행·외부 서비스·배포 설정 확인이 남았다는 표시다. 이번 문서 작업에서는 테스트나 운영 시스템을 실행·조회하지 않았다.

| 영역 | 코드 기준 상태 및 판단 근거 | 추가 검증 |
| --- | --- | --- |
| 회원·인증 | 구현 완료: Google/Kakao OAuth 가입·로그인, JWT/refresh 및 회원 관리. 별도 이메일·비밀번호 회원가입은 확인되지 않음. `auth/controller/AuthController.java`, `auth/service/`, `user/service/UserService.java`, `giftmarket-web/app/login/page.tsx` | OAuth callback, 토큰 재발급·탈퇴 및 권한 경계 |
| 상품 탐색 | 구현 완료: 목록·상세·카테고리·검색·필터. `product/controller/ProductController.java`, `product/service/ProductService.java`, `product/repository/ProductSpecifications.java`, `product/dto/request/ProductSearchCondition.java`, `giftmarket-web/lib/product-api.ts`, `giftmarket-web/app/products/` | 검색 품질·실제 데이터·모바일 |
| 장바구니·찜 | 구현 완료: `cart/controller/CartController.java`, `cart/service/CartService.java`, `wishlist/controller/WishlistController.java`, `wishlist/service/WishlistService.java`, `giftmarket-web/lib/cart-api.ts`, `giftmarket-web/lib/wishlist-api.ts` | 로그인·재고 변경 시 동기화 |
| Meilisearch·동의어·인덱스 | 완료: `product/search/`의 `ProductSearchService`, `MeiliClient`, `MeiliSynonyms`, `MeiliIndexService`, `AdminSearchIndexController` 및 `src/main/resources/search/korean-synonyms.properties` | 실제 엔진 선택·사전 해시·네트워크·인덱스 상태·장애 fallback은 확인 필요 |
| 자동완성·헤더 URL 동기화 | 완료: `giftmarket-web/components/common/SearchBox.tsx`, `components/layout/HeaderSearchSync.tsx`, `lib/suggestion-request.ts`, `tests/header-search.test.mjs` | 배포 반영과 모바일·IME·브라우저 실제 history 동작은 확인 필요 |
| 선물하기·수신자 | 부분 구현: 일반 주문 DTO/Entity의 수령인 이름·연락처·주소 snapshot 입력은 완료. 선물 전용 주문·수락/거절·수신자 주소 입력 링크·만료 API/UI는 미구현 | 선물 상황 분류와 별도 범위. 제품 정책·ERD·API 설계 및 수신자 E2E는 확인 필요 |
| 주문·내역 | 구현 완료: `order/controller/OrderController.java`, `order/service/OrderService.java`, `order/repository/OrderRepository.java`, `order/dto/request/OrderCreateRequest.java`, `giftmarket-web/lib/order-api.ts`, `giftmarket-web/app/order/`, `giftmarket-web/app/my/orders/` | 다중 판매자·동시 주문·금액과 재고 |
| 결제·실패 복구 | 구현 완료: Toss 승인·취소·webhook·reconciliation. `payment/controller/PaymentController.java`, `payment/service/PaymentService.java`, `payment/service/PaymentReconciliationService.java`, `payment/repository/PaymentRepository.java`, `payment/dto/request/PaymentConfirmRequest.java`, `giftmarket-web/lib/payment-api.ts`, `giftmarket-web/app/payment/` | PG 결과 불명·중복·webhook 및 운영 연동 |
| 배송·확정·취소·반품·교환 | 구현 완료: 구매자·판매자 workflow와 환불·재고 처리. `order/controller/`, `order/service/PurchaseConfirmationService.java`, `order/service/ReturnRequestService.java`, `order/service/ExchangeRequestService.java`, `payment/service/PartialPaymentCancellationReconciliationService.java`, `giftmarket-web/components/order/`, `giftmarket-web/app/seller/(seller-center)/orders/` | 실제 배송, 부분 수량·동시 클레임·환불 및 교환배송비 결제 |
| 리뷰·문의 | 구현 완료: `review/controller/ReviewController.java`, `review/service/ReviewService.java`, `review/repository/ReviewRepository.java`, `inquiry/controller/`, `inquiry/service/`, `giftmarket-web/components/product/ProductReviewSection.tsx`, `ProductInquirySection.tsx`, `giftmarket-web/lib/review-api.ts`, `inquiry-api.ts` | 구매확정 자격·비밀문의·판매자 답변 |
| 판매자 신청·센터 | 구현 완료: `seller/service/SellerApprovalService.java`, `seller/controller/`, `giftmarket-web/lib/seller-api.ts`, `giftmarket-web/app/seller/` | 승인 rollback·활성 Seller 소유권 및 운영 시나리오 |
| 관리자 운영 | 부분 구현: 대시보드·조회·제재·신청 승인·정산 관리 구현, 강제 환불·클레임 중재는 미구현. `admin/controller/`, `admin/service/AdminDashboardService.java`, `giftmarket-web/lib/admin-api.ts`, `giftmarket-web/app/admin/(admin-center)/` | 권한·감사 및 실제 운영 절차 |
| 옵션·재고·이미지 | 구현 완료: `product/service/ProductRegistrationService.java`, `ProductVariantService.java`, `product/repository/ProductVariantRepository.java`, `product/dto/request/ProductRegistrationRequest.java`, `global/storage/service/StorageService.java`, `giftmarket-web/components/seller/ProductForm.tsx` | 동시 재고 변경·업로드 및 보관 객체 정리 |
| 선물 상황 | 구현 완료: 관리자가 생성·수정·활성 상태 관리, 판매자가 상품에 최대 3개 연결, 구매자 홈과 목록에서 조회·필터. `admin/controller/AdminGiftOccasionController.java`, `product/service/GiftOccasionService.java`, `product/repository/ProductGiftOccasionRepository.java`, `product/dto/request/GiftOccasionCreateRequest.java`, `giftmarket-web/lib/admin-api.ts`, `lib/product-api.ts`, `app/admin/(admin-center)/gift-occasions/`, `components/seller/ProductForm.tsx`, `app/page.tsx`, `app/products/page.tsx` | 실제 분류 데이터·필터 및 비활성 처리 |
| 쿠폰·포인트·프로모션 | 미구현: 관련 독립 도메인과 구매 적용 흐름을 소스에서 확인하지 못함 | 정책·거래 및 정산 영향 설계 |
| 정산 | 부분 구현: 원장과 수동 생성·확정 및 판매자·관리자 조회는 존재, 자동 생성과 실제 송금은 없음. `settlement/controller/`, `settlement/service/SettlementGenerationService.java`, `settlement/repository/`, `settlement/dto/`, `giftmarket-web/lib/seller-settlement-api.ts`, `admin-settlement-api.ts` | 클레임·수수료 환입·회차 및 지급 운영 정책 |
| 배포·저장 | 부분 구현/검증 필요: 설정상 Vercel rewrite, Docker, MySQL, S3/MinIO 추상화와 수동 DDL. `giftmarket-web/next.config.ts`, `giftmarket-api/src/main/java/com/giftmarket/global/storage/`, `docs/sql/` | 실제 AWS·DB·백업·복구·외부 연동 및 현재 배포 상태 |
| 테스트·CI/CD | 부분 구현: Backend 기본/컨테이너 task와 Frontend Node 테스트·lint·tsc·build가 존재. API Actions는 배포 스크립트 호출만 포함 | 통합 테스트 게이트·실브라우저/외부 PG E2E·Vercel 설정·기존 로그인 테스트 실패는 확인/개선 필요 |

Java 경로는 `giftmarket-api/src/main/java/com/giftmarket/` 기준이다. DTO·Repository와 API 연결은 대표 흐름을 기준으로 기록했으며 모든 분기나 운영 데이터의 완전성을 보증하지 않는다.

선물 상황 관계는 `GiftOccasion`과 `ProductGiftOccasion` Entity의 상품-상황 연결 및 `(product_id, gift_occasion_id)` 유일 제약으로 표현된다. [`sql/gift-occasions.sql`](./sql/gift-occasions.sql)은 수동 DDL 참고본이며 자동 migration이나 현재 운영 DB 적용 확인이 아니다. 별도의 전체 ERD 최신성은 확인되지 않았고, Notification·Settlement 등의 기존 도메인별 관계도는 각 설계 문서와 Entity를 함께 확인한다.

## 1. 현재 서비스 범위

Gift Market은 다중 판매자 주문, Toss 결제, 배송, 취소·반품·교환, 알림과 정산 금액 확정까지 연결된 오픈마켓형 서비스다. 정산 v1은 실제 지급이 아니라 판매자별 매출 원장과 정산 금액 확정까지 담당한다.

## 2. 구현 완료

### 인증·회원

- Google OIDC와 Kakao OAuth2 로그인
- JWT Access Token과 HttpOnly Refresh Token cookie
- Refresh Token rotation, 이전 토큰 grace, 동시 갱신 잠금
- 프로필 조회·수정과 프로필 이미지 저장
- 배송지 CRUD와 회원당 최대 10개 정책
- 회원 탈퇴: 진행 중 주문·클레임과 판매자 처리 건 검증, 개인정보 익명화, Refresh Token·주소·장바구니·찜 정리
- 탈퇴 시 OAuth 식별정보가 익명화되어 같은 OAuth 계정으로 신규 가입 가능

### 판매자·상점

- 일반 사용자 판매자 신청과 관리자 승인·거절
- ADMIN 자기 신청의 같은 transaction 자동 승인
- `Seller 1:1 SellerStore`와 판매자센터 상점 설정
- `ACTIVE`, `SALES_SUSPENDED`, `SUSPENDED`, `WITHDRAWN` 상태 모델
- `SALES_SUSPENDED`는 신규 판매 작업을 차단하고 기존 주문·클레임 처리는 허용
- Seller Dashboard, 상품, 주문, 배송, 취소, 반품, 교환, 문의, 알림, 정산 화면

### 상품·구매 기능

아래 선물 상황은 탐색용 분류다. 주문 생성은 구매자가 수령인·주소를 입력하는 일반 구매 흐름이며, 선물 전용 수신자 전달·수락/거절·주소 입력 링크는 현재 구현 범위가 아니다.

- Category, Product CRUD, 판매 상태, 대표·상세 이미지와 MP4 상세 미디어
- 옵션 그룹·옵션 값·Variant 조합과 재고
- 과거 주문 참조를 위한 inactive Variant 보존과 같은 조합 재활성화
- 구매자 상품 목록·상세·검색·카테고리·품절 제외·URL 기반 pagination/filter
- 선물 상황 관리·상품 연결과 구매자 홈 추천 탭 및 목록 필터
- 회원별 Wishlist와 Cart
- 상품문의 작성·수정·삭제, 비밀문의 masking, 판매자 답변 등록·수정
- 구매확정된 OrderItem 기준 리뷰, 이미지, soft delete 후 같은 row 재사용

### Object Storage

- `StorageProvider` 추상화로 AWS S3와 MinIO 구현을 제공하며 Production은 AWS S3, 로컬 개발은 MinIO를 사용할 수 있다.
- DB와 상품 description에는 영구 URL 대신 object key를 저장한다. 업로드·권한 확인 후 조회에는 300초 presigned URL을 사용하고 URL 자체는 저장하지 않는다.
- presigned PUT은 검증한 `Content-Length`와 정규화한 `Content-Type`을 서명한다. 이미지 기본 상한은 5MB, 상품 이미지 20MB, 상품 MP4 50MB, 스토어 배너 10MB다.
- 현재 key 규칙:

| 용도 | key prefix |
| --- | --- |
| 프로필 | `profiles/{userId}/` |
| 상품 대표·갤러리·상세 | `products/{sellerId}/representative/`, `gallery/`, `content/` |
| 상품 상세 MP4 | `products/{sellerId}/content/video/` |
| 반품·교환 증빙 | `returns/{userId}/`, `exchanges/{userId}/` |
| 리뷰 | `reviews/{userId}/` |
| 스토어 로고·배너 | `stores/{sellerId}/logo/`, `stores/{sellerId}/banner/` |

`StorageType.BANNER`의 공용 `banner/` prefix도 존재하지만 현재 주요 상품·회원·클레임·스토어 흐름은 위 소유자 prefix를 사용한다.

### 주문·결제·배송

- `Order -> SellerOrder -> OrderItem` 다중 판매자 구조
- 주문 시 상품명·옵션·가격·배송비 snapshot
- Toss 결제 승인, webhook, READY 만료와 CONFIRMING reconciliation
- 최초 배송, 반품 회수, 교환 회수·재배송을 구분하는 `SellerOrder 1:N Shipment`
- 구매확정 수량과 리뷰 작성 자격 연결
- 결제창을 닫은 뒤 배송정보가 바뀌면 READY/PENDING_PAYMENT 주문 snapshot을 갱신하고 기존 Payment를 안전하게 재사용
- Frontend가 재사용 주문 상세와 현재 상품·옵션·수량·금액 snapshot을 비교하고 불일치 시 취소 API로 준비 주문을 취소·예약 복원한 뒤 재시도를 안내. 같은 요청 키 조회 자체는 OrderService에서 기존 준비 결과를 반환하며, 모든 비교가 Backend에 구현된 것은 아님
- 결제 callback에서 `sessionStorage`가 없어도 `merchantPaymentId + 현재 사용자`로 Payment를 조회
- confirm 통신 실패·5xx를 즉시 결제 실패로 단정하지 않고 상태 재조회와 제한된 polling/reconciliation으로 복구
- polling timeout은 실패가 아니라 결과 확인 지연으로 안내

상세 transaction과 상태 정책은 [`PAYMENT_ARCHITECTURE_DESIGN.md`](./PAYMENT_ARCHITECTURE_DESIGN.md)를 따른다.

### 취소·반품·교환

- 구매자 PAID 즉시 부분취소와 PREPARING 승인형 취소
- 판매자 직접취소·환불과 BUYER/SELLER requester 구분
- OrderItem별 부분수량, 원 배송비, 재고복원과 `PaymentCancellation(PARTIAL)`
- 취소 reconciliation, webhook과 orphan recovery
- 구매자 PAID 취소 제출 전 공통 Modal 최종 확인
- 반품 요청·승인·회수·입고·검수·환불·완료, 귀책과 반품배송비 snapshot
- 교환 target 재고 reservation/release/consume, 구매자 귀책 교환배송비 추가결제, 회수·검수·재배송·완료

상세 기준은 [`ORDER_CANCELLATION_REFUND_DESIGN.md`](./ORDER_CANCELLATION_REFUND_DESIGN.md)와 [`ORDER_RETURN_EXCHANGE_DESIGN.md`](./ORDER_RETURN_EXCHANGE_DESIGN.md)를 따른다.

### 알림

- BUYER, SELLER, ADMIN context별 목록·미읽음 수·개별/전체 읽음 API와 UI
- SELLER 알림은 `ACTIVE`, `SALES_SUSPENDED`에 허용하고 `SUSPENDED`, `WITHDRAWN`은 차단
- 주문·배송·취소·반품·교환·상품문의·판매자 신청 이벤트 연결
- 업무 transaction commit 후 이벤트를 처리하고 알림 저장 실패가 원 업무를 rollback하지 않도록 분리
- 실시간 push가 아닌 진입·Bell open 기반 조회

알림 type과 연결 지점은 [`NOTIFICATION_DESIGN.md`](./NOTIFICATION_DESIGN.md)를 따른다.

### Settlement v1

```text
Seller 1:N Settlement
SellerOrder 1:N SettlementLedgerEntry
Settlement 1:N SettlementLedgerEntry (settlement_id nullable)
```

- 결제 완료: `SALE_PRODUCT`, 필요한 경우 `SALE_SHIPPING`, `COMMISSION`
- `ORIGINAL_OUTBOUND` 배송완료: `deliveredAt + holdDays`로 최초 매출 원장 `eligibleAt` 확정
- 취소: 성공한 `PaymentCancellation` 기준 `CANCELLATION_REFUND`, 필요한 `COMMISSION_REVERSAL`
- 반품: COMPLETED와 PG 환불 결과 기준 `RETURN_REFUND`, 필요한 `COMMISSION_REVERSAL`
- 최초 commission rate snapshot과 취소·반품 통합 누적 상품환불액으로 수수료 환입 계산
- eligible 미귀속 원장 수집, active claim SellerOrder 전체 제외, 과거 누락분 catch-up
- `READY -> ON_HOLD -> READY`, `READY -> CONFIRMED`; CONFIRMED는 terminal
- 판매자 목록·상세·미정산 summary API/UI
- 관리자 목록·상세·수동 generate·hold·release·confirm API/UI
- `CONFIRMED`는 정산 금액 확정이며 지급 완료가 아님

자동 Scheduler와 실제 Payout은 구현되어 있지 않다. 상세 기준은 [`SETTLEMENT_V1.md`](./SETTLEMENT_V1.md)를 따른다.

### Admin

- Dashboard
- 회원 정지·해제
- 판매자 신청 승인·거절, 판매 정지·해제
- 상품 관리자 숨김·해제
- 주문·취소·반품·교환 조회
- 알림과 Settlement 조회·생성·상태 관리
- Admin 상태 변경 감사 로그
- `SUPER_ADMIN` 전용 관리자 목록·ADMIN 권한 부여·회수와 역할 변경 이력
- Seller row를 보존한 채 ADMIN을 부여하고, 회수 시 Seller 존재 여부에 따라 `SELLER` 또는 `USER`로 복원

강제 환불이나 클레임 중재 command는 아직 없다. 운영 권한 범위는 [`admin-operation-policy.md`](./admin-operation-policy.md)를 따른다.

### Frontend 안정성·접근성

- 상품 목록의 stale result 안내·재시도와 pagination 성공 후 결과 상단 이동
- 상품 상세 section 이동은 hash를 유지하되 history를 추가하지 않음
- 상품 이미지 carousel·확대 modal·touch swipe와 상품 상세 Scroll To Top
- Scroll To Top의 `prefers-reduced-motion` 대응
- 공통 Pagination 모바일 약 44px touch target
- 구매확정 성공 후 상세 refresh 실패를 mutation 실패와 구분
- 리뷰 상태 조회 실패를 리뷰 없음과 구분하고 영역 단위 재시도
- Seller 주문·취소·반품·교환·상품문의 목록의 필터/page를 URL query로 보존
- 홈 상품 캐러셀은 PC 4.5개·태블릿 3.5개, 모바일 2행과 다음 열 0.5개를 표시하고 드래그·스크롤과 마지막 전체 보기 카드 끝 정렬을 지원한다. 상품은 모바일에서 위→아래→다음 열 순서다 (`giftmarket-web/components/product/HomeProductCarousel.tsx`, `giftmarket-web/styles/home.css`).
- 상품 상세 600px 이하에서 5개 탭을 균등 배치한다 (`giftmarket-web/styles/product/content.css`). 헤더 관리자 메뉴는 `/admin`으로 연결된다 (`giftmarket-web/components/layout/Header.tsx`). 기존 상세 탭에는 스크롤 연동 활성 상태 구현이 확인되지 않았다. 이 UI의 실기기 검증은 필요하다.

## 3. 부분 구현·현재 불일치

- 회원 탈퇴는 진행 중 구매·판매 업무를 차단하지만, Seller의 미귀속 ledger나 미확정 Settlement를 직접 검사하는 정산 guard는 없다.
- `/terms`, `/privacy`, `/support` route는 존재하지만 운영 법무·고객지원 문구 확정이 필요하다.
- 상세 미디어와 프로필 등 일부 object는 실패·이탈 시 orphan cleanup이 완전 자동화되어 있지 않다.

## 4. 운영·배포 현재 기준

아래 운영 상태와 성공 기록은 기존 문서에 남은 설정·작업 이력이다. 2026-11-09 문서 대조에서는 실제 운영 자원, 현재 설정과 외부 연동을 재확인하지 않았다. 백업·인증서·외부 결제 성공 이력을 현재 정상 상태로 해석하지 않는다.

### 배포 책임과 저장소에서 확인되는 범위

| 대상 | 담당과 근거 | 확인 필요 |
| --- | --- | --- |
| Backend API | [deploy-api.yml](../.github/workflows/deploy-api.yml): main의 `giftmarket-api/**` push 또는 수동 실행. OIDC로 AWS 권한을 받아 SSM으로 EC2의 외부 `deploy-api.sh` 호출, 최대 20분 상태 대기 | 외부 스크립트의 checkout commit·테스트·빌드·health·롤백. workflow 자체에 checkout/Gradle test 단계는 없다. Dockerfile은 bootJar에 `-x test`를 사용하므로 테스트 게이트를 추정하지 않는다. |
| Frontend | Vercel에서 Next.js 배포. [next.config.ts](../giftmarket-web/next.config.ts)가 production rewrite와 storage origin을 구성 | Vercel Git 연동 branch·root directory·build command·배포 commit·환경변수는 저장소에서 확인 불가. API Actions가 Frontend를 배포하지 않는다. |
| Meilisearch | [compose.search.yaml](../compose.search.yaml): opt-in profile, v1.16.0, 내부 네트워크·영속 볼륨·리소스 제한·healthcheck | root에 API/MySQL용 Compose는 없다. 외부 운영 Compose의 검색 네트워크 연결·환경변수·볼륨·재기동 상태는 확인 필요. 검색 Compose 변경만으로 API Actions는 자동 실행되지 않는다. |

환경변수 이름·용도와 안전한 실행 안내는 [README](../README.md), 검색 전용 설정은 [Meilisearch 운영 문서](./MEILISEARCH_OPERATIONS.md)를 따른다. 원격 배포·백업 스크립트와 실제 secret 파일은 이번 점검 대상에 포함하지 않았다.

### 기존 운영 기록 (현재 실측 아님)

- Backend: Java 21, Spring Boot 4.1.0, Spring Security/JPA, MySQL, Toss Payments, Actuator
- Frontend: Next.js 16.2.11 App Router, React 19, TypeScript, Zustand, 일반 CSS
- Production Frontend: Vercel (`https://gift-market-test.vercel.app`)
- Production Backend/DB: AWS EC2 Ubuntu Server 24.04 LTS ARM64 `t4g.small`에서 Spring Boot와 MySQL 8.4 계열을 Docker로 운영한다.
- Frontend는 production에서 `BACKEND_API_ORIGIN` 기반 same-origin rewrite를 사용한다. 브라우저의 `NEXT_PUBLIC_API_BASE_URL`은 empty/unset이며 `/api`, `/oauth2`, `/login/oauth2` 요청을 Vercel이 AWS Backend HTTPS origin으로 전달한다.
- Google/Kakao OAuth callback도 Vercel 경유 구조를 유지한다. AWS 이전 후 두 Provider의 Production 로그인을 확인했다.
- Nginx가 HTTPS termination과 HTTP→HTTPS redirect를 담당한다. Let's Encrypt IP certificate 자동 renewal과 성공 후 Nginx reload hook이 설정되어 있다.
- Spring Boot는 host `127.0.0.1:8080`에만 bind하고 MySQL `3306`은 Docker internal network에서만 사용한다. 외부에는 80/443만 공개하며 SSH 22는 관리자 현재 IP 범위로 제한한다.
- MySQL은 Docker named volume을 사용하고 실제 데이터는 EC2 EBS에 영속된다. 기존 Aiven 데이터 dump/import가 완료됐으며 현재 Production은 EC2 MySQL을 사용한다.
- Production object storage는 AWS S3다. 애플리케이션은 동일 object key 정책과 presigned URL을 사용하며 로컬 개발에서는 MinIO Provider를 선택할 수 있다.
- AWS 이전 후 Toss 결제와 기존 webhook endpoint의 Production 연결을 확인했다.
- Docker image는 Java 21 Dynamic AppCDS archive를 생성하고 runtime `JAVA_TOOL_OPTIONS`에서 archive를 사용한다.
- 인증 없는 `GET /health`를 운영 health check에 사용할 수 있다.
- production은 수동 DDL 선적용 후 `ddl-auto=validate`로 검증한다. `docs/sql/*.sql`은 자동 migration이 아니다.
- 개발 example의 기본 `JPA_DDL_AUTO`는 `update`이므로 production 설정과 혼동하지 않는다.
- 시간 정책은 `LocalDateTime + MySQL DATETIME(6)` KST wall-clock이다. 운영 JVM은 `-Duser.timezone=Asia/Seoul`, JDBC는 `connectionTimeZone=%2B09:00&forceConnectionTimeZoneToSession=true`를 사용하며 Hibernate `jdbc.time_zone`은 추가하지 않는다.
- 운영 MySQL 컨테이너 `giftmarket-mysql`은 `/home/ubuntu/gift-market-deploy/scripts/backup-mysql.sh`로 매일 04:00 KST에 자동 백업한다. systemd service/timer에 `Persistent=true`를 적용해 누락된 실행을 보완한다.
- 백업은 컨테이너에서 `mysqldump`를 수행하고 gzip 압축한 뒤 private S3 bucket의 `backups/mysql/` prefix에 업로드한다. 업로드 성공 후 EC2 임시 파일을 삭제한다.
- S3 Lifecycle은 `backups/mysql/`의 현재 객체 버전을 생성 14일 후 만료해 rolling 14-day retention을 유지한다. 상품 이미지 등 다른 object prefix에는 이 규칙을 적용하지 않는다.
- EC2 Instance Profile의 backup policy는 `backups/mysql/`에 대한 object Put/Get/Delete와 해당 prefix의 ListBucket으로 한정하며 다른 S3 object 영역의 관리 권한을 포함하지 않는다.
- 수동 백업, S3 upload/download, `gzip -t`가 성공했다. 운영 DB를 변경하지 않고 별도 임시 MySQL 8.4 컨테이너에 복원해 41개 table과 주요 데이터 row를 확인한 뒤 임시 자원을 삭제했다.
- 과거 Render/Aiven 환경은 현재 운영 기준이 아니며 이전 이력과 당시 문제 해결 기록만 [`TROUBLESHOOTING.md`](./TROUBLESHOOTING.md)에 보존한다.

## 5. 검증 기록과 현재 미검증 범위

2026-11-09에는 테스트나 빌드를 실행하지 않았다. 아래는 기존 작업 기록이며 현재 HEAD 전체의 재실행 결과가 아니다.

- 2026-11-06 검색 일관성 작업: Backend 기본 테스트 1,082개와 Meilisearch 컨테이너 테스트 2개, JAR 빌드 성공 기록.
- 헤더 URL 동기화 작업: 검색 관련 Node 테스트 10개, lint·tsc·production build 성공 기록. 전체 Node 테스트 31개 중 30개 통과, `tests/login-redirect.test.mjs`의 `isLoggingOut is not defined` 1건 실패 기록. 현재 소스에서도 해당 테스트 실행 context에 변수가 없는 것을 확인했으나 이번에 재실행하거나 수정하지 않았다.
- 위 테스트는 수신자 선물 수락 등 미구현 흐름이나 전체 운영 E2E의 완료 근거가 아니다. Node 헤더 테스트는 URL 이동·history를 모의 실행하며 실제 브라우저 history 검증과 구분한다.

### 2026-10-08 과거 회귀

- Backend: 959 tests / 959 success
- Frontend: `npm run lint` 성공
- Frontend: `npx tsc --noEmit` 성공
- Frontend: `npm run build` 성공 (Next.js build 출력 기준 50 routes)

이 수치는 실행 시점의 기록이며 이후 변경에서는 새 실행 결과를 우선한다.

## 6. 문서·SQL 사용 기준

- `docs/sql`은 단계별 수동 DDL·backfill·검증 참고본이다. 전체 파일을 새 DB에 순서 없이 일괄 실행하지 않는다.
- Settlement 신규 테이블은 `settlements`, `settlement_ledger_entries`이며 `SettlementItem`은 없다.
- 빠르게 변하는 테스트 개수와 정적 page 개수는 이 문서에서 고정하지 않는다. 변경 시 관련 테스트와 Frontend type/lint/build를 실제로 실행해 결과를 확인한다.
- 앞으로 할 일은 [`ROADMAP.md`](./ROADMAP.md)에만 요약하고, 상세 설계는 각 도메인 문서에 둔다.
