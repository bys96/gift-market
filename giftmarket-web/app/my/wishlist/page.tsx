"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useRouter } from "next/navigation";

import ProductCard from "@/components/product/ProductCard";
import { useAuthStore } from "@/stores/auth-store";
import { useWishlistStore } from "@/stores/wishlist-store";

export default function MyWishlistPage() {
  const router = useRouter();

  const authInitialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const items = useWishlistStore((state) => state.items);
  const initialized = useWishlistStore((state) => state.initialized);
  const isLoading = useWishlistStore((state) => state.isLoading);
  const errorMessage = useWishlistStore((state) => state.errorMessage);
  const loadWishlist = useWishlistStore((state) => state.loadWishlist);

  useEffect(() => {
    if (!authInitialized) return;
    if (!isAuthenticated || !user) {
      router.replace("/login?redirect=%2Fmy%2Fwishlist");
      return;
    }
    void loadWishlist();
  }, [authInitialized, isAuthenticated, user, router, loadWishlist]);

  if (!authInitialized || (isAuthenticated && !initialized)) {
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

      {isLoading && items.length === 0 ? (
        <section className="my-wishlist-empty">
          <h2>찜 목록을 불러오고 있습니다.</h2>
        </section>
      ) : errorMessage && items.length === 0 ? (
        <section className="my-wishlist-empty">
          <h2>찜 목록을 불러오지 못했습니다.</h2>
          <p>{errorMessage}</p>
          <button
            type="button"
            className="my-wishlist-empty-link"
            onClick={() => void loadWishlist(true)}
          >
            다시 시도
          </button>
        </section>
      ) : items.length > 0 ? (
        <>
          <div className="my-wishlist-toolbar">
            <p>
              총 <strong>{items.length}</strong>개의 상품
            </p>

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
