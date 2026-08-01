"use client";

import { useEffect, useRef } from "react";

import { API_BASE_URL, apiFetch } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";
import type { ApiResponse } from "@/types/api";
import type { User } from "@/types/user";

interface TokenResponse {
  accessToken: string;
}

export default function AuthInitializer() {
  const initializedRef = useRef(false);

  const setInitialized = useAuthStore((state) => state.setInitialized);
  const setAccessToken = useAuthStore((state) => state.setAccessToken);
  const setUser = useAuthStore((state) => state.setUser);
  const clearAuth = useAuthStore((state) => state.clearAuth);

  useEffect(() => {
    if (initializedRef.current) {
      return;
    }

    initializedRef.current = true;

    const initializeAuth = async () => {
      try {
        const tokenResponse = await fetch(`${API_BASE_URL}/api/auth/token`, {
          method: "POST",
          credentials: "include",
        });

        if (!tokenResponse.ok) {
          throw new Error("Access Token 재발급에 실패했습니다.");
        }

        const tokenResult: ApiResponse<TokenResponse> =
          await tokenResponse.json();

        // Refresh Token 쿠키가 없으면 data가 null
        if (!tokenResult.success || !tokenResult.data?.accessToken) {
          clearAuth();
          return;
        }

        setAccessToken(tokenResult.data.accessToken);

        const userResult = await apiFetch<ApiResponse<User>>("/api/auth/me");

        if (!userResult.success || !userResult.data) {
          clearAuth();
          return;
        }

        setUser(userResult.data);
      } catch (error) {
        console.error("인증 초기화 실패:", error);
        clearAuth();
      } finally {
        setInitialized(true);
      }
    };

    void initializeAuth();
  }, [clearAuth, setAccessToken, setInitialized, setUser]);

  return null;
}
