"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { API_BASE_URL } from "@/lib/api";
import MyMenuList from "@/components/my/MyMenuList";
import MyProfileCard from "@/components/my/MyProfileCard";
import MyQuickStats from "@/components/my/MyQuickStats";
import { useAuthStore } from "@/stores/auth-store";

interface MySummary {
  orderCount: number;
  wishlistCount: number;
  addressCount: number;
}

const INITIAL_SUMMARY: MySummary = {
  orderCount: 0,
  wishlistCount: 0,
  addressCount: 0,
};

export default function MyPage() {
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const clearAuth = useAuthStore((state) => state.clearAuth);

  const [summary] = useState<MySummary>(INITIAL_SUMMARY);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/");
    }
  }, [initialized, isAuthenticated, user, router]);

  const handleLogout = async () => {
    try {
      setIsLoggingOut(true);

      await fetch(`${API_BASE_URL}/api/auth/logout`, {
        method: "POST",
        credentials: "include",
      });

      clearAuth();
      router.replace("/");
      router.refresh();
    } catch (error) {
      console.error(error);
      alert("로그아웃 처리 중 오류가 발생했습니다.");
    } finally {
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

        <MyMenuList onLogout={handleLogout} isLoggingOut={isLoggingOut} />
      </div>
    </div>
  );
}
