const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL;

if (!API_BASE_URL) {
  throw new Error("NEXT_PUBLIC_API_BASE_URL이 설정되지 않았습니다.");
}

export async function apiFetch<T>(
  path: string,
  options?: RequestInit,
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    credentials: "include", // Refresh Token 쿠키 전송
  });

  if (!response.ok) {
    console.error(
      "API Error:",
      response.status,
      response.statusText,
      await response.text(),
    );

    throw new Error("API 요청에 실패했습니다.");
  }

  return response.json();
}

export { API_BASE_URL };
