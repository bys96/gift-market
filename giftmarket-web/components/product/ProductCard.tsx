"use client";

import Image from "next/image";
import Link from "next/link";
import { useWishlistStore } from "@/stores/wishlist-store";
import type { Product, ProductSummary } from "@/types/product";
import { resolveImageUrl } from "@/utils/image-url";

interface ProductCardProps {
  product: Product | ProductSummary;
}

function formatPrice(price: number) {
  return `${price.toLocaleString("ko-KR")}원`;
}

function isProductSummary(
  product: Product | ProductSummary,
): product is ProductSummary {
  return "representativeImageKey" in product;
}

export default function ProductCard({ product }: ProductCardProps) {
  const items = useWishlistStore((state) => state.items);
  const hydrated = useWishlistStore((state) => state.hydrated);
  const toggleItem = useWishlistStore((state) => state.toggleItem);

  const isSummary = isProductSummary(product);

  const imageUrl = isSummary
    ? resolveImageUrl(product.representativeImageKey)
    : product.imageUrl;

  const brandName = product.brandName ?? "브랜드 정보 없음";

  const freeShipping = isSummary
    ? product.freeShipping
    : product.isFreeShipping;

  const isSoldOut = isSummary && product.status === "SOLD_OUT";

  const wishlistProduct: Product = {
    id: product.id,
    name: product.name,
    brandName,
    price: product.price,
    imageUrl: imageUrl ?? "",
    isFreeShipping: freeShipping,
  };

  const isWishlisted = hydrated && items.some((item) => item.id === product.id);

  return (
    <article className="product-card">
      <div className="product-card-image-area">
        <Link
          href={`/products/${product.id}`}
          className="product-card-image-link"
        >
          <div className="product-card-image-wrapper">
            {imageUrl ? (
              <Image
                src={imageUrl}
                alt={product.name}
                fill
                sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 25vw"
                className="product-card-image"
              />
            ) : (
              <div className="product-card-image-placeholder">
                이미지 없음
              </div>
            )}

            {isSoldOut && <div className="product-card-sold-out">품절</div>}
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

            toggleItem(wishlistProduct);
          }}
        >
          {isWishlisted ? "♥" : "♡"}
        </button>
      </div>

      <Link href={`/products/${product.id}`} className="product-card-content">
        <p className="product-card-brand">{brandName}</p>

        <h2 className="product-card-name">{product.name}</h2>

        <strong className="product-card-price">
          {formatPrice(product.price)}
        </strong>

        {freeShipping ? (
          <span className="product-card-shipping">무료배송</span>
        ) : isSummary && product.shippingFee > 0 ? (
          <span className="product-card-shipping">
            배송비 {formatPrice(product.shippingFee)}
          </span>
        ) : null}
      </Link>
    </article>
  );
}
