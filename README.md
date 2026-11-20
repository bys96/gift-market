<div align="center">

# 🎁 Gift Market

### 선물을 고르는 경험부터 주문·결제·배송까지, 하나의 오픈마켓으로

**Next.js + Spring Boot 기반 멀티 셀러 커머스 프로젝트**

구매자·판매자·관리자 역할을 분리하고, 상품 탐색부터 결제 이후 클레임과 정산 관리까지 구현한 개인 풀스택 프로젝트입니다.

[🌐 서비스 체험하기](https://gift-market-test.vercel.app) · [📂 GitHub Repository](https://github.com/bys96/gift-market) · [📋 개발 현황](./docs/DEVELOPMENT_STATUS.md)

> 현재 공개 서비스는 **기능 검증용 테스트 환경**입니다. Toss Payments는 테스트 결제를 사용하며 실제 판매자 계좌 지급은 수행하지 않습니다.

</div>

---

## 프로젝트 소개

Gift Market은 **선물하기 경험을 참고한 오픈마켓형 쇼핑몰**입니다. 상품을 찾고 구매하는 사용자 경험뿐 아니라, 여러 판매자가 상품과 주문을 관리하고 관리자가 입점·운영·정산을 통제하는 흐름까지 하나의 서비스로 구성했습니다.

단순한 상품 CRUD를 넘어 **다중 옵션 구매, 재고 일관성, 결제 요청 검증, 취소·반품·교환, 인증 세션 관리** 등 실제 커머스에서 발생하는 문제를 고려해 구현했습니다.

## 핵심 기능

| 구매자 | 판매자 | 관리자 |
| --- | --- | --- |
| Google·Kakao 소셜 로그인 | 판매자 신청 및 상점 관리 | 판매자 신청 승인·거절 |
| 카테고리·선물 상황별 상품 탐색 | 상품·옵션·재고 관리 | 회원·판매자·상품 관리 |
| 검색·필터·정렬·자동완성 | 상품 상세 콘텐츠 편집 | 주문·클레임 현황 조회 |
| 장바구니·찜·바로구매 | 주문 확인 및 배송 처리 | 정산 생성·보류·확정 |
| Toss Payments 테스트 결제 | 취소·반품·교환 처리 | 검색 인덱스·동의어 관리 |
| 주문·배송 조회 및 구매확정 | 상품 문의 답변·정산 조회 | 관리자 권한 관리 |
| 전체·부분 취소, 반품·교환 | | |

### 주요 구현 포인트

**01. 옵션 유무에 관계없는 일관된 주문 모델**

- 상품 옵션 조합별 Variant와 재고를 관리합니다.
- 옵션이 없는 상품도 내부적으로 기본 Variant를 사용해 주문·장바구니·재고 처리 구조를 통일했습니다.
- 구매자는 단일 옵션과 다중 옵션을 선택해 바로구매할 수 있습니다.

**02. 중복 요청에 대응하는 주문·결제 흐름**

- 동일한 요청 키와 동일한 구매 선택으로 재시도하면 기존 준비 주문을 재사용합니다.
- 같은 요청 키로 다른 구매 선택을 요청하면 `HTTP 409 Conflict`를 반환합니다.
- 결제는 Toss Payments **테스트 환경**과 연동합니다.

**03. 구매 이후까지 이어지는 커머스 처리**

- 주문 및 배송 조회, 구매확정, 전체·부분 취소를 지원합니다.
- 반품·교환 요청과 판매자 처리 흐름을 구현했습니다.
- 관리자는 정산 내역을 수동 생성하고 보류·해제 및 금액 확정을 관리합니다.

**04. 인증과 검색의 운영 안정성 고려**

- Google·Kakao OAuth2와 JWT 기반 인증을 사용합니다.
- Access Token은 단기 사용하고 Refresh Token은 HttpOnly 쿠키로 관리합니다. 서버 로그아웃 실패 시 자동 로그인 복원을 차단하고 재시도를 안내합니다.
- 기본 MySQL 검색에 더해 Meilisearch를 선택적으로 활용하며, 검색 엔진 장애나 인덱스 준비 중에는 MySQL 검색으로 전환합니다.

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| **Frontend** | Next.js 16, React 19, TypeScript, Zustand, TanStack Query, Tiptap, CSS |
| **Backend** | Java 21, Spring Boot 4, Spring Security, Spring Data JPA, Validation |
| **Authentication** | Google·Kakao OAuth2, JWT, HttpOnly Refresh Token Cookie |
| **Database / Search** | MySQL, Meilisearch |
| **Payment / Storage** | Toss Payments(테스트), AWS S3, MinIO(로컬) |
| **Infrastructure** | Vercel, AWS EC2, Nginx, Docker Compose, GitHub Actions |
| **Testing** | JUnit, H2, Testcontainers, Node.js Test Runner |

## 시스템 구성

```text
                      ┌──────────────────────────┐
                      │          사용자          │
                      │   구매자 / 판매자 / 관리자 │
                      └────────────┬─────────────┘
                                   │
                                   ▼
                      ┌──────────────────────────┐
                      │ Next.js 16 · Vercel      │
                      │ 화면 · 상태 관리 · API 호출 │
                      └────────────┬─────────────┘
                                   │ HTTPS / API
                                   ▼
                      ┌──────────────────────────┐
                      │ Nginx · AWS EC2          │
                      │ Spring Boot REST API     │
                      │ 인증 · 상품 · 주문 · 결제    │
                      └──────┬───────┬───────────┘
                             │       │
                  ┌──────────┘       └────────────┐
                  ▼                               ▼
             ┌─────────┐                  ┌──────────────┐
             │  MySQL  │                  │ Meilisearch  │
             │ 주 데이터│                  │ 선택적 검색   │
             └─────────┘                  └──────────────┘

        외부 연동: Google / Kakao OAuth2 · Toss Payments · S3
        API 배포: GitHub Actions · Docker Compose
```

검색은 MySQL을 기본으로 사용하며 Meilisearch는 선택적으로 구성합니다. 프론트엔드는 Vercel, 백엔드와 MySQL은 EC2에 배치하는 구조입니다.

## 프로젝트 디렉터리

```text
gift-market/
├── giftmarket-api/         # Spring Boot REST API
├── giftmarket-web/         # Next.js 웹 애플리케이션
├── docs/                   # 기능 설계, 개발 현황, 운영 문서
├── compose.search.yaml     # Meilisearch 선택 구성
└── .github/workflows/      # API 배포 자동화
```

## 구현 범위와 현재 상태

**구현된 영역** — 회원 인증, 상품·검색, 장바구니·찜, 옵션·재고, 주문·테스트 결제, 배송, 취소·반품·교환, 판매자 기능, 관리자 기능, 수동 정산 관리.

**현재 범위에 포함되지 않는 영역** — 선물 수락·거절 및 수신자 직접 배송지 입력 흐름, 실제 판매자 계좌 지급, 자동 정산 스케줄러, 쿠폰·포인트.

> 기능 구현과 운영 환경에서의 검증 완료는 구분합니다. 세부 테스트 및 운영 확인 항목은 [개발 현황](./docs/DEVELOPMENT_STATUS.md)을 참고해 주세요.

## 설계 및 개발 문서

| 주제 | 문서 |
| --- | --- |
| 개발 진행 및 우선순위 | [개발 현황](./docs/DEVELOPMENT_STATUS.md) · [로드맵](./docs/ROADMAP.md) |
| 주문 및 결제 | [결제 아키텍처](./docs/PAYMENT_ARCHITECTURE_DESIGN.md) · [취소·환불](./docs/ORDER_CANCELLATION_REFUND_DESIGN.md) |
| 반품 및 교환 | [반품·교환 설계](./docs/ORDER_RETURN_EXCHANGE_DESIGN.md) |
| 상품 옵션 및 SKU | [SKU 설계](./docs/SKU_DESIGN.md) · [기본 Variant](./docs/DEFAULT_VARIANT_INTEGRATION.md) |
| 판매자 정산 | [정산 설계](./docs/SETTLEMENT_V1.md) |
| 검색 | [Meilisearch 운영](./docs/MEILISEARCH_OPERATIONS.md) · [검색 검증](./docs/SEARCH_VERIFICATION.md) |
| 기타 운영 설계 | [알림](./docs/NOTIFICATION_DESIGN.md) · [관리자 운영 정책](./docs/admin-operation-policy.md) · [문제 해결](./docs/TROUBLESHOOTING.md) |

---

<div align="center">

**Gift Market** · 개인 풀스택 커머스 프로젝트

[서비스 체험](https://gift-market-test.vercel.app) · [MIT License](./LICENSE)

</div>
