"use client";

import Image from "next/image";
import Link from "next/link";
import type { Product } from "@/types/product";
import { useWishlistStore } from "@/stores/wishlist-store";

interface WishlistProductCardProps {
  product: Product;
}

function formatPrice(price: number) {
  return `${price.toLocaleString("ko-KR")}원`;
}

export default function WishlistProductCard({
  product,
}: WishlistProductCardProps) {
  const removeItem = useWishlistStore((state) => state.removeItem);

  return (
    <article className="wishlist-product-card">
      <Link
        href={`/products/${product.id}`}
        className="wishlist-product-image-wrapper"
      >
        <Image
          src={product.imageUrl}
          alt={product.name}
          fill
          sizes="(max-width: 640px) 44vw, 220px"
          className="wishlist-product-image"
        />
      </Link>

      <div className="wishlist-product-info">
        <p className="wishlist-product-brand">{product.brandName}</p>

        <Link
          href={`/products/${product.id}`}
          className="wishlist-product-name"
        >
          {product.name}
        </Link>

        <strong className="wishlist-product-price">
          {formatPrice(product.price)}
        </strong>

        {product.isFreeShipping && (
          <span className="wishlist-product-shipping">무료배송</span>
        )}
      </div>

      <button
        type="button"
        className="wishlist-product-remove-button"
        aria-label={`${product.name} 찜 삭제`}
        onClick={() => removeItem(product.id)}
      >
        ×
      </button>
    </article>
  );
}
