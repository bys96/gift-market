"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { API_BASE_URL } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";

export default function LoginPage() {
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const user = useAuthStore((state) => state.user);

  const googleLoginUrl = `${API_BASE_URL}/oauth2/authorization/google`;
  const kakaoLoginUrl = `${API_BASE_URL}/oauth2/authorization/kakao`;

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (isAuthenticated && user) {
      router.replace("/");
    }
  }, [initialized, isAuthenticated, user, router]);

  if (!initialized) {
    return (
      <main>
        <p>로그인 정보를 확인하고 있습니다.</p>
      </main>
    );
  }

  if (isAuthenticated && user) {
    return null;
  }

  return (
    <main>
      <h1>로그인</h1>

      <a href={googleLoginUrl}>Google로 로그인</a>
      <br />
      <a href={kakaoLoginUrl}>카카오로 로그인</a>
    </main>
  );
}
