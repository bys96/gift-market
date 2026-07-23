"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { apiFetch } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";
import type { ApiResponse } from "@/types/api";
import type { User } from "@/types/user";

interface TokenResponse {
  accessToken: string;
}

export default function OAuthCallbackPage() {
  const router = useRouter();

  const setAccessToken = useAuthStore((state) => state.setAccessToken);
  const setUser = useAuthStore((state) => state.setUser);

  useEffect(() => {
    const completeLogin = async () => {
      try {
        // 1. Refresh Token 쿠키로 Access Token 발급
        const tokenResponse = await apiFetch<ApiResponse<TokenResponse>>(
          "/api/auth/token",
          {
            method: "POST",
          },
        );

        const accessToken = tokenResponse.data.accessToken;

        setAccessToken(accessToken);

        // 2. Access Token으로 로그인 사용자 정보 조회
        const userResponse = await apiFetch<ApiResponse<User>>("/api/auth/me", {
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        });

        // 3. 사용자 정보를 Zustand에 저장
        setUser(userResponse.data);

        // 4. 메인 페이지로 이동
        router.replace("/");
      } catch (error) {
        console.error("로그인 처리 실패:", error);

        router.replace("/login");
      }
    };

    completeLogin();
  }, [router, setAccessToken, setUser]);

  return (
    <main>
      <p>로그인 처리 중입니다...</p>
    </main>
  );
}
