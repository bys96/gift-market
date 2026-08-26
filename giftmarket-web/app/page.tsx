"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import ProductCard from "@/components/product/ProductCard";
import { getProducts } from "@/lib/product-api";
import type { ProductSummary } from "@/types/product";

const HOME_PRODUCT_SIZE = 4;

export default function HomePage() {
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    const loadProducts = async () => {
      try {
        setIsLoading(true);
        setErrorMessage(null);

        const response = await getProducts({
          page: 0,
          size: HOME_PRODUCT_SIZE,
          excludeSoldOut: true,
        });

        if (!isMounted) {
          return;
        }

        setProducts(response.products);
      } catch (error) {
        console.error(error);

        if (!isMounted) {
          return;
        }

        setProducts([]);
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "상품을 불러오지 못했습니다.",
        );
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    void loadProducts();

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <div className="home">
      <section className="home-hero">
        <div className="home-hero-content">
          <p className="home-hero-eyebrow">마음을 전하는 가장 쉬운 방법</p>

          <h1 className="home-hero-title">
            소중한 사람에게
            <br />
            특별한 선물을 보내세요
          </h1>

          <p className="home-hero-description">
            생일, 감사, 축하까지 상황에 맞는 선물을 한곳에서 만나보세요.
          </p>

          <Link href="/products" className="home-hero-link">
            선물 둘러보기
          </Link>
        </div>

        <div className="home-hero-visual" aria-hidden="true">
          <span className="home-hero-gift">🎁</span>
        </div>
      </section>

      <section className="home-section">
        <div className="home-section-header">
          <div>
            <p className="home-section-eyebrow">NEW PRODUCTS</p>

            <h2 className="home-section-title">새로 등록된 상품</h2>
          </div>

          <Link
            href="/products?excludeSoldOut=true"
            className="home-section-more"
          >
            전체 보기
          </Link>
        </div>

        {isLoading && (
          <div className="home-product-placeholder">
            상품을 불러오는 중입니다.
          </div>
        )}

        {!isLoading && errorMessage && (
          <div className="home-product-placeholder">{errorMessage}</div>
        )}

        {!isLoading && !errorMessage && products.length === 0 && (
          <div className="home-product-placeholder">
            현재 판매 중인 상품이 없습니다.
          </div>
        )}

        {!isLoading && !errorMessage && products.length > 0 && (
          <div className="product-list">
            {products.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
