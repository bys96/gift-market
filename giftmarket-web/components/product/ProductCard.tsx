"use client";

import Image from "next/image";
import Link from "next/link";
import { useWishlistStore } from "@/stores/wishlist-store";
import type { Product } from "@/types/product";

interface ProductCardProps {
  product: Product;
}

function formatPrice(price: number) {
  return `${price.toLocaleString("ko-KR")}원`;
}

export default function ProductCard({ product }: ProductCardProps) {
  const items = useWishlistStore((state) => state.items);
  const hydrated = useWishlistStore((state) => state.hydrated);
  const toggleItem = useWishlistStore((state) => state.toggleItem);

  const isWishlisted = hydrated && items.some((item) => item.id === product.id);

  return (
    <article className="product-card">
      <div className="product-card-image-area">
        <Link
          href={`/products/${product.id}`}
          className="product-card-image-link"
        >
          <div className="product-card-image-wrapper">
            <Image
              src={product.imageUrl}
              alt={product.name}
              fill
              sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 25vw"
              className="product-card-image"
            />
          </div>
        </Link>

        <button
          type="button"
          className={`product-card-wishlist ${
            isWishlisted ? "product-card-wishlist-active" : ""
          }`}
          aria-label={
            isWishlisted ? `${product.name} 찜 삭제` : `${product.name} 찜하기`
          }
          disabled={!hydrated}
          onClick={() => {
            if (!hydrated) {
              return;
            }

            toggleItem(product);
          }}
        >
          {isWishlisted ? "♥" : "♡"}
        </button>
      </div>

      <Link href={`/products/${product.id}`} className="product-card-content">
        <p className="product-card-brand">{product.brandName}</p>

        <h2 className="product-card-name">{product.name}</h2>

        <strong className="product-card-price">
          {formatPrice(product.price)}
        </strong>

        {product.isFreeShipping && (
          <span className="product-card-shipping">무료배송</span>
        )}
      </Link>
    </article>
  );
}
