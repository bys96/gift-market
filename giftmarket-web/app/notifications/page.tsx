"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import NotificationList from "@/components/notification/NotificationList";
import { getLoginRedirectUrl } from "@/lib/login-redirect";
import { useAuthStore } from "@/stores/auth-store";

export default function BuyerNotificationsPage() {
  const router = useRouter();
  const initialized = useAuthStore((state) => state.initialized);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const user = useAuthStore((state) => state.user);

  useEffect(() => {
    if (initialized && (!isAuthenticated || !user)) {
      router.replace(getLoginRedirectUrl());
    }
  }, [initialized, isAuthenticated, router, user]);

  if (!initialized || !isAuthenticated || !user) {
    return <div className="notification-page-state">로그인 정보를 확인하고 있습니다.</div>;
  }

  return (
    <NotificationList
      context="BUYER"
      title="내 알림"
      description="주문, 배송, 취소, 반품, 교환 및 문의 소식을 확인하세요."
    />
  );
}
