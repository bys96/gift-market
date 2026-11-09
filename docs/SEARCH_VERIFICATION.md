# 검색 기능 검증 기준

> 2026-11-09 소스 대조. 이번 작업에서는 테스트·빌드·운영 접속을 실행하지 않았다. 구현 상태와 과거 실행 기록은 [개발 현황](./DEVELOPMENT_STATUS.md), 설정·관리 API는 [Meilisearch 운영 문서](./MEILISEARCH_OPERATIONS.md)를 따른다.

## 현재 요청 경로

- 일반 검색: `app/products/page.tsx` → `lib/product-api.ts` → `GET /api/products?keyword=…` → `ProductSearchService`.
- 자동완성: `SearchBox` → `scheduleSuggestionRequest`(275ms) → `GET /api/products/suggest?q=…` → `ProductSuggestionService` → 동일 검색 서비스. 응답의 추천 검색어만 UI에 표시한다.
- 기본값은 MySQL이며 Meilisearch 모드에서도 준비 전·실제 변경·장애·후보 상한 초과 때 MySQL fallback을 유지한다. 두 엔진의 검색 필드·동의어 범위가 다르므로 장애 fallback의 결과가 정상 엔진과 동일하다고 보장하지 않는다.
- 변경 없는 정기 인덱스 점검은 ready를 유지한다. 이미 제외한 초안·숨김 상품을 매번 다시 삭제하지 않는다. 필터·정렬·페이지 계산은 현재 DB 조건을 따른다.
- Header의 PC·모바일 입력 상태는 공유한다. `HeaderSearchSync`가 pathname·query를 관찰하며 `/products`는 URL의 `keyword`, 나머지 경로는 빈 값으로 동기화한다. 별도 Suspense 경계로 헤더 사전 렌더링을 유지한다.
- URL 변경 때 검색창 key가 바뀌어 이전 목록·선택 상태와 debounce/진행 요청을 초기화한다. 타이핑만 할 때는 key가 유지된다. 자동완성은 AbortController와 query 검사, 일반 목록은 요청 ID 검사로 이전 응답을 무시한다.

## 회귀 시나리오

| 시나리오 | 자동화 근거 | 배포 후 확인 필요 |
| --- | --- | --- |
| `침구`, `침구류` 단독·반복·순차 검색 | `ProductSearchServiceTest`, `MeiliIndexServiceTest`, 실제 엔진 `MeiliContainerTest` | 상품 내용·사전 해시·배포 버전·정기 스캔 주기를 포함한 반복 API 비교 |
| 자동완성/일반 검색 후보 경로 | 검색 서비스·자동완성 controller/repository/service 테스트 | 자동완성은 5건·판매중·재고 양수 조건이므로 전체 목록과 건수를 무조건 같게 비교하지 않음 |
| 빠른 타이핑·동일 검색어 재입력·늦은 성공/오류 | `tests/suggestion-request.test.mjs` | 실제 지연·IME·키보드·터치 동작 |
| 검색 후 홈·마이페이지·상품 상세 이동 | `tests/header-search.test.mjs`에서 두 입력값·key 확인 | 입력값과 추천 목록 초기화, 이전 요청 중단 |
| 검색 URL 직접 진입·다른 검색어·필터·정렬·페이지·뒤로/앞으로 | 헤더 테스트의 URL 이동 모의 실행, `tests/product-sort.test.mjs` | 실제 브라우저 history와 모바일 검색창을 다시 열었을 때 URL 검색어 표시 |
| 실제 엔진·설정 오류·fallback | Meilisearch 컨테이너, 설정/서비스 테스트 | 네트워크·인증·리소스 장애 시 fallback 및 정상 0건/장애 구분 |

## 실행과 증거 범위

```bash
cd giftmarket-api
./gradlew test meiliIntegrationTest
cd ../giftmarket-web
node --test tests/header-search.test.mjs tests/suggestion-request.test.mjs tests/product-sort.test.mjs
npm run lint
npx tsc --noEmit
npm run build
```

동일한 Gradle `test`를 동시에 실행하지 않는다. 테스트는 가상 상품을 사용하며 운영 상품 구성·정답의 근거가 아니다. 과거 공개 API에서 관찰한 `침구` 3건→0건→3건 현상은 [트러블슈팅](./TROUBLESHOOTING.md)에 보존하며, 현재 배포본에 수정이 적용됐다는 뜻은 아니다. SQL·스키마 변경은 검색 수정의 요구사항이 아니다.
