# 문서 최신화 변경 요약

기준 소스: 사용자가 2026-08-20 제공한 최신 `gift-market.zip` 실제 코드.

## 교체 대상

- `AGENTS.md`
- `docs/DEVELOPMENT_STATUS.md`
- `docs/ORDER_CANCELLATION_REFUND_DESIGN.md`
- `docs/PAYMENT_ARCHITECTURE_DESIGN.md`

## 주요 정정

- `/api/orders/**`, `/api/payments/**`, `/api/addresses/**`가 authenticated가 아니라는 오래된 AGENTS 설명 제거
- Payment READY 자동 만료가 미구현이라는 오래된 설명 제거
- CONFIRMING reconciliation 미구현 설명 제거
- Toss webhook 미구현 설명 제거
- 전체취소/PaymentCancellation 미구현 설명 제거
- 부분취소/부분환불 미구현 설명 제거
- `PaymentStatus.PARTIALLY_CANCELED` 현재 구현 반영
- `PaymentCancellationType.FULL/PARTIAL` 현재 구현 반영
- Toss `cancelAmount`, `PARTIAL_CANCELED`, `isPartialCancelable`, `cancels[]` 처리 반영
- 부분 재고복원 `restoreCancellationItems(...)` 반영
- PARTIAL reconciliation/webhook/orphan recovery 반영
- 구매자/판매자 cancellation UI 완료 반영
- SHIPPED/DELIVERED 반품·교환은 미구현으로 유지
- 운영 전 공개 staging + 상점용 Toss test key 통합 검증 TODO 유지
- versioned DB migration 도입 TODO 유지

## 검증 제한

문서 최신화 중 Backend 테스트를 다시 실행하려 했으나, 현재 실행 환경에 Gradle 9.5.1 배포본이 캐시되어 있지 않고 외부 네트워크가 차단되어 wrapper download 단계에서 실패했다. 코드 테스트 실패로 판단하지 않았다.
