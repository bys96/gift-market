"use client";

import Image from "next/image";
import { Suspense, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import { API_BASE_URL } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";

const DEFAULT_REDIRECT_URL = "/";
const LOGIN_REDIRECT_STORAGE_KEY = "login_redirect_url";

function resolveRedirectUrl(redirect: string | null): string {
  if (!redirect) {
    return DEFAULT_REDIRECT_URL;
  }

  if (!redirect.startsWith("/") || redirect.startsWith("//")) {
    return DEFAULT_REDIRECT_URL;
  }

  return redirect;
}

function LoginContent() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const initialized = useAuthStore((state) => state.initialized);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const user = useAuthStore((state) => state.user);

  const redirectUrl = resolveRedirectUrl(searchParams.get("redirect"));

  const googleLoginUrl = `${API_BASE_URL}/oauth2/authorization/google`;

  const kakaoLoginUrl = `${API_BASE_URL}/oauth2/authorization/kakao`;

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (isAuthenticated && user) {
      router.replace(redirectUrl);
    }
  }, [initialized, isAuthenticated, user, redirectUrl, router]);

  const handleOAuthLogin = () => {
    sessionStorage.setItem(LOGIN_REDIRECT_STORAGE_KEY, redirectUrl);
  };

  if (!initialized) {
    return (
      <main className="login-page">
        <section className="login-card">
          <p className="login-loading">로그인 정보를 확인하고 있습니다.</p>
        </section>
      </main>
    );
  }

  if (isAuthenticated && user) {
    return null;
  }

  return (
    <main className="login-page">
      <section className="login-card">
        <header className="login-header">
          <p className="login-brand">Gift Market</p>

          <h1 className="login-title">로그인</h1>

          <p className="login-description">
            Gift Market에서 마음을 전해보세요.
          </p>
        </header>

        <div className="login-provider-list">
          <a
            href={googleLoginUrl}
            className="login-provider-google"
            onClick={handleOAuthLogin}
          >
            <Image
              src="/images/auth/google-logo.svg"
              alt=""
              width={20}
              height={20}
              className="login-google-logo"
            />

            <span className="login-google-text">Google로 계속하기</span>
          </a>

          <a
            href={kakaoLoginUrl}
            className="login-provider-kakao"
            onClick={handleOAuthLogin}
          >
            <Image
              src="/images/auth/kakao-login.png"
              alt="카카오 로그인"
              width={300}
              height={45}
              className="login-kakao-image"
            />
          </a>
        </div>

        <p className="login-notice">
          로그인하면 Gift Market의 서비스 이용약관 및 개인정보 처리방침에 동의한
          것으로 간주됩니다.
        </p>
      </section>
    </main>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <main className="login-page">
          <section className="login-card">
            <p className="login-loading">로그인 정보를 확인하고 있습니다.</p>
          </section>
        </main>
      }
    >
      <LoginContent />
    </Suspense>
  );
}
