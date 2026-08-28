# Gift Market 개발 현황

> 최종 갱신: 2026-08-28
>
> 이 문서는 현재 저장소의 실제 코드를 기준으로 한 배포 준비 기준점이다. 문서와 코드가 충돌하면 실제 코드가 우선한다.

## 1. 현재 요약

Gift Market의 구매자·판매자 핵심 commerce workflow가 구현되어 있다.

- 인증/JWT/OAuth, 판매자 신청·승인
- 상품·옵션·Variant, 장바구니, 회원별 Backend Wishlist, 상품문의 Q&A
- 주문, SellerOrder, Shipment, Toss 결제
- 전체취소, 상품·수량 부분취소와 부분환불
- Return 구매자·판매자 전체 workflow
- Exchange 구매자·판매자 전체 workflow와 교환배송비 추가결제
- MinIO 상품·프로필·Return/Exchange 증빙 이미지
- Buyer/Seller 주요 Frontend와 모바일 대응

현재 기능 개발 기준으로 Return과 Exchange를 미구현 범위로 취급하지 않는다. 남은 큰 범위는 운영환경 분리, migration 전략, staging 배포와 외부 연동 회귀 검증이다.

## 2. 기술 기준

### Backend

- Java 21
- Spring Boot 4.1.0
- Spring Security, OAuth2/OIDC, JWT
- Spring Data JPA, MySQL
- MinIO
- Toss Payments
- Gradle

### Frontend

- Next.js 16.2.11 App Router
- React 19.2.4
- TypeScript
- Zustand
- TanStack Query dependency
- Tiptap
- 일반 CSS 기반 UI
- Tailwind dependency/import는 존재하지만 신규 UI에는 utility class를 사용하지 않음

## 3. 구현 완료 상태

### 인증 / 회원 / 판매자

- Google OAuth/OIDC, Kakao OAuth
- JWT Access Token과 Refresh Token cookie
- 프로필, 배송지, 회원별 Backend Wishlist API와 Frontend 서버 동기화
- 신규 프로필 이미지는 `profiles/{userId}/{uuid}` key만 저장·삭제할 수 있으며, 기존 `profile/{uuid}` key는 조회 호환만 유지하고 자동 삭제하지 않음
- `ProductInquiry 1:0..1 ProductInquiryAnswer` 기반 Buyer 상품문의와 Seller 답변 관리
- 답변 후 Buyer 수정은 차단하되 삭제는 허용하며, 문의 soft delete 후에도 Answer 이력은 보존
- 구매확정 기반 Buyer 리뷰 구현: OrderItem당 활성 리뷰 1개, 삭제 후 동일 행 복구 재작성
- 완료 교환이 있으면 `completedAt DESC, id DESC` 최신 target 상품/Variant snapshot을 리뷰 대상으로 저장
- 리뷰 본문/1~5 정수 별점/이미지 0~5장, soft delete, 상품별 최신순 pagination 및 활성 리뷰 평균·개수 집계
- Review 이미지는 `reviews/{userId}/` objectKey만 DB에 저장하고 공개 상품 리뷰 조회 시 단기 presigned GET URL로 제공
- 판매자 신청, 관리자 승인, SELLER 권한
- ADMIN도 별도 Seller 등록이 가능하며, 동일 등록 폼 제출 후 Backend에서 같은 transaction으로 자동 APPROVED + ACTIVE Seller 생성. ADMIN role은 유지
- Seller Center 접근의 최종 기준은 role이 아니라 `/api/sellers/me`의 ACTIVE Seller 여부이며, Backend Seller API는 인증 후 Service에서 ACTIVE Seller/ownership을 검증
- 관리자 판매자 신청 목록은 `page`/`size` 기반 server pagination이며 `createdAt DESC, id DESC`로 정렬
- 판매자센터와 상품·주문·클레임 관리
- Seller Dashboard 실데이터 집계와 처리 필요 업무 Action Center
- Seller Center redirect loop 및 ADMIN Seller API 403 불일치 정리

### 상품 / 옵션 / Variant

- 상품 등록·수정, 이미지, 판매상태와 재고
- 옵션 그룹·값과 Variant 조합 편집
- 제거된 조합은 `ProductVariant`를 물리 삭제하지 않고 `active=false`로 보존
- 과거 `OrderItem.variant` 참조와 `optionSnapshot` 유지
- Buyer 상품 조회에는 active Variant만 노출
- Buyer 상품 목록은 URL에서 페이지당 20/50/100개 선택을 유지하며 Backend는 최대 100개로 제한
- 공통 `Pagination`은 `<< < 숫자 최대 5개 > >>` 정책으로 통일하며 URL Link/local state/summary/scroll 동작을 유지
- Product 총재고는 active Variant 재고 합계로 동기화
- 현재 옵션 구조와 같은 `combinationKey`의 inactive Variant는 기존 ID로 재활성화
- `(product_id, combination_key)` unique로 중복 조합 방지
- Seller 편집 화면에서 active/inactive Variant를 구분하고 옵션 그룹·값 제거 및 재활성화 지원

