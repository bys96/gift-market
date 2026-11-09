# 문서 동기화 기록

## 2026-11-09 전체 문서·소스 대조

이번 작업은 기존 Markdown 문서만 수정했다. Java·TypeScript·SQL·Docker·GitHub Actions·환경설정과 Git commit/push는 변경하거나 실행하지 않았다. `.env`·로컬 credential·OS 환경변수·배포 secret은 읽지 않았다. 테스트·빌드·운영 API·AWS/Vercel 상태를 새로 실행·조회하지 않았다.

### 범위와 확인 근거

| 문서 | 점검·정리 내용 |
| --- | --- |
| [README](./README.md) | 기술·검색 구조, 배포 책임, 환경변수 이름/용도, 구현·운영 검증 구분 |
| [DEVELOPMENT_STATUS](./docs/DEVELOPMENT_STATUS.md) | 완료/부분 구현/미구현/확인 필요 기준, 근거 경로, 최근 검색·헤더 변경, 과거 테스트 기록 |
| [ROADMAP](./docs/ROADMAP.md) | 주문·결제→선물하기→운영센터→보안→배포·관측·백업→모바일 순서와 미검증/미구현 구분 |
| [TROUBLESHOOTING](./docs/TROUBLESHOOTING.md) | 과거 운영 기록의 시점·증거 범위, 헤더 상태 유지 원인과 수정 |
| [MEILISEARCH_OPERATIONS](./docs/MEILISEARCH_OPERATIONS.md) | 준비 상태 유지·제외 상품 비교, 사전 자동 적용, 기존 fallback 유지, 과거 자원 수치 정리 |
| [SEARCH_VERIFICATION](./docs/SEARCH_VERIFICATION.md) | 일반/자동완성 요청 경로, 추천 검색어 전용 UI, URL 동기화·history 모의 검증과 실브라우저 검증 구분 |
| [PAYMENT_ARCHITECTURE_DESIGN](./docs/PAYMENT_ARCHITECTURE_DESIGN.md) | 준비 주문 snapshot 재검증, 선물 수신 흐름과 구분, 운영 E2E 미확인 표시 |
| [ORDER_CANCELLATION_REFUND_DESIGN](./docs/ORDER_CANCELLATION_REFUND_DESIGN.md) | 기존 부분취소·환불 구현 보존, 관리자 조회와 미구현 중재 분리 |
| [ORDER_RETURN_EXCHANGE_DESIGN](./docs/ORDER_RETURN_EXCHANGE_DESIGN.md) | 기존 Buyer/Seller 구현 보존, 교환배송비 API 보완, 과거 backfill/E2E와 현재 운영 분리 |
| [SETTLEMENT_V1](./docs/SETTLEMENT_V1.md) | 원장·금액 확정과 미구현 자동 생성·지급 구분 |
| [NOTIFICATION_DESIGN](./docs/NOTIFICATION_DESIGN.md) | ADMIN/SUPER_ADMIN 수신자, pathname 변경 조회와 중복 설명 정리 |
| [PRODUCT_DESCRIPTION_MEDIA](./docs/PRODUCT_DESCRIPTION_MEDIA.md) | 구현된 key/업로드 정책과 운영 파일 이전·실기기 재생 미검증 구분 |
| [admin-operation-policy](./docs/admin-operation-policy.md) | 현재 command 범위와 미래 중재 API 예시 구분 |
| Backend HELP (`giftmarket-api/HELP.md`) | 로컬에 존재하는 Git ignore 대상 Initializr 템플릿. 참고 자료로 점검했으며 변경·추적 추가하지 않음. 현재 실행 안내는 README를 사용 |
| [AGENTS](./AGENTS.md) | 문서 역할·보안·소유권 규칙 확인. 기능 상태 중복 관리를 피하기 위해 변경하지 않음 |
| 이 파일 | 현재 점검과 아래 과거 기록 구분 |

실제 Controller/Service/Entity/DTO·Frontend page/lib/store·테스트 및 안전한 example을 대조했다. 독립 ERD/API 명세 파일은 없으며 기존 도메인 설계 문서의 관계·계약을 유지했다. 선물 상황 분류와 일반 수령인 snapshot은 구현돼 있지만 선물 전용 수락·거절·주소 입력 링크/만료 흐름은 확인되지 않아 미구현으로 남겼다. 반품 가능 수량 문서에서 빠진 완료 교환 누계는 실제 `ReturnRequestService` 기준으로 보완했으며, 주문 snapshot 비교는 Frontend와 준비 갱신 API의 역할을 구분했다.

### 배포·검증의 제한

