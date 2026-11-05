# Meilisearch 선택적 상품 검색

## 기본 동작과 정합성

기본값은 `search.engine=mysql`이다. 검색어가 없으면 항상 기존 MySQL 상품 목록을 사용한다.
검색 엔진 선택과 인덱싱 활성화는 분리된다. `SEARCH_INDEXING_ENABLED` (`search.indexing-enabled`)의 기본값은 `false`다.
`SEARCH_ENGINE=meilisearch`이면 이 값이 `false`이거나 미설정이어도 기존 검색 및 인덱싱을 유지한다.
기존 `/api/products`, `/api/products/suggest`와 요청/응답 DTO는 그대로다.

| SEARCH_ENGINE | SEARCH_INDEXING_ENABLED | 검색·자동완성 | 관리자 재인덱싱·주기 동기화·커밋 감지 |
| --- | --- | --- | --- |
| mysql | false 또는 미설정 | 기존 MySQL | 비활성화 (`DISABLED`) |
| mysql | true | 기존 MySQL | 활성화 |
| meilisearch | false, true 또는 미설정 | 기존 Meilisearch 및 MySQL fallback | 활성화 |

운영 검색을 MySQL로 유지하며 인덱스를 준비하려면 API 환경에 `SEARCH_ENGINE=mysql`,
`SEARCH_INDEXING_ENABLED=true`와 기존 Meilisearch 연결 설정을 적용한 뒤 API를 재시작한다.
기존 관리자 `POST /api/admin/product-search/reindex?restart=true` 및 후속 batch API를 사용할 수 있다.
자동 동기화도 함께 활성화되며, `COMPLETE` 이후에도 검색은 MySQL을 사용한다.
인덱싱만 중단하려면 `SEARCH_ENGINE=mysql`, `SEARCH_INDEXING_ENABLED=false`로 재시작한다.
인덱싱 장애는 기존처럼 `RETRY`로 반환하며 MySQL 상품 검색에는 영향을 주지 않는다.
인덱스 준비 중, 갱신 중, 장애, 응답 오류, 결과 상한 초과 시 기존 MySQL 키워드 검색을 사용한다.
SQL fallback은 의미·동의어·오타 검색 결과와 다를 수 있다.

Meilisearch는 상품 ID 후보만 반환한다. 해당 검색의 **전체 ID 집합**을 확보한 뒤 MySQL에서
공개 상태, Seller ACTIVE, 삭제/관리자 숨김, 카테고리, 선물 상황, 가격, 품절 조건을 검사한다.
전체 집합에 정렬을 적용한 뒤 건수와 페이지를 계산한다. 과거 인덱스의 가격·판매 상태를 응답으로 사용하지 않는다.
자동완성은 현재 ON_SALE 및 재고 양수 상품 5개로 제한하고 기존 이미지 URL 변환을 유지한다.

`search.max-matches` 기본 1000, 하드 상한 2000이다. Meilisearch `maxTotalHits`는 이 값+1로 설정한다.
일부 후보만 필터링해 잘못된 건수를 반환하지 않도록 전체 결과가 상한을 넘으면 MySQL로 전환한다.
이 제한은 작은 인스턴스의 메모리/SQL IN 크기를 위한 것으로, 대규모 카탈로그에서는 별도 성능 설계가 필요하다.
인덱스 설정은 이 모듈만 관리하고 외부에서 pagination 설정을 낮추지 않는다.

정렬 미지정 시 상품명 정확 일치, 상품명 포함 일치, 나머지 Meilisearch 관련성 순이다.
명시적 LATEST/PRICE_ASC/PRICE_DESC는 DB 값으로 정렬한다. POPULAR는 기존 판매수량/찜/등록일/ID 정책을 사용한다.
상품명·공백 제거 상품명·카테고리·브랜드·선물 상황·요약·HTML 제거 설명을 인덱싱한다.
본문은 12000자로 제한하며 실제 데이터 밖의 특징을 생성하지 않는다.
한국어 토큰화/오타 및 공백 처리와 동의어는 별개다. 기본 사전은 아래 공통 파일에서 관리한다.
사전의 침구 용어는 상품 ID/이름 특례가 아니다. 운영 상품의 이불 포함 여부나 검색 성공은 실제 설명 확인이 필요하다.

