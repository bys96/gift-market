"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import { getMyLatestSellerApplication, getMySeller } from "@/lib/seller-api";
import { useAuthStore } from "@/stores/auth-store";

export default function SellerPage() {
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }

    const resolveSellerRoute = async () => {
      try {
        setErrorMessage("");

        const seller = await getMySeller();
        if (seller?.status === "ACTIVE") {
          router.replace("/seller/dashboard");
          return;
        }
        if (seller) {
          setErrorMessage("현재 판매자 상태에서는 판매자센터를 이용할 수 없습니다.");
          return;
        }

        const application = await getMyLatestSellerApplication();

        if (!application) {
          router.replace("/seller/apply");
          return;
        }

        if (application.status === "PENDING") {
          router.replace("/seller/application");
          return;
        }

        if (application.status === "REJECTED") {
          router.replace("/seller/apply");
          return;
        }

        /*
         * 승인 직후 프론트 사용자 정보가 갱신되기 전이라면
         * role은 USER지만 신청 상태는 APPROVED일 수 있다.
         */
        if (application.status === "APPROVED") {
          router.replace("/seller/dashboard");
        }
      } catch (error) {
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "판매자 정보를 확인하지 못했습니다.",
        );
      }
    };

    resolveSellerRoute();
  }, [initialized, isAuthenticated, user, router]);

  if (errorMessage) {
    return (
      <main className="seller-entry-page">
        <section className="seller-entry-error">
          <h1>판매자 정보를 확인하지 못했습니다.</h1>

          <p>{errorMessage}</p>

          <button
            type="button"
            onClick={() => {
              window.location.reload();
            }}
          >
            다시 시도
          </button>
        </section>
      </main>
    );
  }

  return (
    <main className="seller-entry-page">
      <div className="seller-entry-loading">
        <span className="seller-entry-spinner" />

        <p>판매자 정보를 확인하고 있습니다.</p>
      </div>
    </main>
  );
}
