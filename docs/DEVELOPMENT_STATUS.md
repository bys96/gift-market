# Gift Market 개발 현황

> 기준: 현재 저장소의 Backend·Frontend·설정·SQL. 문서와 코드가 충돌하면 코드가 우선한다. 운영 배포 여부와 외부 서비스의 실제 상태는 저장소만으로 재검증할 수 없다.

## 완료된 기능

- 인증·회원: Google OIDC/Kakao OAuth2, JWT Access Token, HttpOnly Refresh Token cookie, rotation·이전 토큰 10초 grace·행 잠금, 프로필·이미지, 배송지 CRUD, 회원 탈퇴 및 OAuth 재가입 시 새 사용자 생성.
- 판매자: 신청·관리자 승인, ADMIN 자기 신청 자동 승인, `Seller`와 1:1 `SellerStore`, 스토어 정보·이미지·고객센터 정보 설정, Seller Center Dashboard·상품·주문·클레임·문의 관리.
- 상품·콘텐츠: 카테고리, 상품 CRUD·임시저장, 옵션/Variant·재고, 비활성 Variant 보존, 상품 이미지와 상세 이미지/MP4, MinIO 또는 S3 provider의 presigned 업로드, 상품 목록·상세·검색, 회원별 Wishlist·Cart.
- 거래: 멀티셀러 `Order → SellerOrder → OrderItem`, 주문 당시 가격·배송비 snapshot, Toss 승인·웹훅·결과 불명 reconciliation, 최초 출고·반품 회수·교환 회수/재배송 `Shipment`.
- 클레임: 전체취소와 상품·수량 부분취소/환불, 구매자 취소·판매자 직접취소, 승인/거절, 부분 재고복원, 반품 요청·회수·검수·환불·완료, 동일가격 교환·target 재고 예약/해제/소비·교환배송비 추가결제, 구매확정 수량과 리뷰.
- 소통·운영: 상품문의/답변, 구매확정 기반 리뷰/이미지, BUYER·SELLER·ADMIN 알림과 읽음 API/UI, 관리자 회원 정지/해제·판매 정지/해제·상품 숨김/해제·판매자 신청 처리 및 주문/클레임 조회.
- 정산 v1: 판매자 주문별 경제 원장, 결제·배송완료·취소·반품 연결, 수동 Settlement 생성·보류·해제·확정, 판매자 목록/상세/미정산 요약 및 관리자 목록/상세/상태 관리 API/UI. 실제 송금은 포함하지 않는다.

## 정산 v1의 현재 경계

상세 원장·집계·API 기준은 [`SETTLEMENT_V1.md`](./SETTLEMENT_V1.md)를 참조한다.

```text
Seller 1:N Settlement
SellerOrder 1:N SettlementLedgerEntry
Settlement 1:N SettlementLedgerEntry (ledger.settlement_id nullable)
```

- 결제 `PAID` 완료 트랜잭션에서 SellerOrder별 주문 snapshot으로 `SALE_PRODUCT`, 0원이 아닌 `SALE_SHIPPING`, 0원이 아닌 `COMMISSION`을 생성한다. 최초 `SALE_PRODUCT`에 당시 수수료율을 기록한다.
- `ORIGINAL_OUTBOUND` 배송완료 시 초기 원장의 `eligibleAt = deliveredAt + settlement.hold-days`를 확정한다. 배송 전 부분취소 원장도 배송완료 시 활성화한다.
- 성공한 `PaymentCancellation`에 대해서만 취소 `CANCELLATION_REFUND`와 필요 시 `COMMISSION_REVERSAL`을 기록한다. 완료된 반품은 실제 PG 환불액의 `RETURN_REFUND`(0원은 생략)와 필요 시 수수료 환입을 기록한다. 환입은 최초 수수료율 snapshot과 취소·반품 누적 상품환불액을 사용한다.
- `SettlementGenerationService`는 정산 가능·미귀속 원장을 잠금 조회하고 활성 취소·반품·교환 claim이 있는 SellerOrder 전체를 제외한다. `periodStart`는 원장 조회 하한이 아니며 이전 회차 미귀속분을 catch-up한다. 빈 결과는 Settlement를 만들지 않는다.
- 상태는 `READY → ON_HOLD → READY`, `READY → CONFIRMED`만 허용한다. `CONFIRMED`는 원장·금액이 확정된 최종 상태이지 **판매자 지급 완료가 아니다**. 사후 환불은 기존 확정분을 바꾸지 않고 다음 회차의 새 음수 원장으로 반영한다.
- 현재 생성 트리거는 ADMIN API/UI의 수동 `generate`다. 정기 Scheduler는 미구현이며 도입 시 계산을 복제하지 않고 `SettlementGenerationService`를 호출한다. 운영/장애 대응용 수동 API와 관리자 조회·보류·해제는 유지할 수 있다.

현재 HTTP 경계: 판매자는 `GET /api/seller/settlements`(선택 `status`, `page`, `size`), `/summary`, `/{settlementId}`로 자기 정산만 조회한다. 관리자는 `GET /api/admin/settlements`(선택 `sellerId`, `status`, `periodStart`, `periodEnd`, `page`, `size`), `/{settlementId}`와 `POST /generate`, `/{settlementId}/hold`, `/{settlementId}/release`, `/{settlementId}/confirm`을 사용한다. 생성 요청에는 seller·기간·cutoff만 받고 금액을 받지 않는다.

## 부분 구현·운영 검증 필요

