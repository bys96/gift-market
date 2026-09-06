// 인증 초기화 이후 client effect에서 호출해 현재 경로와 query/hash를 보존한다.
export function getLoginRedirectUrl(): string {
  const { pathname, search, hash } = window.location;
  return `/login?redirect=${encodeURIComponent(`${pathname}${search}${hash}`)}`;
}