- API Actions는 main의 `giftmarket-api/**` push/수동 실행으로 OIDC/SSM을 통해 EC2 외부 스크립트를 호출한다. workflow 자체에 테스트 게이트는 없고 Dockerfile도 테스트를 제외한다. 원격 스크립트 동작을 추정하지 않는다.
- Vercel Frontend 배포는 별도다. 저장소의 `next.config.ts`로 rewrite를 확인했지만 Git 연결·build 설정·최신 배포 commit은 확인 필요다.
- API/MySQL 운영 Compose·배포/백업 스크립트는 저장소 밖이다. 검색 opt-in Compose를 운영 전체 스택으로 오인하지 않는다. 과거 백업·복구·OAuth·PG 성공은 현재 확인 완료가 아니다.
- Gemini 임베딩 관련 실행 코드·의존성이 없는 현재 상태와 Meilisearch 구현을 기록했다. 과거 임베딩 테이블/secret의 실제 제거는 확인하지 않았다.
- 문서 16개를 조사하고 추적 중인 기존 Markdown 14개를 수정했다. 로컬 Markdown 링크 76개와 명시된 저장소 파일 경로 66개, 링크 앵커와 코드 블록 닫힘을 검사해 오류가 없었다. 외부 HTTP 링크 28개와 원격 서버 경로의 실존은 이번 검사 대상이 아니다. 문서 외 추적 파일 변경이 없음을 확인했다. 문서 변경만 수행하므로 애플리케이션 테스트·빌드는 재실행하지 않았다.

아래 내용은 각 날짜의 작업 이력이며 현재 구현 현황은 위 링크와 실제 코드를 기준으로 한다.

## 2026-10-08 문서 동기화 (과거 기록)

> 기준: 현재 저장소의 Backend/Frontend 코드, 안전한 example 설정, 수동 SQL과 사용자가 제공한 AWS Production 운영 현황. 실제 `.env`, credential, secret, token, key 값은 확인하지 않았다.

### 변경 문서와 이유

- `README.md`: 현재 기능·기술 스택과 Vercel → AWS EC2/Nginx → Spring Boot/MySQL, AWS S3 운영 구조로 갱신
- `docs/DEVELOPMENT_STATUS.md`: Notification, Settlement v1, SellerStore, 회원 탈퇴, SUPER_ADMIN과 AWS 운영 상태 반영
- `docs/ROADMAP.md`: Gift Occasion, 메인·추천, 쿠폰·포인트, Payout, Settlement Scheduler, Seller Review 답글과 운영 과제를 미구현 범위로 정리
- `docs/*_DESIGN.md`: Storage Provider/Production S3 표현, 현재 운영 회귀 기준, `ADMIN`/`SUPER_ADMIN` 권한과 실제 enum 명칭 보정
- `docs/TROUBLESHOOTING.md`: Render/Aiven을 과거 이력으로 명확히 하고 AWS EC2·MySQL·Nginx·HTTPS·Vercel proxy 이전의 재현 가능한 핵심 내용을 추가
- `docs/sql/*.sql`: 현재 인프라와 무관한 Render 전용 rollout 표현을 일반적인 구버전 인스턴스 종료 조건으로 보정

### 코드 대조에서 확인한 핵심

- Settlement는 `SellerOrder → SettlementLedgerEntry → Settlement` 계산·관리까지 구현됐고 실제 Payout과 Scheduler는 없다.
- Storage object key는 `profiles/{userId}`, `products/{sellerId}`, `returns/{userId}`, `exchanges/{userId}`, `reviews/{userId}`, `stores/{sellerId}` 계열이며 DB에는 key를 저장하고 presigned URL은 저장하지 않는다.
- 당시 기록에는 Gift Occasion 및 Seller Review 답글이 없다고 남아 있다. 현재 Gift Occasion은 구현돼 있으며 Seller Review 답글은 미구현이다. 이 문장을 현재 기능 판단에 사용하지 않는다.
- 관리자 공통 API는 `ADMIN`과 `SUPER_ADMIN`을 허용하고, 관리자 권한 부여·회수 API만 `SUPER_ADMIN` 전용이다.

### 2026-10-08 회귀 검증

- Backend: **959 tests / 959 success**
- Frontend lint: 성공
- Frontend `npx tsc --noEmit`: 성공
- Frontend production build: 성공 (build 출력 기준 50 routes)

### 운영 DB 자동 백업 후속 동기화