- 회원 탈퇴는 진행 중 주문·클레임·판매자 주문을 검사하고 개인정보를 익명화하지만, `Settlement` 미확정/미귀속 원장 자체에 대한 탈퇴 차단은 코드에 없다. 정산 운영 정책을 정한 뒤 보완해야 한다.
- 관리자 주문·취소·반품·교환은 조회 중심이다. 관리자 강제 취소·환불·클레임 중재는 구현되지 않았다.
- `/support`, `/terms`, `/privacy`는 외부 테스트 안내 페이지이며 정식 사업자·연락처·법률 문안 확정이 남았다.
- 상품 상세 미디어의 오래된 절대 localhost URL과 저장소 object는 `PRODUCT_DESCRIPTION_MEDIA.md` 절차에 따라 실제 DB/object 확인 후 별도 이전해야 한다. 저장 취소·이탈 후 미참조 업로드 object 자동 정리도 없다.
- SELLER 귀책 교환 실제 E2E, PG timeout/5xx·웹훅·보상 흐름, 백업/복구, 접근성 및 모바일 UX는 배포 환경에서 별도 회귀 검증이 필요하다. 과거 로컬/E2E 기록을 최신 운영 검증으로 간주하지 않는다.

## 미구현·후순위

- 정산 정기 Scheduler, 별도 Payout/실제 송금·계좌/KYC·지급 실패/재시도.
- 쿠폰·포인트, 랭킹/추천 고도화, Seller 리뷰 답글, 관리자 강제 환불/클레임 중재 등 Backoffice 확장.
- 미참조 S3/MinIO object cleanup, Flyway/Liquibase 등 versioned migration 체계, 운영 지표·경보·runbook·정기 백업/복구 자동화.
- 홈은 실제 상품 API로 소수 상품을 보여주는 기본 화면이다. 개인화 추천/랭킹은 구현되지 않았다.

`docs/sql`의 파일은 설계 단계별 수동 DDL·backfill·검증 이력이다. 새 환경에 일괄 실행하지 않는다. 현재 Entity/운영 스키마를 확인해 필요한 파일만 적용한다. 정산의 신규 테이블은 `settlements`, `settlement_ledger_entries` 두 개이며 `SettlementItem`은 없다.

## 운영·배포 설정의 코드 기준

- Backend: Java 21, Spring Boot 4.1.0, Spring Security/JPA, MySQL, Toss, Actuator. `Dockerfile`은 layered JAR와 AppCDS archive를 준비한다. `/health`는 인증 없이 `UP`을 반환한다.
- 배포 구조는 사용자 보고 기준 Vercel Frontend / Render Backend / MySQL / 외부 object storage다. Frontend는 Next.js 16.2.11 App Router/React 19/TypeScript/CSS. `next.config.ts`는 production의 `BACKEND_API_ORIGIN`이 있을 때 `/api`, `/oauth2`, `/login/oauth2`를 같은 origin 경로에서 Backend로 rewrite한다. OAuth redirect, CORS, Refresh Cookie의 Secure/SameSite 및 forwarded header 설정은 실제 배포 환경과 함께 검증해야 한다.
- 객체 저장소는 `storage.provider`로 MinIO(샘플 기본값) 또는 S3를 선택한다. 선택 변경은 기존 object 이전 기능이 아니다.
- `application*.yaml`은 `DB_URL`, `JPA_DDL_AUTO` 등 환경변수 바인딩을 사용하고 개발 기본값은 `ddl-auto=update`다. 운영은 별도 환경변수에서 `validate`로 설정해야 한다. `docs/sql/*.sql`은 자동 migration이 아닌 수동 DDL/backfill 참고본이며 운영에서 실제 적용 여부는 DB와 대조해야 한다.
- 프로젝트의 `LocalDateTime` + MySQL `DATETIME(6)`는 KST 기준 운영 설정을 전제한다. JVM `-Duser.timezone=Asia/Seoul`, JDBC `connectionTimeZone=%2B09:00&forceConnectionTimeZoneToSession=true`는 배포 환경에서 맞춰야 하며, 실제 `DB_URL`이나 Secret 값은 저장소 문서에 기록하지 않는다. 해결 이력은 `TROUBLESHOOTING.md`를 참조한다.
- `application-example.yaml`의 주석형 로컬 DB URL 예시는 아직 `serverTimezone=Asia/Seoul` 표기를 사용한다. 운영에서 확인된 JDBC 설정과 혼동하지 말아야 하며 샘플 정리는 코드/설정 변경 작업으로 별도 처리한다.
- 사용자 보고 기준으로 Settlement v1 Phase 1~7은 배포 완료됐다. 저장소의 Docker/Next/Spring 설정은 배포 구성을 설명하지만 Render/Vercel 콘솔 값과 운영 DB 스키마의 실제 상태는 이 문서 감사로 확인하지 않았다.

## 빠른 검증 기준

- Backend: 변경 범위에 맞는 테스트를 우선 실행하고 필요할 때 `./gradlew test`를 실행한다. 테스트 개수를 문서 계약으로 고정하지 않는다.
- Frontend: `npx tsc --noEmit`, `npm run lint`, `npm run build`.
- 배포: DB 백업 및 대상 스키마 확인 → 필요한 수동 SQL 선적용 → `ddl-auto=validate` Backend 배포 → health·OAuth·Toss·스토리지·정산 E2E 확인. `docs/sql` 전체를 일괄 실행하지 않는다.
