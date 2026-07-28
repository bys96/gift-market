"use client";

import { useEffect, useRef } from "react";

import { useAuthStore } from "@/stores/auth-store";
import type { ApiResponse } from "@/types/api";
import type { User } from "@/types/user";

interface TokenResponse {
  accessToken: string;
}

const API_URL = process.env.NEXT_PUBLIC_API_BASE_URL;

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
        if (!API_URL) {
          clearAuth();
          return;
        }

        const tokenResponse = await fetch(`${API_URL}/api/auth/token`, {
          method: "POST",
          credentials: "include",
        });

        if (!tokenResponse.ok) {
          clearAuth();
          return;
        }

        const tokenResult: ApiResponse<TokenResponse> =
          await tokenResponse.json();

        if (!tokenResult.success || !tokenResult.data?.accessToken) {
          clearAuth();
          return;
        }

        const accessToken = tokenResult.data.accessToken;

        const userResponse = await fetch(`${API_URL}/api/auth/me`, {
          method: "GET",
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          credentials: "include",
        });

        if (!userResponse.ok) {
          clearAuth();
          return;
        }

        const userResult: ApiResponse<User> = await userResponse.json();

        if (!userResult.success || !userResult.data) {
          clearAuth();
          return;
        }

        setAccessToken(accessToken);
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
