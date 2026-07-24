import Image from "next/image";
import Link from "next/link";

import type { Product } from "@/types/product";

interface ProductCardProps {
  product: Product;
}

export default function ProductCard({ product }: ProductCardProps) {
  return (
    <article className="product-card">
      <Link
        href={`/products/${product.id}`}
        className="product-card-link"
        aria-label={`${product.name} 상품 상세 보기`}
      >
        <div className="product-card-image-wrapper">
          <Image
            src={product.imageUrl}
            alt={product.name}
            fill
            sizes="(max-width: 640px) 50vw, (max-width: 900px) 33vw, 25vw"
            className="product-card-image"
          />

          <span className="product-card-wishlist" aria-hidden="true">
            ♡
          </span>
        </div>

        <div className="product-card-content">
          <p className="product-card-brand">{product.brandName}</p>

          <h3 className="product-card-name">{product.name}</h3>

          <strong className="product-card-price">
            {product.price.toLocaleString("ko-KR")}원
          </strong>

          {product.isFreeShipping && (
            <span className="product-card-shipping">무료배송</span>
          )}
        </div>
      </Link>
    </article>
  );
}
