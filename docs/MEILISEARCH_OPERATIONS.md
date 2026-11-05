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
한국어 토큰화/오타 및 공백 처리와 동의어는 별개다. `search.synonyms`로 운영자가 검토한 용어를 관리한다.
샘플 설정의 침구 용어는 상품 ID/이름 특례가 아니다. 운영 상품의 이불 포함 여부나 검색 성공은 실제 설명 확인이 필요하다.

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
   샘플 동의어 설정도 운영 taxonomy를 검토해 복사한다. 설정 파일 변경 후 API 재시작/재색인한다.
6. COMPLETE 확인 후 정상/오타/동의어/필터/정렬/품절/페이지 경계, 장애 시 fallback, 로그·메모리·disk를 점검한다.
   문제 발생 시 `SEARCH_ENGINE=mysql`로 재시작한다. 인덱싱도 중단하려면 `SEARCH_INDEXING_ENABLED=false`를 함께 설정한다. 인덱스 중단/삭제는 상품 원본에 영향이 없다.

## 검증

2026-11-05: 인덱싱 분리 후 `./gradlew test --no-daemon` 전체 회귀 통과.
MySQL + 인덱싱 false/true, Meilisearch + 인덱싱 false의 기존 동작, 장애 시 MySQL fallback,
환경변수 바인딩 및 MySQL 모드의 커밋 후 감지/롤백 미반영을 검증했다.
이번 변경에서 운영 서버 검증과 별도 `meiliIntegrationTest`는 실행하지 않았다.

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