### 주문 / 배송 / 결제

- Order 한 건 아래 SellerOrder별 주문 처리
- `SellerOrder 1:N Shipment`
- `ORIGINAL_OUTBOUND`, `RETURN_COLLECTION`, `EXCHANGE_COLLECTION`, `EXCHANGE_OUTBOUND`
- 주문 prepare 멱등성, 재고 예약 차감, READY 만료와 재고 복원
- Toss 승인, CONFIRMING 결과 불명 reconciliation, webhook 중복 방지
- 전체취소와 CANCELING reconciliation
- 부분취소·부분환불, 환불 잔액, 부분 재고복원과 orphan recovery
- Buyer 주문 목록은 `page`/`size` 기반 server pagination이며, `orderedAt DESC, id DESC`로 정렬하고 현재 page의 OrderItem/SellerOrder만 batch 조회

### Return

```text
REQUESTED → APPROVED → COLLECTING → RECEIVED → INSPECTED → REFUNDING → COMPLETED
REQUESTED → REJECTED
```

- 구매자 부분수량 요청, ownership·기간·가용수량·멱등성 검증
- 판매자 승인/거절, OTHER 귀책 확정, 회수·입고·검수
- 주문 snapshot 기반 환불액과 배송비 계산
- `PaymentCancellation(PARTIAL)` 기반 Toss 환불과 결과 불명 reconciliation
- RESTOCKABLE 원 상품만 재고 복원
- `returnedQuantity`, `restockedQuantity`, completion recovery
- 증빙 이미지 0~5장과 Buyer/Seller 이미지 조회
- Buyer/Seller Frontend 및 정상 E2E 완료

### Exchange

```text
REQUESTED
→ 판매자 승인 및 target reservation
→ BUYER: PAYMENT_PENDING → 배송비 결제 → COLLECTING
→ SELLER: COLLECTING
→ RECEIVED → INSPECTED → RESHIPPING → COMPLETED

REQUESTED → REJECTED
PAYMENT_PENDING 24시간 미결제 → CANCELED + reservation release
```

- 동일 Product의 현재 판매단가가 원 `OrderItem.unitPrice`와 같은 target만 허용
- 신청 시 target 상태·가격·재고 사전검사, 승인 시 재검증·잠금·실제 reservation
- Return/Exchange 활성 수량과 완료된 canceled/returned/exchanged 수량 교차 검증
- target `reservedQuantity / releasedQuantity / consumedQuantity` 추적
- BUYER 귀책은 `ExchangeShippingPayment` 1:1 추가결제, SELLER 귀책은 추가결제 없음
- 0원 결제 추적, REQUESTED 결과 불명 reconciliation, FAILED 새 attempt, 24시간 만료
- 만료 뒤 늦은 성공은 `COMPENSATION_REQUIRED`로 분리
- `EXCHANGE_COLLECTION` 회수, 입고, RESTOCKABLE/NON_RESTOCKABLE 검수
- RESTOCKABLE 원 상품 재고 복원
- `EXCHANGE_OUTBOUND` 생성 시 reservation consume, 완료 시 `exchangedQuantity` 반영
- 증빙 이미지 0~5장, Buyer/Seller Frontend, 상태 timeline과 Toss callback

## 4. 실제 검증 기준점

### Backend

- 구매확정: 배송 완료 `OrderItem`의 현재 확정 가능 수량 전체를 Buyer가 확정하며, `confirmedQuantity`를 이후 취소·반품·교환 가능 수량에서 제외
- 완료 교환 수량은 최종 보유 수량으로 구매확정 가능하고, 진행 중 취소·반품·교환 수량은 확정 대상에서 제외
- 최신 작업 보고 기준 전체 suite: **511 tests / 511 success / 0 failure / 0 error**
- Return/Exchange 수량 교차 점유, reservation/release/consume, Payment reconciliation과 기존 주문 참조 회귀를 포함

### Frontend