## 한국어 공통 동의어 사전과 설정만 적용하기

기본 파일: `giftmarket-api/src/main/resources/search/korean-synonyms.properties` (UTF-8).
`그룹명=표현1,표현2,...` 한 줄로 같은 그룹의 양방향 동의어를 생성한다.
침구/침구류/이불, 담요/블랭킷, 베개/베게, 베개커버/베갯잇, 수건/타월/타올,
머그/머그컵, 디퓨저/디퓨져, 초콜릿/초콜렛 등 보수적인 기본 사전을 제공한다.
침구↔이불은 이불이 침구 범주에 포함되는 **카탈로그 탐색 확장**이다. 모든 침구가 이불이라는 의미는 아니다.
베개·매트리스·담요를 이 그룹에 넣지 않으며, 머그↔텀블러, 향수↔디퓨저 등 다른 상품 종류도 합치지 않는다.
운영 카테고리의 침구에 베개 등 다른 상품이 많이 섞여 있으면 해당 그룹을 축소해야 한다.
형태소 분석·임베딩·LLM·상품별 확장 데이터·별도 라이브러리는 추가하지 않는다.

`MeiliClient.configure()`가 인덱스 설정 시 자동 적용한다. 샘플 YAML을 import하지 않아도 기본 사전은 포함된다.
기존 `search.synonyms` 지정은 유지되며, 같은 검색어의 기본 값을 덮어쓴다 (`[]`도 허용).
운영에서 이전 침구 3개 설정이 남아 있으면 이 값이 기본 사전보다 우선한다. 신규 사전은 공통 파일만 관리하는 방식을 권장한다.

기존 상품 문서를 유지하며 사전만 수정하려면 API에 다음을 설정한다.

```text
SEARCH_ENGINE=mysql
SEARCH_INDEXING_ENABLED=true
# 선택: 아래 파일을 API 컨테이너에 read-only 디렉터리 bind mount. 기본은 classpath 사전.
SEARCH_SYNONYMS_LOCATION=file:/app/config/search/korean-synonyms.properties
```

외부 사전은 전체 기본 사전을 대체한다. 저장소 기본 파일을 복사한 뒤 수정한다.
Linux 배포 checkout의 저장소 루트에서 최초 복사 예:

```bash
mkdir -p deploy-config/search
cp -n giftmarket-api/src/main/resources/search/korean-synonyms.properties deploy-config/search/
```

운영 API Compose의 기존 서비스에 `./deploy-config/search:/app/config/search:ro` 디렉터리 mount를 추가하고,
위 환경변수를 적용해 기존 배포 절차로 재배포한다. 이후에는 이 디렉터리의 사전만 수정하고 설정 API를 호출한다.
파일 최대 64KiB, 그룹당 2~10개 표현, 표현당 최대 100자다. 누락/잘못된 파일은 빈 사전으로 적용하지 않고 실패한다.
외부 파일은 설정 API 호출 시 다시 읽으므로 다음 사전 수정에는 API 재시작이 필요 없다.
디렉터리를 mount하면 파일을 원자적으로 교체해도 새 파일이 보인다. 파일 경로는 브라우저 입력이 아닌 서버 설정으로만 지정한다.

추가 관리자 API (기존 ADMIN/SUPER_ADMIN 권한·JWT·ApiResponse 유지):

```text
GET  /api/admin/product-search/settings  # 현재 파일과 실제 Meilisearch 동의어 비교, 쓰기 없음
POST /api/admin/product-search/settings  # 다를 때만 synonyms PUT → task 완료 확인 → GET 재검증
```