- EC2 MySQL의 `mysqldump → gzip → S3` 자동 백업을 구현 완료 상태로 반영
- systemd 매일 04:00 KST 실행과 `Persistent=true` 기록
- `backups/mysql/` 전용 IAM 최소 권한과 S3 14일 rolling retention 기록
- S3 upload/download, `gzip -t`, 별도 MySQL 8.4 컨테이너 restore 검증 완료 기록
- `ROADMAP.md`에서 DB 자동 백업과 restore 검증 TODO 제거

---

## 2026-08-28 문서 동기화 기록 (보관)

> 기록일: 2026-08-28. 이 파일은 당시 작업 이력이며 현재 구현 현황 문서가 아니다. 현재 상태는 `docs/DEVELOPMENT_STATUS.md`와 실제 코드를 우선한다.
>
> 기준: 사용자가 2026-08-28 제공한 최신 `gift-market.zip` 실제 코드 + 같은 날 완료 보고된 회귀 검증 결과.
> 문서와 코드가 충돌하면 실제 코드가 우선한다.

## 교체 대상

- `AGENTS.md`
- `docs/DEVELOPMENT_STATUS.md`
- `docs/ORDER_CANCELLATION_REFUND_DESIGN.md`
- `docs/ORDER_RETURN_EXCHANGE_DESIGN.md`
- `docs/PAYMENT_ARCHITECTURE_DESIGN.md`
- `docs/TROUBLESHOOTING.md`
- `DOCS_UPDATE_NOTES.md`

## 2026-08-28 주요 최신화

### Seller / ADMIN

- ADMIN도 Seller 등록 가능
- ADMIN은 Seller 등록 폼을 동일하게 사용하되 신청 transaction에서 자동 APPROVED
- ADMIN role 유지 + ACTIVE Seller 생성
- 일반 관리자 승인과 ADMIN 자동승인은 `SellerApprovalService` 공통 primitive 사용
- Seller Center 최종 접근 기준은 role이 아니라 ACTIVE Seller 존재 여부
- `/api/seller/**`, `/api/sellers/**`는 authenticated 후 Service에서 ACTIVE Seller/ownership 검증
- 기존 `ADMIN + ACTIVE Seller` DB row 호환
- redirect loop 및 Backend 403 정책 불일치 해결 반영

### Pagination / 조회 UX

- 공통 Pagination: `<< < 숫자 최대 5개 > >>`
- 0-based, URL Link/local state/summary/scroll 호환 유지
- 리뷰 전체 page 번호 override 제거
- loading/API error를 `0건`, `0.0점`으로 오인하지 않게 수정
- Buyer 상품문의 삭제 후 사라진 마지막 page 자동 보정

### 주문 / Claim 정합성

- `confirmedQuantity`를 Cancellation/Return/Exchange 가능 수량에서 제외하도록 문서 공식 보정
- 완료 Exchange 수량은 최종 보유 수량으로 구매확정 가능하다는 기존 정책 유지
- Return/Exchange는 구현 완료 상태 유지
- PaymentCancellation PARTIAL, ExchangeShippingPayment, Shipment 1:N 구조 유지

### 테스트 / 빌드 기준

2026-08-28 당시 작업 보고 기준 (현재 검증 결과 아님):

- Backend: **511 tests / 511 success**
- Frontend lint: 성공
- `npx tsc --noEmit`: 성공
- Frontend build: 성공
- Next.js 정적 페이지: **34개**

이 숫자는 이후 변경 시 실제 실행 결과를 우선한다.

## TROUBLESHOOTING 추가

- ADMIN Seller redirect loop / 403 권한 불일치
- Pagination page 번호 과다 렌더링
- API 실패를 0건/0.0으로 오인
- Buyer 문의 삭제 후 invalid page
- `.next` stale cache의 CSS module resolution 사례
- 상품 상세 새 진입 scroll / 목록 back-scroll 경계
- Profile objectKey 사용자 prefix 보안
- localStorage Wishlist의 사용자 간 공유/노후화 문제와 Backend 이전
- `NEXT_PUBLIC_STORAGE_BASE_URL` 누락 시 이미지 설정 오류가 숨겨지는 미해결 후속

## 당시 배포 전 TODO (현재 목록 아님)

- 공개 HTTPS staging
- 실제 상점용 Toss test key/webhook 외부 회귀
- SELLER 귀책 Exchange 실제 E2E
- timeout/5xx 실제 장애 E2E
- MinIO 외부 endpoint/bucket/CORS/영속성
- production versioned DB migration
- 운영 log/metric/alert/runbook
- Storage base URL 설정 오류 관측성
- Modal 접근성 / Seller Sidebar 모바일 UX
- support/terms/privacy 실제 운영 정보 확정

## 보안

문서 최신화 및 코드 점검 시 실제 `.env`, secret, API key, token, credential은 읽거나 출력하지 않는다.
