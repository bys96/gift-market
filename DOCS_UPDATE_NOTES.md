# 문서 최신화 변경 요약

> 갱신일: 2026-08-28
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

최신 작업 보고 기준:

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

## 여전히 배포 전 TODO

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
