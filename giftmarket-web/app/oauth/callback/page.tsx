"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { apiFetch } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";
import { useWishlistStore } from "@/stores/wishlist-store";
import type { ApiResponse } from "@/types/api";
import type { User } from "@/types/user";

interface TokenResponse {
  accessToken: string;
}

const DEFAULT_REDIRECT_URL = "/";
const LOGIN_REDIRECT_STORAGE_KEY = "login_redirect_url";

function resolveRedirectUrl(redirect: string | null): string {
  if (!redirect) {
    return DEFAULT_REDIRECT_URL;
  }

  // 외부 URL redirect 방지
  if (!redirect.startsWith("/") || redirect.startsWith("//")) {
    return DEFAULT_REDIRECT_URL;
  }

  return redirect;
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

        if (!tokenResponse.success || !tokenResponse.data?.accessToken) {
          throw new Error("Access Token 발급에 실패했습니다.");
        }

        const accessToken = tokenResponse.data.accessToken;

        setAccessToken(accessToken);

        // 2. Access Token으로 로그인 사용자 정보 조회
        const userResponse = await apiFetch<ApiResponse<User>>("/api/auth/me");

        if (!userResponse.success || !userResponse.data) {
          throw new Error("로그인 사용자 정보를 불러오지 못했습니다.");
        }

        // 3. 사용자 정보를 Zustand에 저장
        setUser(userResponse.data);
        useWishlistStore.getState().resetWishlist();
        try {
          await useWishlistStore.getState().loadWishlist(true);
        } catch (error) {
          console.error("찜 목록 초기화 실패:", error);
        }

        // 4. 로그인 전에 있던 페이지 확인
        const redirectUrl = resolveRedirectUrl(
          sessionStorage.getItem(LOGIN_REDIRECT_STORAGE_KEY),
        );

        // 5. 사용한 redirect 제거
        sessionStorage.removeItem(LOGIN_REDIRECT_STORAGE_KEY);

        // 6. 원래 페이지로 복귀
        router.replace(redirectUrl);
      } catch (error) {
        console.error("로그인 처리 실패:", error);

        useWishlistStore.getState().resetWishlist();

        sessionStorage.removeItem(LOGIN_REDIRECT_STORAGE_KEY);

        router.replace("/login");
      }
    };

    void completeLogin();
  }, [router, setAccessToken, setUser]);

  return (
    <main>
      <p>로그인 처리 중입니다...</p>
    </main>
  );
}
