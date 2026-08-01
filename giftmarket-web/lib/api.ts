import { useAuthStore } from "@/stores/auth-store";
import type { ApiResponse } from "@/types/api";

interface TokenResponse {
  accessToken: string;
}

interface ApiErrorResponse {
  success?: boolean;
  message?: string;
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL;

if (!API_BASE_URL) {
  throw new Error("NEXT_PUBLIC_API_BASE_URL이 설정되지 않았습니다.");
}

// 여러 API 요청이 동시에 401을 받아도 토큰 재발급은 한 번만 실행한다.
let refreshPromise: Promise<string | null> | null = null;

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

    throw new Error(
      errorResponse?.message ?? `API 요청에 실패했습니다. (${response.status})`,
    );
  }

  return parsedBody as T;
}

async function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/token`, {
        method: "POST",
        credentials: "include",
      });

      if (!response.ok) {
        return null;
      }

      const result = await parseResponse<ApiResponse<TokenResponse>>(response);

      if (!result.success || !result.data?.accessToken) {
        return null;
      }

      const accessToken = result.data.accessToken;

      useAuthStore.getState().setAccessToken(accessToken);

      return accessToken;
    } catch (error) {
      console.error("Access Token 재발급 실패:", error);

      return null;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const accessToken = useAuthStore.getState().accessToken;

  let response = await request(path, options, accessToken);

  const shouldRefreshToken =
    response.status === 401 &&
    path !== "/api/auth/token" &&
    path !== "/api/auth/logout";

  if (shouldRefreshToken) {
    const refreshedAccessToken = await refreshAccessToken();

    if (!refreshedAccessToken) {
      useAuthStore.getState().clearAuth();

      throw new Error("로그인이 만료되었습니다.");
    }

    response = await request(path, options, refreshedAccessToken);
  }

  return parseResponse<T>(response);
}

export { API_BASE_URL };
