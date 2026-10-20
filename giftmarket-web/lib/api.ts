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
let authSessionVersion = 0;

export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
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
  return fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: createRequestHeaders(options.headers, accessToken),
    credentials: "include",
  });
}

async function parseResponse<T>(response: Response): Promise<T> {
  const responseBody = await response.text();

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
  // rotation 이후 응답만 유실됐을 수 있어 같은 문서에서 자동 재시도하지 않는다.
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
      refreshFailure = error instanceof Error ? error : new Error("인증 서버에 연결하지 못했습니다.");
      throw refreshFailure;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

export async function apiFetch<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const { skipAuthRefresh = false, ...requestOptions } = options;
  const accessToken = useAuthStore.getState().accessToken;

  let response = await request(path, requestOptions, accessToken);

  const shouldRefreshToken =
    response.status === 401 &&
    !skipAuthRefresh &&
    path !== "/api/auth/token" &&
    path !== "/api/auth/logout";

  if (shouldRefreshToken) {
    const currentAccessToken = useAuthStore.getState().accessToken;
    const refreshedAccessToken = currentAccessToken && currentAccessToken !== accessToken
      ? currentAccessToken : await refreshAccessToken();

    if (!refreshedAccessToken) {
      useAuthStore.getState().clearAuth();

      throw new Error("로그인이 만료되었습니다.");
    }

    response = await request(path, requestOptions, refreshedAccessToken);
  }

  return parseResponse<T>(response);
}

export { API_BASE_URL };
