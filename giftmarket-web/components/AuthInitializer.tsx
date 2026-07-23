"use client";

import { useEffect } from "react";

import { apiFetch } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";
import type { ApiResponse } from "@/types/api";
import type { User } from "@/types/user";

interface TokenResponse {
  accessToken: string;
}

export default function AuthInitializer() {
  const setAccessToken = useAuthStore((state) => state.setAccessToken);
  const setUser = useAuthStore((state) => state.setUser);
  const clearAuth = useAuthStore((state) => state.clearAuth);

  useEffect(() => {
    const restoreAuth = async () => {
      try {
        const tokenResponse = await apiFetch<ApiResponse<TokenResponse>>(
          "/api/auth/token",
          {
            method: "POST",
          },
        );

        const accessToken = tokenResponse.data.accessToken;

        setAccessToken(accessToken);

        const userResponse = await apiFetch<ApiResponse<User>>("/api/auth/me", {
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        });

        setUser(userResponse.data);
      } catch {
        clearAuth();
      }
    };

    restoreAuth();
  }, [setAccessToken, setUser, clearAuth]);

  return null;
}
