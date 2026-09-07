import { ApiError, apiFetch, refreshAccessToken } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";
import { useWishlistStore } from "@/stores/wishlist-store";
import type { ApiResponse } from "@/types/api";
import type { User } from "@/types/user";

// Strict Mode 재실행/재마운트에도 초기 복구는 문서당 한 번만 수행한다.
let initializationPromise: Promise<void> | null = null;

async function loadCurrentUser(): Promise<ApiResponse<User>> {
  for (let attempt = 0; ; attempt += 1) {
    try {
      // 방금 회전한 토큰으로 사용자 확인 중에는 추가 rotation을 하지 않는다.
      return await apiFetch<ApiResponse<User>>("/api/auth/me", { skipAuthRefresh: true });
    } catch (error) {
      const transient = error instanceof TypeError
        || (error instanceof ApiError && [502, 503, 504].includes(error.status));
      if (!transient || attempt >= 1) throw error;
      await new Promise((resolve) => setTimeout(resolve, 500));
    }
  }
}

export function initializeAuth(): Promise<void> {
  if (initializationPromise) return initializationPromise;
  initializationPromise = (async () => {
    const auth = useAuthStore.getState();
    auth.setInitializationError(null);
    const clearSession = () => {
      auth.clearAuth();
      useWishlistStore.getState().resetWishlist();
      auth.setInitialized(true);
    };
    try {
      const token = await refreshAccessToken();
      if (!token) {
        clearSession();
        return;
      }
      const result = await loadCurrentUser();
      if (result?.success !== true || !result.data) {
        throw new Error("사용자 정보를 확인하지 못했습니다.");
      }
      auth.setUser(result.data);
      auth.setInitialized(true);
      try {
        await useWishlistStore.getState().loadWishlist(true);
      } catch {
        // 찜 목록 오류가 인증 성공을 취소하지 않는다.
      }
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        clearSession();
        return;
      }
      // 인증 여부 불명: guard가 비로그인으로 판단하지 않도록 initialized=false 유지.
      auth.setInitializationError("로그인 상태를 확인하지 못했습니다. 잠시 후 다시 확인해주세요.");
    }
  })();
  return initializationPromise;
}