- `npm run lint`: **0 errors / 0 warnings**
- `npx tsc --noEmit`: 성공
- `npm run build`: 성공
- Next.js 정적 페이지 **34개** 생성 성공
- 공통 Pagination/조회 상태 UX 수정 후 lint/tsc/build 재검증 성공
- `/products`, `/login`, `/order`, `/seller/products/new`의 `useSearchParams` 렌더링 경로는 Suspense boundary 적용 완료

### 실제 E2E

- Return 정상 요청 → 승인 → 회수 → 입고 → 검수 → 환불 → 완료 확인
- Exchange BUYER 귀책, 다른 동일가격 Variant 교환 확인
- 판매자 승인과 target reservation 확인
- Toss 교환배송비 6,000원 실제 결제 SUCCEEDED 확인
- 회수 Shipment → 입고 → RESTOCKABLE 검수 → 원 재고 복원 확인
- 재배송 Shipment → reservation consume → `exchangedQuantity` → COMPLETED 확인

아직 완료로 기록하지 않는 범위:

- SELLER 귀책 Exchange 실제 E2E
- timeout/5xx를 실제로 유발한 외부 장애 E2E
- 공개 staging/production 외부환경 전체 회귀

## 5. DB / SQL 기준

- 현재 개발 설정은 Hibernate `ddl-auto:update`를 사용한다.
- `docs/sql/*.sql`은 자동 실행 migration이 아니라 개발 DB 확인·backfill·수동 DDL 참고본이다.
- 새 환경은 schema 생성 후 `docs/sql/category-seed.sql`을 적용해야 Seller 상품 등록과 Buyer 카테고리 필터에 사용할 최소 활성 카테고리가 생성된다.
- Hibernate가 이미 반영한 변경을 같은 SQL로 중복 실행하지 않는다.
- 운영 배포 전 Flyway/Liquibase 등 versioned migration 전략을 확정해야 한다.
- 이번 기준점에서는 schema나 production profile을 변경하지 않는다.

## 6. 배포 전 남은 작업

1. development/staging/production profile과 환경변수 분리
2. Secret, cookie secure/SameSite, CORS, OAuth redirect URI 점검
3. localhost hardcoding과 Frontend API/Storage URL 외부환경 설정 점검
4. Toss 상점용 테스트 키·webhook을 사용한 공개 HTTPS staging 회귀
5. MinIO endpoint/bucket/CORS와 외부 영속 스토리지 운영 설정
6. production DB versioned migration 및 backup/rollback 전략
7. Security review와 관리자 결제·환불 관측/수동 대응 정책
8. EC2 등 staging 배포 후 Return/Exchange 포함 전체 E2E
9. 운영 로그·지표·경보·백업과 장애 runbook
10. SELLER 귀책 Exchange 및 실제 timeout/5xx 보상 흐름 E2E
11. `NEXT_PUBLIC_STORAGE_BASE_URL` 누락/오설정을 단순 이미지 없음으로 숨기지 않도록 운영 설정 오류 관측성 개선
12. Modal 키보드 접근성(Escape/focus)과 Seller Sidebar 모바일 UX 최종 점검
13. `/support`, `/terms`, `/privacy`의 실제 사업자/문의/개인정보 담당 정보 배포 전 확정

## 7. 운영 전 주의사항

- Frontend 금액·재고를 최종 신뢰하지 않는다.
- PG timeout/5xx를 실패로 단정하지 않는다.
- Secret/API key/token을 코드·문서·로그에 기록하지 않는다.
- 기존 주문 Payment, PaymentCancellation, ExchangeShippingPayment의 역할을 섞지 않는다.
- ProductVariant와 과거 주문 참조를 물리 삭제하지 않는다.
- 운영환경에서 `ddl-auto:update`를 migration 전략으로 사용하지 않는다.


## 8. 2026-08-28 마감 변경

- ADMIN Seller 미등록 시 일반 Seller 등록 폼을 사용하며 ADMIN 신청은 자동 승인한다.
- 일반 관리자 승인과 ADMIN 자동승인은 `SellerApprovalService` 공통 primitive를 사용한다.
- `/api/seller/**`, `/api/sellers/**`는 authenticated matcher로 통과하고 실제 ACTIVE Seller/ownership 검증은 Service가 담당한다.
- 공통 Pagination의 기본 page window는 최대 5개이며 처음/이전/다음/마지막 이동을 제공한다.
- 리뷰, Buyer/Seller 문의, Admin 판매자 신청의 loading/error 숫자 표시는 실제 0과 구분한다.
- Buyer 상품문의 삭제로 현재 페이지가 사라지는 경우 마지막 유효 페이지로 보정한다.
- Backend schema/API 변경 없이 Frontend 마감 작업은 lint/TypeScript/build를 통과했다.