`data`: `status`, `matches`, `terms` (검색어 키 수), `expectedHash`, `actualHash` (SHA-256).
`APPLIED`: 변경 후 일치, `VERIFIED`: 이미 일치, `MISMATCH`: 불일치,
`RETRY`: 연결/인증/파일/비동기 task 오류 또는 제한 시간 내 미완료, `BUSY`: 인덱싱 작업 중,
`DISABLED`: 인덱싱 비활성화. HTTP 200만으로 성공을 판단하지 말고 `matches=true`와 두 해시 일치를 확인한다.
기존 HTTP timeout과 최대 약 3초 task 대기 제한을 그대로 사용한다. `RETRY`이면 잠시 뒤 GET으로 먼저 확인한다.
이미 적용됐으면 POST도 새 task를 만들지 않는다. 상태 확인 실패를 검색 결과 0건으로 표시하지 않는다.

이 API는 상품 DB 조회·문서 삭제·문서 재등록·커서 초기화를 하지 않는다.
동의어 설정 변경에 따른 Meilisearch 내부 인덱스 처리는 엔진이 수행한다. 비용이 0인 작업은 아니므로 작은 사전을 한 번에 적용한다.
사전만 변경할 때 `/reindex?restart=true`를 호출하지 않는다.
기존 API 프로세스 재시작/장애 복구 시의 전체 스캔·재구축 정책은 유지된다. 최초 코드 배포/환경변수 변경으로
API를 재시작하면 이 기존 동기화는 실행된다. 이후 외부 사전 변경은 재시작 없이 위 설정 API로 적용한다.

### 운영 명령 예시

API 배포는 기존 배포 파이프라인을 사용한다. 저장소 밖 운영 Compose의 서비스 이름을 추측하지 않는다.
Meilisearch는 이미 운영 중이므로 컨테이너를 새로 만들거나 볼륨을 지울 필요가 없다.
기존 URL/key/index 설정은 유지한다. 샘플 YAML 미사용 시 `SEARCH_URL`, `SEARCH_API_KEY`, `SEARCH_INDEX`를 사용하며
샘플 placeholder가 적용되는 환경에서는 기존 `MEILI_URL`, `MEILI_API_KEY`를 사용할 수 있다.
EC2 t4g.small에서는 기존 `MEILI_MAX_INDEXING_MEMORY=128Mb`, `MEILI_MAX_INDEXING_THREADS=1`을 유지하고,
설정이 같을 때 PUT을 생략하는 동작으로 중복 내부 작업을 피한다.

아래 명령은 관리자 로그인한 브라우저의 같은 사이트 개발자 콘솔에서 실행한다.
Access Token은 메모리에만 있으므로 sessionStorage/localStorage를 읽지 않는다.
아래 예제는 기존 Refresh Cookie로 한 번 재발급하고 설정 결과만 출력한다. 토큰 자체는 출력하지 않는다.
다른 탭의 인증 요청과 겹치지 않게 실행하고, 끝난 뒤 페이지를 새로고침해 기존 앱 인증 초기화를 수행한다.

```javascript
// 관리자 화면에서 실행. 토큰은 출력하지 않는다.
await (async () => {
  const session = await fetch("/api/auth/token", { method: "POST", credentials: "include" });
  const body = await session.json();
  if (!session.ok || !body.success || !body.data?.accessToken) throw new Error("관리자 재로그인 필요");
  const headers = { Authorization: `Bearer ${body.data.accessToken}` };
  const results = [];
  for (const method of ["GET", "POST", "GET"]) {
    const response = await fetch("/api/admin/product-search/settings", { method, headers, credentials: "include", redirect: "error" });
    if (!response.ok) throw new Error(`설정 요청 실패: ${response.status}`);
    results.push(await response.json());
  }
  return results;
})();
```

