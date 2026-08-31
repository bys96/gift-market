"use client";

import { type ReactNode, useEffect } from "react";
import { useRouter } from "next/navigation";

import AdminSidebar from "@/components/admin/AdminSidebar";
import { useAuthStore } from "@/stores/auth-store";

export default function AdminCenterLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const initialized = useAuthStore((state) => state.initialized);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const user = useAuthStore((state) => state.user);

  useEffect(() => {
    if (!initialized) return;
    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }
    if (user.role !== "ADMIN") router.replace("/");
  }, [initialized, isAuthenticated, router, user]);

  if (!initialized || !isAuthenticated || !user || user.role !== "ADMIN") {
    return <div className="admin-center-auth-loading">관리자 권한을 확인하고 있습니다.</div>;
  }

  return (
    <div className="admin-center-layout">
      <AdminSidebar />
      <div className="admin-center-content">{children}</div>
    </div>
  );
}
