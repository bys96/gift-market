"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import ProductForm from "@/components/seller/ProductForm";
import { getProductDraft } from "@/lib/product-draft-api";
import { useAuthStore } from "@/stores/auth-store";
import type { ProductDraft } from "@/types/product-draft";

function SellerProductCreateContent() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const initialized = useAuthStore((state) => state.initialized);

  const user = useAuthStore((state) => state.user);

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [initialDraft, setInitialDraft] = useState<ProductDraft | null>(null);

  const [isLoadingDraft, setIsLoadingDraft] = useState(false);

  const [draftErrorMessage, setDraftErrorMessage] = useState("");

  const draftIdParam = searchParams.get("draftId");

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

  useEffect(() => {
    if (
      !initialized ||
      !isAuthenticated ||
      !user ||
      user.role !== "SELLER" ||
      !draftIdParam
    ) {
      return;
    }

    const draftId = Number(draftIdParam);

    if (!Number.isSafeInteger(draftId) || draftId <= 0) {
      // query parameter 검증 결과를 기존 오류 UI에 반영한다.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setDraftErrorMessage("올바르지 않은 임시저장 번호입니다.");

      return;
    }

    let cancelled = false;

    const loadDraft = async () => {
      try {
        setIsLoadingDraft(true);

        setDraftErrorMessage("");

        const draft = await getProductDraft(draftId);

        if (cancelled) {
          return;
        }

        if (draft.productId !== null) {
          throw new Error("신규 상품 임시저장 데이터가 아닙니다.");
        }

        setInitialDraft(draft);
      } catch (error) {
        if (cancelled) {
          return;
        }

        setDraftErrorMessage(
          error instanceof Error
            ? error.message
            : "임시저장 상품을 불러오지 못했습니다.",
        );
      } finally {
        if (!cancelled) {
          setIsLoadingDraft(false);
        }
      }
    };

    void loadDraft();

    return () => {
      cancelled = true;
    };
  }, [initialized, isAuthenticated, user, draftIdParam]);

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

  if (isLoadingDraft) {
    return (
      <main className="seller-product-form-page">
        <div className="common-inner">
          <div className="seller-application-loading">
            <span className="seller-application-loading-spinner" />

            <p>임시저장 상품을 불러오고 있습니다.</p>
          </div>
        </div>
      </main>
    );
  }

  if (draftErrorMessage) {
    return (
      <main className="seller-product-form-page">
        <div className="common-inner">
          <div className="seller-product-form-container">
            <div className="seller-product-form-error" role="alert">
              {draftErrorMessage}
            </div>

            <button
              type="button"
              className="seller-product-form-back-button"
              onClick={() => router.push("/seller/products")}
            >
              ← 상품 관리로 돌아가기
            </button>
          </div>
        </div>
      </main>
    );
  }

  return <ProductForm mode="create" initialDraft={initialDraft} />;
}

export default function SellerProductCreatePage() {
  return (
    <Suspense
      fallback={
        <main className="seller-product-form-page">
          <div className="common-inner">
            <div className="seller-application-loading">
              <span className="seller-application-loading-spinner" />
              <p>판매자 정보를 확인하고 있습니다.</p>
            </div>
          </div>
        </main>
      }
    >
      <SellerProductCreateContent />
    </Suspense>
  );
}