만료 시 관리자 페이지에서 다시 로그인한다.
Meilisearch 직접 검증은 서버 내부에서 기존 안전한 credential 주입 도구를 사용한다.
`GET /indexes/giftmarket_products/settings/synonyms`, `GET /indexes/giftmarket_products/stats`,
`POST /indexes/giftmarket_products/search` (`{"q":"이불","page":1,"hitsPerPage":100}`)로 확인한다.
문서 수가 기존 61개와 같은지(동시 상품 변경이 없는 경우), 침구·이불·담요·블랭킷 및 무관한 단어 결과를 확인한다.
운영 키/Access Token을 명령어·로그·스크린샷에 출력하지 않는다.

### 검색 0건 원인 구분

| 관찰 | 원인과 확인 |
| --- | --- |
| 쇼핑몰은 0건, Meilisearch 직접 검색은 적중 | `SEARCH_ENGINE=mysql`이면 정상. MySQL에 동의어를 추가하지 않았으므로 결과가 달라도 된다. |
| 설정 GET이 MISMATCH | 사전 미적용/구형 설정. POST 적용 후 matches와 해시 재확인. |
| 설정 GET이 RETRY | 엔진 연결/인증/파일/task 문제. 기존 보안 처리된 오류 클래스 로그 및 Meilisearch task 상태 확인. |
| 사전 일치, 침구 적중, 이불 미적중 | 실제 검색 인덱스 이름·검색 필드·기존 search.synonyms override·토큰화·상품 내용을 확인. |
| 사전 일치, 두 표현 모두 미적중 | 해당 상품 내용이 없거나 아직 동기화되지 않았을 수 있음. 문서 수와 실제 색인 필드 확인. |
| Meilisearch 후보는 있으나 쇼핑몰은 0건 | 공개/판매자 ACTIVE/품절/가격/카테고리/선물 상황/페이지 범위 등 기존 DB 조건을 확인. |
| 준비 중·장애·결과 상한 초과 | 기존 MySQL fallback. Meilisearch 검색 0건과 별개이며 로그/인덱싱 상태 확인. |

테스트는 가상 상품으로 사전 적용 전 0건→동일 문서에서 설정만 적용 후 적중, 무관한 검색어 0건,
사전 불일치/연결 오류, 후보 0건/DB 필터 0건, 준비 전 MySQL 경로를 구분한다. 운영 61개 상품의 정답을 가정하지 않는다.

## 동기화와 재색인

상품/카테고리/Seller/선물 상황/상품-선물 상황의 Hibernate **커밋 후** 이벤트는 검색 모듈의 revision만 변경한다.
트랜잭션 내 HTTP 호출이나 기존 등록/수정/삭제 로직 변경은 없다. 롤백 시 인덱스에 전달하지 않는다.
스케줄러는 기본 5초마다 ID 순으로 50개씩 현재 DB를 재검사한다. 평상시 전체 카탈로그는 60초마다 재검사한다.
검색 전용 단일 스레드 scheduler를 명시적으로 사용하며 기존 주문/결제 scheduler를 대체하지 않는다.
연관관계 bulk 변경/외부 SQL/변경 이벤트 유실도 이 순회에서 복구한다. 정상 완료 전에는 MySQL을 사용한다.
한 순회 중 추가 커밋이 있으면 다음 전체 순회를 완료해야 검색 엔진을 다시 사용한다.
캐시는 본문 대신 SHA-256 해시를 최대 약 10000개 보관한다. 재시작하면 새로 DB를 읽는다.
변경 문서는 primary key=id로 교체하고 비공개/삭제 문서는 제거한다. 물리 삭제 이벤트도 별도로 제거한다.

기존 ADMIN/SUPER_ADMIN 보안 matcher를 따르는 API:

```text
POST /api/admin/product-search/reindex?restart=true   # 처음 한 번: 초기화 및 첫 batch
POST /api/admin/product-search/reindex                # 후속 batch / 재시도
```

