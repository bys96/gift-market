# Gift Market 개발 현황

> 이 문서는 현재 Backend, Frontend, 설정과 수동 SQL을 기준으로 한 구현 상태다. 문서와 코드가 충돌하면 코드가 최종 기준이다. 향후 작업은 [`ROADMAP.md`](./ROADMAP.md)에서 관리한다.

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

- Category, Product CRUD, 판매 상태, 대표·상세 이미지와 MP4 상세 미디어
- 옵션 그룹·옵션 값·Variant 조합과 재고
- 과거 주문 참조를 위한 inactive Variant 보존과 같은 조합 재활성화
- 구매자 상품 목록·상세·검색·카테고리·품절 제외·URL 기반 pagination/filter
- 회원별 Wishlist와 Cart
- 상품문의 작성·수정·삭제, 비밀문의 masking, 판매자 답변 등록·수정
- 구매확정된 OrderItem 기준 리뷰, 이미지, soft delete 후 같은 row 재사용

### 주문·결제·배송

- `Order -> SellerOrder -> OrderItem` 다중 판매자 구조
- 주문 시 상품명·옵션·가격·배송비 snapshot
- Toss 결제 승인, webhook, READY 만료와 CONFIRMING reconciliation
- 최초 배송, 반품 회수, 교환 회수·재배송을 구분하는 `SellerOrder 1:N Shipment`
- 구매확정 수량과 리뷰 작성 자격 연결
- 결제창을 닫은 뒤 배송정보가 바뀌면 READY/PENDING_PAYMENT 주문 snapshot을 갱신하고 기존 Payment를 안전하게 재사용
- 상품·옵션·수량·금액 snapshot이 달라지면 stale 준비 주문을 취소하고 예약 재고를 복원한 뒤 새로 준비
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

## 3. 부분 구현·현재 불일치

- 회원 탈퇴는 진행 중 구매·판매 업무를 차단하지만, Seller의 미귀속 ledger나 미확정 Settlement를 직접 검사하는 정산 guard는 없다.
- `/terms`, `/privacy`, `/support` route는 존재하지만 운영 법무·고객지원 문구 확정이 필요하다.
- 상세 미디어와 프로필 등 일부 object는 실패·이탈 시 orphan cleanup이 완전 자동화되어 있지 않다.

## 4. 운영·배포 현재 기준

- Backend: Java 21, Spring Boot 4.1.0, Spring Security/JPA, MySQL, Toss Payments, Actuator
- Frontend: Next.js 16.2.11 App Router, React 19, TypeScript, Zustand, 일반 CSS
- 배포 구조: Vercel Frontend, Render Backend, MySQL, S3-compatible object storage
- Frontend는 production에서 `BACKEND_API_ORIGIN` 기반 same-origin rewrite를 사용한다.
- Docker image는 Java 21 Dynamic AppCDS archive를 생성하고 runtime `JAVA_TOOL_OPTIONS`에서 archive를 사용한다.
- Render health check는 인증 없는 `GET /health`를 사용한다.
- production은 수동 DDL 선적용 후 `ddl-auto=validate`로 검증한다. `docs/sql/*.sql`은 자동 migration이 아니다.
- 개발 example의 기본 `JPA_DDL_AUTO`는 `update`이므로 production 설정과 혼동하지 않는다.
- 시간 정책은 `LocalDateTime + MySQL DATETIME(6)` KST wall-clock이다. 운영 JVM은 `-Duser.timezone=Asia/Seoul`, JDBC는 `connectionTimeZone=%2B09:00&forceConnectionTimeZoneToSession=true`를 사용하며 Hibernate `jdbc.time_zone`은 추가하지 않는다.
- timezone과 Render/AppCDS 문제 해결 기록은 [`TROUBLESHOOTING.md`](./TROUBLESHOOTING.md)에 보존한다.

## 5. 문서·SQL 사용 기준

- `docs/sql`은 단계별 수동 DDL·backfill·검증 참고본이다. 전체 파일을 새 DB에 순서 없이 일괄 실행하지 않는다.
- Settlement 신규 테이블은 `settlements`, `settlement_ledger_entries`이며 `SettlementItem`은 없다.
- 빠르게 변하는 테스트 개수와 정적 page 개수는 이 문서에서 고정하지 않는다. 변경 시 관련 테스트와 Frontend type/lint/build를 실제로 실행해 결과를 확인한다.
- 앞으로 할 일은 [`ROADMAP.md`](./ROADMAP.md)에만 요약하고, 상세 설계는 각 도메인 문서에 둔다.
