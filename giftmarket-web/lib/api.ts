import { useAuthStore } from "@/stores/auth-store";
import type { ApiResponse } from "@/types/api";

interface TokenResponse {
  accessToken: string;
}

interface ApiErrorResponse {
  success?: boolean;
  message?: string;
}

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim().replace(/\/+$/, "") ?? "";

// 여러 API 요청이 동시에 401을 받아도 토큰 재발급은 한 번만 실행한다.
let refreshPromise: Promise<string | null> | null = null;
let refreshFailure: Error | null = null;
let refreshFailedAt = 0;
let authSessionVersion = 0;

export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
  }
}

export class ApiNetworkError extends TypeError {
  constructor() {
    super("네트워크 연결을 확인한 후 다시 시도해주세요.");
  }
}

export class ApiResponseFormatError extends ApiError {
  constructor(message: string, status: number) {
    super(message, status);
  }
}

// 로그아웃 뒤 이미 진행 중이던 token refresh가 인증 상태를 되살리지 못하게 한다.
export function invalidateAuthSession(): void {
  authSessionVersion += 1;
  refreshPromise = null;
  refreshFailure = null;
  refreshFailedAt = 0;
}

type ApiRequestOptions = RequestInit & { skipAuthRefresh?: boolean };

function createRequestHeaders(
  headersInit: HeadersInit | undefined,
  accessToken: string | null,
): Headers {
  const headers = new Headers(headersInit);

  if (accessToken && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  return headers;
}

async function request(
  path: string,
  options: RequestInit,
  accessToken: string | null,
): Promise<Response> {
  try {
    return await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers: createRequestHeaders(options.headers, accessToken),
      credentials: "include",
    });
  } catch (error) {
    if (error instanceof TypeError) throw new ApiNetworkError();
    throw error;
  }
}

export function isAccessTokenExpired(accessToken: string): boolean {
  try {
    const payload = accessToken.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const { exp } = JSON.parse(atob(payload));
    return typeof exp !== "number" || exp * 1000 <= Date.now() + 30_000;
  } catch {
    return true;
  }
}

// Called only by the throttled page-resume observer, never as a request retry.
export async function recoverAuthSession(): Promise<void> {
  const auth = useAuthStore.getState();
  if (!auth.initialized || !auth.isAuthenticated || !auth.accessToken
    || !isAccessTokenExpired(auth.accessToken)) return;

  const sessionVersion = authSessionVersion;
  if (!refreshPromise) refreshFailure = null;
  const token = await refreshAccessToken();
  if (!token && sessionVersion === authSessionVersion) {
    useAuthStore.getState().clearAuth();
  }
}

async function parseResponse<T>(response: Response): Promise<T> {
  let responseBody: string;
  try {
    responseBody = await response.text();
  } catch (error) {
    if (error instanceof TypeError) throw new ApiNetworkError();
    throw error;
  }

  let parsedBody: unknown = null;

  if (responseBody) {
    try {
      parsedBody = JSON.parse(responseBody);
    } catch {
      parsedBody = null;
    }
  }

  if (!response.ok) {
    const errorResponse = parsedBody as ApiErrorResponse | null;

    throw new ApiError(
      errorResponse?.message ?? `API 요청에 실패했습니다. (${response.status})`,
      response.status,
    );
  }

  if (
    parsedBody === null ||
    typeof parsedBody !== "object" ||
    Array.isArray(parsedBody)
  ) {
    throw new ApiResponseFormatError(
      "서버 응답을 확인할 수 없습니다. 잠시 후 다시 시도해주세요.",
      response.status,
    );
  }

  return parsedBody as T;
}

export async function refreshAccessToken(): Promise<string | null> {
  // Request failures are retained until a throttled page-resume recovery.
  if (refreshFailure) throw refreshFailure;
  if (refreshPromise) {
    return refreshPromise;
  }

  const sessionVersion = authSessionVersion;
  refreshPromise = (async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/token`, {
        method: "POST",
        credentials: "include",
        signal: AbortSignal.timeout(15_000),
      });

      if (response.status === 401) return null;

      const result = await parseResponse<ApiResponse<TokenResponse>>(response);

      if (result?.success === true && result.data === null) return null;
      if (result?.success !== true || typeof result.data?.accessToken !== "string"
        || !result.data.accessToken) throw new Error("인증 응답을 확인하지 못했습니다.");

      if (sessionVersion !== authSessionVersion) return null;

      const accessToken = result.data.accessToken;

      useAuthStore.getState().setAccessToken(accessToken);

      return accessToken;
    } catch (error) {
      if (sessionVersion !== authSessionVersion) return null;
      refreshFailure = error instanceof TypeError
        || (error instanceof Error && error.name === "TimeoutError")
        ? new ApiNetworkError()
        : error instanceof Error ? error : new Error("인증 서버에 연결하지 못했습니다.");
      refreshFailedAt = Date.now();
      throw refreshFailure;
    } finally {
      if (sessionVersion === authSessionVersion) refreshPromise = null;
    }
  })();

  return refreshPromise;
}

export async function apiFetch<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const { skipAuthRefresh = false, ...requestOptions } = options;
  let accessToken = useAuthStore.getState().accessToken;
  const canRefresh = !skipAuthRefresh
    && path !== "/api/auth/token" && path !== "/api/auth/logout";
  const sessionVersion = authSessionVersion;
  if (canRefresh && accessToken && isAccessTokenExpired(accessToken)) {
    // Some browsers keep a sleeping tab visible and emit no resume event.
    // A later user request can recover authentication after the cooldown.
    if (refreshFailure && !refreshPromise && Date.now() - refreshFailedAt >= 30_000) {
      refreshFailure = null;
    }
    accessToken = await refreshAccessToken();
    if (!accessToken) {
      if (sessionVersion === authSessionVersion) useAuthStore.getState().clearAuth();
      throw new ApiError("로그인이 만료되었습니다. 다시 로그인해주세요.", 401);
    }
  }

  let response = await request(path, requestOptions, accessToken);

  const shouldRefreshToken =
    response.status === 401 &&
    canRefresh;

  if (shouldRefreshToken) {
    if (sessionVersion !== authSessionVersion) {
      throw new ApiError("로그인 상태가 변경되었습니다. 다시 확인해주세요.", 401);
    }
    const currentAccessToken = useAuthStore.getState().accessToken;
    const refreshedAccessToken = currentAccessToken && currentAccessToken !== accessToken
      ? currentAccessToken : await refreshAccessToken();

    if (!refreshedAccessToken) {
      if (sessionVersion === authSessionVersion) useAuthStore.getState().clearAuth();

      throw new ApiError("로그인이 만료되었습니다. 다시 로그인해주세요.", 401);
    }

    const method = (requestOptions.method ?? "GET").toUpperCase();
    if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
      throw new ApiError("로그인 상태를 갱신했습니다. 요청 결과를 확인한 후 다시 시도해주세요.", 401);
    }
    response = await request(path, requestOptions, refreshedAccessToken);
    if (response.status === 401 && sessionVersion === authSessionVersion) {
      useAuthStore.getState().clearAuth();
    }
  }

  return parseResponse<T>(response);
}

export { API_BASE_URL };