결과: `nextCursor`, `processed`, `status` (`DISABLED`, `BUSY`, `MORE`, `RETRY`, `COMPLETE`).
서버가 커서를 관리한다. `restart=true`를 매 batch 반복하지 않는다. 단일 프로세스 안에서 중복 실행은 BUSY다.
재시작/명시적 rebuild는 **검색 인덱스 문서만** 초기화하고 MySQL로 서비스하면서 재구축한다.
인덱스 write task 성공을 확인한 뒤에만 커서를 전진시킨다. timeout/실패는 동일 batch를 다음 tick에서 재시도한다.
HTTP connect/read 기본 각 800ms, task polling 최대 약 3초+마지막 HTTP timeout이다. 요청 안에서 무한 재시도하지 않는다.
검색 장애 후에는 rebuild를 예약해 반복적인 검색 요청 타임아웃을 줄인다.
로그는 HTTP 상태·오류 클래스·커서만 기록하고 키/요청본문/상품설명/개인정보를 출력하지 않는다.

인덱싱은 eventual consistency다. 커밋부터 반영까지 지연되며 그동안 MySQL 결과로 서비스된다.
현재 구성은 기존 단일 API 인스턴스를 위한 것이다. 여러 writer/API로 확장할 때에는 분산 작업 소유권과 변경 전파를 추가해야 한다.
운영 DB DDL, product_embeddings, 상품 원본, 주문/결제 데이터는 변경하지 않는다.

## 운영 활성화 전 준비

이번 작업은 운영에 접속하거나 배포하지 않았다. 현재 GitHub Actions는 저장소 밖
`/home/ubuntu/gift-market-deploy/scripts/deploy-api.sh`를 호출한다. 실제 운영 Compose/스크립트는 확인하지 못했다.
기존 workflow/배포 설정은 변경하지 않았으며 Meilisearch 미설치 상태에서도 기본 MySQL 모드로 동작한다.

1. 먼저 메모리를 확보한다. 현재 약 595MiB available + swap 사용 상태에서 추가 실행하지 않는다.
   `384m` 상한은 메모리 예약/성능 보장이 아니며 OS 파일 캐시/인덱스 크기까지 고려해야 한다.
   더 큰 인스턴스 또는 별도 검색 호스트를 검토한 뒤 승인받는다.
2. `MEILI_MASTER_KEY`를 외부 secret 저장소/운영 환경으로 제공한다. 코드에 실제 키를 넣지 않는다.
   API의 `MEILI_API_KEY`에는 해당 인덱스 search/documents/settings/tasks 작업 권한을 가진 서버 전용 키를 설정한다.
3. `compose.search.yaml`은 기존 stack을 건드리지 않는 별도 opt-in stack이다.
   승인 후에만 `docker compose -p giftmarket-search -f compose.search.yaml --profile search up -d`를 실행한다.
   공식 v1.16.0 다중 아키텍처 이미지, 영속 볼륨, 384MiB 메모리/추가 swap 금지, CPU 0.5,
   indexing memory 128Mb/1 thread, 5MiB x 2 로그, healthcheck를 사용한다. 외부 published port는 없다.
4. API 컨테이너를 운영자가 `giftmarket-search` 내부 네트워크에도 연결한다.
   기존 MySQL/API 네트워크를 제거하지 않는다. API의 `MEILI_URL=http://meilisearch:7700` 연결을 확인한다.
   이 연결은 운영 Compose에 별도로 반영해야 컨테이너 재생성 후에도 유지된다. 기존 서비스 이름을 추측해 수정하지 않았다.
