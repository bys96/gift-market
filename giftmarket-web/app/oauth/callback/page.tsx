"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { initializeAuth } from "@/lib/auth-initialization";
import { useAuthStore } from "@/stores/auth-store";
import { useWishlistStore } from "@/stores/wishlist-store";

const DEFAULT_REDIRECT_URL = "/";
const LOGIN_REDIRECT_STORAGE_KEY = "login_redirect_url";

function resolveRedirectUrl(redirect: string | null): string {
  if (!redirect) {
    return DEFAULT_REDIRECT_URL;
  }

  // 외부 URL redirect 방지
  if (
    !redirect.startsWith("/") ||
    redirect.startsWith("//") ||
    /[\\\u0000-\u0020\u007f]/.test(redirect)
  ) {
    return DEFAULT_REDIRECT_URL;
  }

  return redirect;
}

export default function OAuthCallbackPage() {
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const user = useAuthStore((state) => state.user);
  const initializationError = useAuthStore((state) => state.initializationError);

  useEffect(() => {
    void initializeAuth();
  }, []);

  useEffect(() => {
    if (!initialized || initializationError) return;
    if (!isAuthenticated || !user) {
      useWishlistStore.getState().resetWishlist();
      sessionStorage.removeItem(LOGIN_REDIRECT_STORAGE_KEY);
      router.replace("/login");
      return;
    }
    const redirectUrl = resolveRedirectUrl(
      sessionStorage.getItem(LOGIN_REDIRECT_STORAGE_KEY),
    );
    sessionStorage.removeItem(LOGIN_REDIRECT_STORAGE_KEY);
    router.replace(redirectUrl);
  }, [initialized, initializationError, isAuthenticated, user, router]);

  return (
    <main>
      <p>{initializationError ?? "로그인 처리 중입니다..."}</p>
      {initializationError && (
        <button type="button" onClick={() => window.location.reload()}>
          다시 시도
        </button>
      )}
    </main>
  );
}
