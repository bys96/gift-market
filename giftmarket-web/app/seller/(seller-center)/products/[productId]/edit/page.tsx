"use client";

import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import ProductForm from "@/components/seller/ProductForm";
import { getSellerProduct } from "@/lib/product-api";
import { useAuthStore } from "@/stores/auth-store";
import type { SellerProduct } from "@/types/product";

export default function SellerProductEditPage() {
  const params = useParams<{ productId: string }>();
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [product, setProduct] = useState<SellerProduct | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const productId = Number(params.productId);

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }

    if (user.role !== "SELLER" && user.role !== "ADMIN") {
      router.replace("/seller");
    }
  }, [initialized, isAuthenticated, user, router]);

  useEffect(() => {
    if (
      !initialized ||
      !isAuthenticated ||
      !user ||
      (user.role !== "SELLER" && user.role !== "ADMIN")
    ) {
      return;
    }

    if (!Number.isSafeInteger(productId) || productId <= 0) {
      // route parameter 검증 결과를 기존 오류 UI에 반영한다.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setErrorMessage("올바르지 않은 상품 번호입니다.");
      setIsLoading(false);
      return;
    }

    let cancelled = false;

    const loadProduct = async () => {
      try {
        setIsLoading(true);
        setErrorMessage("");

        const productResponse = await getSellerProduct(productId);

        if (cancelled) {
          return;
        }

        setProduct(productResponse);
      } catch (error) {
        if (cancelled) {
          return;
        }

        setErrorMessage(
          error instanceof Error
            ? error.message
            : "상품 정보를 불러오지 못했습니다.",
        );
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    void loadProduct();

    return () => {
      cancelled = true;
    };
  }, [initialized, isAuthenticated, user, productId]);

  if (
    !initialized ||
    !isAuthenticated ||
    !user ||
    (user.role !== "SELLER" && user.role !== "ADMIN")
  ) {
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

  if (isLoading) {
    return (
      <main className="seller-product-form-page">
        <div className="common-inner">
          <div className="seller-application-loading">
            <span className="seller-application-loading-spinner" />
            <p>상품 정보를 불러오고 있습니다.</p>
          </div>
        </div>
      </main>
    );
  }

  if (errorMessage || !product) {
    return (
      <main className="seller-product-form-page">
        <div className="common-inner">
          <div className="seller-product-form-container">
            <div className="seller-product-form-error" role="alert">
              {errorMessage || "상품 정보를 확인할 수 없습니다."}
            </div>

            <button
              type="button"
              className="seller-product-form-back-button"
              onClick={() => router.push(`/seller/products/${productId}`)}
            >
              ← 상품 정보로 돌아가기
            </button>
          </div>
        </div>
      </main>
    );
  }

  return <ProductForm mode="edit" initialProduct={product} />;
}