5. 먼저 `SEARCH_ENGINE=mysql`, `SEARCH_INDEXING_ENABLED=true`와 URL/key를 설정해 검색 전환 없이 인덱싱할 수 있다.
   Meilisearch 검색까지 활성화하려면 `SEARCH_ENGINE=meilisearch`로 설정한다. 이 경우 인덱싱 플래그와 무관하게 기존 동작을 유지한다.
   샘플 import 없이 환경변수만 설정할 때에는 Spring 표준 `SEARCH_URL`, `SEARCH_API_KEY`, `SEARCH_INDEX`를 사용한다.
   `MEILI_URL`/`MEILI_API_KEY` 별칭은 샘플 YAML의 placeholder가 적용되어 있을 때 사용한다.
   설정 샘플은 `application-example.yaml`; 운영 설정에 적용하려면 명시적으로 import하거나 같은 properties를 제공한다.
   공통 동의어 파일을 운영 taxonomy와 검토한다. 사전만 변경할 때는 위 설정 전용 API를 사용한다.
6. COMPLETE 확인 후 정상/오타/동의어/필터/정렬/품절/페이지 경계, 장애 시 fallback, 로그·메모리·disk를 점검한다.
   문제 발생 시 `SEARCH_ENGINE=mysql`로 재시작한다. 인덱싱도 중단하려면 `SEARCH_INDEXING_ENABLED=false`를 함께 설정한다. 인덱스 중단/삭제는 상품 원본에 영향이 없다.

## 검증

2026-11-05 한국어 사전 개선: `./gradlew test meiliIntegrationTest --no-daemon` 성공.
공통 사전/외부 파일 재로딩/설정만 적용·읽기 검증/권한/기존 검색 회귀와 실제 v1.16.0 컨테이너 검증을 통과했다.
기존 컨테이너 테스트의 초기 settings 작업은 차가운 Docker 환경에서 3초를 넘길 수 있어 테스트에서만
동일 설정 요청을 최대 3회 재시도한다. 운영 HTTP/task 대기 제한은 변경하지 않았다.
운영 EC2/실제 상품 61개에는 접속하거나 배포하지 않았다.

2026-11-05: 인덱싱 분리 후 `./gradlew test --no-daemon` 전체 회귀 통과.
MySQL + 인덱싱 false/true, Meilisearch + 인덱싱 false의 기존 동작, 장애 시 MySQL fallback,
환경변수 바인딩 및 MySQL 모드의 커밋 후 감지/롤백 미반영을 검증했다.
당시 인덱싱 분리 작업에서는 운영 서버 검증과 별도 `meiliIntegrationTest`를 실행하지 않았다.

```text
cd giftmarket-api
./gradlew test
./gradlew meiliIntegrationTest
```

두 작업을 같은 test task 결과 디렉터리에 동시에 실행하지 않는다.
일반 test는 Docker 없이 실행하며 `meili-container`와 기존 `mysql-container` 태그를 제외한다.
실제 Meilisearch 테스트는 별도 opt-in task에서 로컬 일회성 컨테이너를 생성/제거한다.
테스트의 키는 로컬 테스트 전용 고정값이며 운영 credential이 아니다.
6개 예시 검색어는 **가상의 침구 구성 설명**으로 검증하므로 운영 상품 구성/검색 품질 증거가 아니다.
운영에서는 상품의 실제 설명/카테고리를 확인하고 해당 6개 검색어를 재검증해야 한다.

2026-11-04 로컬 검증: `./gradlew test meiliIntegrationTest` 성공.
일반 테스트 1052개, 실제 Meilisearch 컨테이너 테스트 1개, 실패/오류/skip 0.
Compose 문법 검사 및 공식 이미지 내 curl healthcheck 실행 가능 여부도 확인했다.
운영 MySQL/EC2와 ARM64 실기 환경에서는 실행하지 않았다.

참고: [검색 API](https://www.meilisearch.com/docs/reference/api/search/search-with-post),
[인덱스 설정](https://www.meilisearch.com/docs/reference/api/settings/list-all-settings),
[공식 Docker/ARM64 지원](https://www.meilisearch.com/integrations/docker),
[v1.16.0 릴리스](https://github.com/meilisearch/meilisearch/releases/tag/v1.16.0).
