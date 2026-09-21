# Gift Market Roadmap

> [`DEVELOPMENT_STATUS.md`](./DEVELOPMENT_STATUS.md)는 현재 구현 상태, 이 문서는 아직 구현하지 않은 후속 작업을 관리한다. 계획 항목을 완료 기능으로 해석하지 않는다.

## 다음 우선순위

- 메인 페이지 고도화와 상품 탐색 API 설계
  - 최근 순판매수량 기반 인기상품
  - 찜·리뷰 기반 상품 노출
  - 카테고리별 인기상품
  - 같은 카테고리 추천
- Settlement 자동 생성 Scheduler
  - 기존 `SettlementGenerationService` 재사용
  - 계산 로직을 Scheduler에 중복 구현하지 않음
  - ADMIN generate는 운영·장애 대응용 수동 트리거로 유지 가능
- Seller 회원 탈퇴 시 미귀속 ledger·미확정 Settlement guard 정책

## 중기

### 상품 탐색·추천

- 함께 구매한 상품: 현재 `OrderItem` 관계로 계산 가능하지만 데이터량과 self-join 비용을 확인한 뒤 도입
- Gift Occasion 분류
  - 생일, 감사, 축하, 집들이, 결혼, 출산, 응원 등
  - 현재 Product에는 일반 Category만 있으며 Gift Occasion Entity/API/UI는 없음
  - Product와 다대다 관계를 후보로 두되 검색·운영 정책을 먼저 설계

### 혜택

- 쿠폰
- 포인트

쿠폰과 포인트는 표시 기능만 추가할 수 없다. 주문금액 snapshot, 결제 승인, 전체·부분취소, 반품, 환불 잔액과 Settlement 원장에 미치는 영향을 포함해 별도 도메인으로 설계한다.

### Seller·Admin

- Seller 리뷰 관리와 답글
- Admin 강제 환불·클레임 중재 등 command형 운영 기능
- 단순 상태 UPDATE가 아니라 기존 결제·재고·클레임 불변식을 재사용하는 workflow로 구현

### 서비스 UI·문서

- 약관·개인정보처리방침·고객지원의 실제 운영 문구 확정
- global error/not-found와 운영 오류 안내 보강
- 남아 있는 browser `alert`/`confirm`을 기존 공통 Modal로 점진적으로 통일
- 접근성·실기기 모바일 회귀 점검

## 장기·운영

### Payout

Settlement와 별도 도메인으로 구현한다.

- 실제 판매자 송금
- 지급 계좌와 계좌 인증
- KYC·지급 검증
- `Payout`, `PayoutAttempt`
- 지급 실패·재시도와 provider 연동

`Settlement.CONFIRMED`는 계속 “정산 금액 확정”을 의미하며 지급 완료 상태로 재사용하지 않는다.

### 데이터·인프라

- Flyway/Liquibase 등 versioned DB migration 도입
- observability: 구조화 로그, metric, tracing, alert
- DB·object storage backup/recovery와 복구 훈련
- S3/MinIO orphan object 탐지·정리
- 운영 runbook과 장애 대응 절차 보강
- 상품 랭킹 트래픽 증가 시 캐시 또는 기간별 집계 테이블 검토
