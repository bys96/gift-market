# Gift Market

실제 오픈마켓 운영 흐름을 기준으로 구현한 Spring Boot + Next.js 쇼핑몰 프로젝트다.

```text
giftmarket-api/   Spring Boot Backend (기본 포트 8080)
giftmarket-web/   Next.js Frontend (기본 포트 3000)
docs/             현재 개발 상태, 결제·취소·반품·교환 설계와 수동 SQL 참고본
```

로컬 실행에는 MySQL, MinIO와 OAuth/JWT/Toss 관련 환경변수가 필요하다. 실제 Secret은 저장소나 문서에 기록하지 않는다. Backend 설정 예시는 `giftmarket-api/src/main/resources/application-example.yaml`, 현재 구현·검증 기준점은 `docs/DEVELOPMENT_STATUS.md`를 참고한다.

```bash
cd giftmarket-api
./gradlew bootRun
```

```bash
cd giftmarket-web
npm install
npm run dev
```

현재 개발환경은 Hibernate `ddl-auto:update`를 사용한다. `docs/sql/*.sql`은 자동 실행 migration이 아니므로 이미 반영된 DDL과 중복 실행하지 않는다. 운영 배포 전 versioned migration과 환경별 설정을 별도로 확정해야 한다.
