# Gift Market Frontend

Next.js 16 App Router와 React 19 기반 Frontend다. 프로젝트 UI는 기존 일반 CSS convention을 사용하며 Tailwind utility class를 새 UI에 사용하지 않는다.

## 로컬 실행

```bash
npm install
npm run dev
```

기본 주소는 `http://localhost:3000`이다. Backend 기본 주소는 `http://localhost:8080`이며 실제 연결값은 환경변수로 설정한다.

필요한 공개 환경변수 이름:

- `NEXT_PUBLIC_API_BASE_URL`
- `NEXT_PUBLIC_STORAGE_BASE_URL`
- `NEXT_PUBLIC_TOSS_CLIENT_KEY`

실제 key나 credential은 README와 저장소에 기록하지 않는다.

## 검증

```bash
npm run lint
npx tsc --noEmit
npm run build
```

현재 기준 결과는 `docs/DEVELOPMENT_STATUS.md`에 기록한다.
