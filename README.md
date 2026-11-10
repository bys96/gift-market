# Gift Market

선물할 만한 상품을 찾고 구매할 수 있는 오픈마켓형 쇼핑몰입니다.
여러 판매자의 상품을 한 번에 주문하고, 판매자는 상품 등록부터 배송·반품·정산 내역까지 관리할 수 있습니다.
개인 프로젝트로 개발하고 있으며, 현재 서비스는 기능을 확인하기 위한 테스트 환경입니다.

## 서비스 링크

- [Gift Market 이용하기](https://gift-market-test.vercel.app)
- [GitHub 저장소](https://github.com/bys96/gift-market)

## 주요 기능

### 사용자

- Google·Kakao OAuth2 로그인, 프로필·배송지 관리
- 상품 목록·상세, 카테고리·선물 상황별 탐색, 검색·필터·정렬
- 한국어 동의어 검색과 추천 검색어 자동완성
- 장바구니·찜, 바로구매, Toss Payments 결제
- 주문·배송 조회, 구매확정, 전체·부분 취소, 반품·교환
- 상품 문의·리뷰 작성과 알림 확인

### 판매자

- 판매자 신청과 상점 정보 관리
- 상품·옵션·재고 관리, 이미지·동영상이 포함된 상세 설명 편집
- 주문 확인, 배송 처리, 취소·반품·교환 요청 처리
- 상품 문의 답변과 정산 내역 조회

### 관리자

- 판매자 신청 승인·거절, 회원·판매자·상품 관리
- 주문·취소·반품·교환 내역 조회
- 정산 수동 생성, 보류·해제, 금액 확정
- 검색 인덱스·동의어 설정 관리
- SUPER_ADMIN 전용 관리자 권한 부여·회수

정산은 금액 확정까지 지원하며 실제 판매자 계좌로 지급하는 기능은 아직 없습니다.
선물 상황 분류와 일반 주문의 수령인 입력은 지원하지만, 선물 전용 수락·거절·수신자 배송지 입력은 미구현입니다.

## 기술 스택

- Frontend: Next.js 16, React 19, TypeScript, Zustand, 일반 CSS, Tiptap
- Backend: Java 21, Spring Boot 4, Spring Security, Spring Data JPA, JWT
- 데이터·검색: MySQL, Meilisearch, H2·Testcontainers(테스트)
- 외부 연동: Google·Kakao OAuth2, Toss Payments, AWS S3 / 로컬 MinIO
- 배포: Vercel, AWS EC2, Nginx, Docker Compose, GitHub Actions

검색은 MySQL을 기본으로 사용하며 Meilisearch를 선택해 사용할 수 있습니다.
Meilisearch를 사용하는 경우에도 장애나 인덱스 준비 중에는 MySQL 검색으로 전환합니다.

Frontend는 Vercel, Backend와 MySQL은 EC2에서 운영하는 구조입니다.
GitHub Actions는 EC2의 API 배포를 담당하며, Frontend 배포는 Vercel에서 별도로 진행합니다.

## 프로젝트 구조

```text
gift-market/
├─ giftmarket-api/       # Spring Boot API
├─ giftmarket-web/       # Next.js 웹
├─ docs/                 # 설계·개발 현황·운영 기록
├─ compose.search.yaml  # 선택적으로 사용하는 Meilisearch 구성
└─ .github/workflows/    # API 배포 워크플로
```

## 로컬 실행

Java 21, Node.js와 npm, MySQL이 필요합니다. 파일 저장소는 MinIO 또는 S3를 설정합니다.
DB·OAuth·JWT·스토리지·Toss 설정은 아래 샘플을 참고해 준비하고, 실제 키와 비밀번호는 커밋하지 않습니다.

### Backend

```bash
git clone https://github.com/bys96/gift-market.git
cd gift-market/giftmarket-api
cp src/main/resources/application-example.yaml src/main/resources/application.yaml
```

복사한 설정의 환경변수를 준비한 뒤 실행합니다.
필요한 항목은 [Backend 설정 샘플](./giftmarket-api/src/main/resources/application-example.yaml)에서 확인할 수 있습니다.

```bash
./gradlew bootRun
```

새 DB에는 활성 카테고리가 필요합니다. [카테고리 seed](./docs/sql/category-seed.sql)를 확인해 적용합니다.
로컬 샘플의 스키마 자동 갱신 설정과 운영의 `ddl-auto=validate`는 구분해서 사용합니다.

### Frontend

새 터미널을 열고 저장소 루트에서 실행합니다.

```bash
cd giftmarket-web
cp .env.sample .env.local
npm ci
npm run dev
```

[Frontend 설정 샘플](./giftmarket-web/.env.sample)을 참고해 API·스토리지 주소와 Toss client key를 설정합니다.
로컬 API 주소는 `NEXT_PUBLIC_API_BASE_URL`로 지정합니다.
샘플의 과거 Render 주소는 사용하지 않으며, 운영은 Vercel의 same-origin rewrite를 유지합니다.

### 테스트와 빌드

저장소 루트에서 실행합니다.

```bash
cd giftmarket-api
./gradlew test
cd ../giftmarket-web
node --test tests/*.test.mjs
npm run lint
npx tsc --noEmit
npm run build
```

실제 검색 엔진·MySQL 컨테이너 테스트는 Backend의 `./gradlew meiliIntegrationTest`, `./gradlew mysqlIntegrationTest`로 별도 실행합니다.
Windows PowerShell에서 npm 실행 정책에 막히면 `npm.cmd`, `npx.cmd`를 사용합니다.

## 현재 개발 상태와 계획

상품 탐색부터 주문·결제·배송, 취소·반품·교환까지 기본 흐름과 판매자·관리자 화면이 구현되어 있습니다.
구현 여부와 운영 환경의 검증 완료는 구분하고 있으며, 테스트 결과와 남은 확인 항목은 [개발 현황](./docs/DEVELOPMENT_STATUS.md)에 기록합니다.

당장은 주문·결제 E2E 검증과 선물하기의 미구현 수신자 흐름을 우선 진행할 계획입니다.
이후 판매자·관리자 운영 점검, 인증·보안, 배포·모니터링·백업, 모바일 사용성을 보완합니다.
자동 정산·실제 지급, 관리자 강제 환불·클레임 중재, 쿠폰·포인트는 후속 과제입니다.
세부 우선순위는 [로드맵](./docs/ROADMAP.md)을 참고하세요.

## 관련 문서

- [개발 현황](./docs/DEVELOPMENT_STATUS.md) · [로드맵](./docs/ROADMAP.md)
- [검색 운영](./docs/MEILISEARCH_OPERATIONS.md) · [검색 검증](./docs/SEARCH_VERIFICATION.md)
- [결제 설계](./docs/PAYMENT_ARCHITECTURE_DESIGN.md) · [취소·환불](./docs/ORDER_CANCELLATION_REFUND_DESIGN.md) · [반품·교환](./docs/ORDER_RETURN_EXCHANGE_DESIGN.md)
- [정산](./docs/SETTLEMENT_V1.md) · [관리자 운영 정책](./docs/admin-operation-policy.md)
- [알림](./docs/NOTIFICATION_DESIGN.md) · [상품 상세 미디어](./docs/PRODUCT_DESCRIPTION_MEDIA.md)
- [문제 해결·운영 기록](./docs/TROUBLESHOOTING.md)

라이선스는 [MIT](./LICENSE)를 따릅니다.
