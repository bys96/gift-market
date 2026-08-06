"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import ProductForm from "@/components/seller/ProductForm";
import { useAuthStore } from "@/stores/auth-store";

export default function SellerProductCreatePage() {
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }

    if (user.role !== "SELLER") {
      router.replace("/seller");
    }
  }, [initialized, isAuthenticated, user, router]);

  if (!initialized || !isAuthenticated || !user || user.role !== "SELLER") {
    return (
      <main className="seller-product-form-page">
        <div className="common-inner">
          <div className="seller-application-loading">
            <span className="seller-application-loading-spinner" />
            <p>판매자 정보를 확인하고 있습니다.</p>
          </div>
        </div>
      </main>
    );
  }

  return <ProductForm mode="create" />;
}
