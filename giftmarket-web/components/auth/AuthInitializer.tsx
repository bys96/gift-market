"use client";

import { useEffect, useRef } from "react";
import { useAuthStore } from "@/stores/auth-store";

interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

interface TokenResponse {
  accessToken: string;
}

interface User {
  id: number;
  email: string;
  name: string;
  profileImageUrl: string;
  role: string;
}

const API_URL = process.env.NEXT_PUBLIC_API_BASE_URL;

export default function AuthInitializer() {
  const initializedRef = useRef(false);

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

        const accessToken = tokenResult.data.accessToken;

        const userResponse = await fetch(`${API_URL}/api/auth/me`, {
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

        setAccessToken(accessToken);
        setUser(userResult.data);
      } catch {
        clearAuth();
      }
    };

    initializeAuth();
  }, [clearAuth, setAccessToken, setUser]);

  return null;
}
