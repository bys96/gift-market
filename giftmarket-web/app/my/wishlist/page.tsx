"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useRouter } from "next/navigation";

import ProductCard from "@/components/product/ProductCard";
import { useAuthStore } from "@/stores/auth-store";
import { useWishlistStore } from "@/stores/wishlist-store";

export default function MyWishlistPage() {
  const router = useRouter();

  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const items = useWishlistStore((state) => state.items);
  const hydrated = useWishlistStore((state) => state.hydrated);
  const clearWishlist = useWishlistStore((state) => state.clearWishlist);

  useEffect(() => {
    if (hydrated && (!isAuthenticated || !user)) {
      router.replace("/login");
    }
  }, [hydrated, isAuthenticated, user, router]);

  if (!hydrated) {
    return null;
  }

  if (!isAuthenticated || !user) {
    return null;
  }

  return (
    <div className="my-wishlist-page">
      <div className="my-wishlist-header">
        <div>
          <p className="my-wishlist-eyebrow">나의 쇼핑</p>

          <h1 className="my-wishlist-title">
            찜한 상품
            <span>{items.length}</span>
          </h1>
        </div>

        <Link href="/my" className="my-wishlist-back-link">
          마이페이지
        </Link>
      </div>

      {items.length > 0 ? (
        <>
          <div className="my-wishlist-toolbar">
            <p>
              총 <strong>{items.length}</strong>개의 상품
            </p>

            <button
              type="button"
              className="my-wishlist-clear-button"
              onClick={clearWishlist}
            >
              전체 삭제
            </button>
          </div>

          <div className="product-list">
            {items.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        </>
      ) : (
        <section className="my-wishlist-empty">
          <div className="my-wishlist-empty-icon" aria-hidden="true">
            ♡
          </div>

          <h2>찜한 상품이 없습니다.</h2>

          <p>마음에 드는 상품을 찜해보세요.</p>

          <Link href="/products" className="my-wishlist-empty-link">
            상품 보러 가기
          </Link>
        </section>
      )}
    </div>
  );
}
