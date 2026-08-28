"use client";

import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import SellerSidebar from "@/components/seller/SellerSidebar";
import { getMySeller } from "@/lib/seller-api";
import { useAuthStore } from "@/stores/auth-store";
import type { Seller } from "@/types/seller";

interface SellerCenterLayoutProps {
  children: ReactNode;
}

export default function SellerCenterLayout({
  children,
}: SellerCenterLayoutProps) {
  const router = useRouter();
  const initialized = useAuthStore((state) => state.initialized);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const user = useAuthStore((state) => state.user);
  const [seller, setSeller] = useState<Seller | null>(null);
  const [isChecking, setIsChecking] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!initialized) return;
    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }

    let active = true;
    const checkSeller = async () => {
      try {
        setIsChecking(true);
        setError("");
        const currentSeller = await getMySeller();
        if (!active) return;
        if (!currentSeller) {
          router.replace("/seller");
          return;
        }
        setSeller(currentSeller);
      } catch (failure) {
        if (active) {
          setError(failure instanceof Error ? failure.message : "판매자 정보를 확인하지 못했습니다.");
        }
      } finally {
        if (active) setIsChecking(false);
      }
    };
    void checkSeller();
    return () => { active = false; };
  }, [initialized, isAuthenticated, router, user]);

  if (!initialized || isChecking || !isAuthenticated || !user) {
    return <div className="seller-center-layout"><div className="seller-center-content"><div className="seller-orders-auth-loading">판매자 정보를 확인하고 있습니다.</div></div></div>;
  }

  if (error) {
    return <div className="seller-center-layout"><div className="seller-center-content"><div className="seller-orders-state seller-orders-state-error"><p>{error}</p><button type="button" onClick={() => window.location.reload()}>다시 시도</button></div></div></div>;
  }

  if (!seller) return null;

  if (seller.status !== "ACTIVE") {
    return <div className="seller-center-layout"><div className="seller-center-content"><div className="seller-orders-state seller-orders-state-error"><p>현재 판매자 상태에서는 판매자센터를 이용할 수 없습니다.</p><Link href="/">쇼핑몰로 돌아가기</Link></div></div></div>;
  }

  return (
    <div className="seller-center-layout">
      <SellerSidebar />

      <div className="seller-center-content">{children}</div>
    </div>
  );
}
