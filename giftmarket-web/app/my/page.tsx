"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { apiFetch } from "@/lib/api";
import { getMyAddresses } from "@/lib/address-api";
import { getMyOrders } from "@/lib/order-api";
import { getWishlistCount } from "@/lib/wishlist-api";
import MyMenuList from "@/components/my/MyMenuList";
import MyProfileCard from "@/components/my/MyProfileCard";
import MyQuickStats from "@/components/my/MyQuickStats";
import { useAuthStore } from "@/stores/auth-store";
import { useCartStore } from "@/stores/cart-store";
import { useWishlistStore } from "@/stores/wishlist-store";

interface MySummary {
  orderCount: number | null;
  wishlistCount: number | null;
  addressCount: number | null;
}

const INITIAL_SUMMARY: MySummary = {
  orderCount: null,
  wishlistCount: null,
  addressCount: null,
};

export default function MyPage() {
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const clearAuth = useAuthStore((state) => state.clearAuth);

  const [summary, setSummary] = useState<MySummary>(INITIAL_SUMMARY);
  const [summaryError, setSummaryError] = useState("");
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/");
    }
  }, [initialized, isAuthenticated, user, router]);

  useEffect(() => {
    if (!initialized || !isAuthenticated || !user) {
      return;
    }

    let cancelled = false;

    const loadSummary = async () => {
      const [ordersResult, addressesResult, wishlistResult] = await Promise.allSettled([
        getMyOrders(0, 1),
        getMyAddresses(),
        getWishlistCount(),
      ]);

      if (cancelled) {
        return;
      }

      setSummary({
        orderCount:
          ordersResult.status === "fulfilled"
            ? ordersResult.value.totalElements
            : null,
        wishlistCount:
          wishlistResult.status === "fulfilled" ? wishlistResult.value : null,
        addressCount:
          addressesResult.status === "fulfilled"
            ? addressesResult.value.length
            : null,
      });
      setSummaryError(
        ordersResult.status === "rejected" ||
          addressesResult.status === "rejected"
          || wishlistResult.status === "rejected"
          ? "일부 쇼핑 요약을 불러오지 못했습니다. 각 메뉴에서 다시 확인해주세요."
          : "",
      );
    };

    void loadSummary();

    return () => {
      cancelled = true;
    };
  }, [initialized, isAuthenticated, user]);

  const handleLogout = async () => {
    try {
      setIsLoggingOut(true);

      await apiFetch("/api/auth/logout", {
        method: "POST",
      });
    } catch (error) {
      console.error("서버 로그아웃 처리 실패:", error);
    } finally {
      // 서버 요청이 실패해도 프론트 로그인 상태는 제거
      clearAuth();
      useCartStore.getState().resetCart();
      useWishlistStore.getState().resetWishlist();
      router.replace("/");
      router.refresh();
      setIsLoggingOut(false);
    }
  };

  if (!user) {
    return null;
  }

  return (
    <div className="my-page">
      <div className="my-page-header">
        <h1 className="my-page-title">마이페이지</h1>
      </div>

      <div className="my-page-content">
        <MyProfileCard user={user} />

        <MyQuickStats
          orderCount={summary.orderCount}
          wishlistCount={summary.wishlistCount}
          addressCount={summary.addressCount}
        />

        {summaryError && <p className="my-summary-error">{summaryError}</p>}

        <MyMenuList onLogout={handleLogout} isLoggingOut={isLoggingOut} />
      </div>
    </div>
  );
}
