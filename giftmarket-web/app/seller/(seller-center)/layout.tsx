"use client";

import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";

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
  const pathname = usePathname();
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

  if (seller.status !== "ACTIVE" && seller.status !== "SALES_SUSPENDED") {
    return <div className="seller-center-layout"><div className="seller-center-content"><div className="seller-orders-state seller-orders-state-error"><p>현재 판매자 상태에서는 판매자센터를 이용할 수 없습니다.</p><Link href="/">쇼핑몰로 돌아가기</Link></div></div></div>;
  }

  const isSalesManagementRoute = pathname === "/seller/products/new" || pathname.endsWith("/edit");

  if (seller.status === "SALES_SUSPENDED" && isSalesManagementRoute) {
    return <div className="seller-center-layout"><SellerSidebar /><div className="seller-center-content"><div className="seller-sales-suspended-block"><strong>현재 판매가 정지된 상태입니다.</strong><p>상품 등록·수정 등 신규 판매 관련 기능은 이용할 수 없습니다. 기존 주문 및 클레임 처리는 계속할 수 있습니다.</p><Link href="/seller/products">상품 목록으로 돌아가기</Link></div></div></div>;
  }

  return (
    <div className="seller-center-layout">
      <SellerSidebar />

      <div className={`seller-center-content${seller.status === "SALES_SUSPENDED" ? " seller-center-content-sales-suspended" : ""}`}>
        {seller.status === "SALES_SUSPENDED" && <div className="seller-sales-suspended-notice" role="status"><strong>현재 판매가 정지된 상태입니다.</strong><span>신규 판매 관련 기능은 제한되며 기존 주문 및 클레임 처리는 계속할 수 있습니다.</span></div>}
        {children}
      </div>
    </div>
  );
}
