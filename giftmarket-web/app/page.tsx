"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import ProductCard from "@/components/product/ProductCard";
import { getProducts } from "@/lib/product-api";
import type { ProductSummary } from "@/types/product";

const HOME_PRODUCT_SIZE = 4;

const categories = [
  { name: "생일", emoji: "🎂" },
  { name: "감사", emoji: "💛" },
  { name: "축하", emoji: "🎉" },
  { name: "응원", emoji: "💪" },
  { name: "간식", emoji: "🍰" },
  { name: "뷰티", emoji: "💄" },
  { name: "패션", emoji: "👕" },
  { name: "리빙", emoji: "🏠" },
];

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
            <p className="home-section-eyebrow">CATEGORY</p>

            <h2 className="home-section-title">어떤 선물을 찾고 있나요?</h2>
          </div>
        </div>

        <div className="home-category-list">
          {categories.map((category) => (
            <Link
              key={category.name}
              href={`/products?keyword=${encodeURIComponent(category.name)}`}
              className="home-category-item"
            >
              <span className="home-category-icon" aria-hidden="true">
                {category.emoji}
              </span>

              <span className="home-category-name">{category.name}</span>
            </Link>
          ))}
        </div>
      </section>

      <section className="home-section">
        <div className="home-section-header">
          <div>
            <p className="home-section-eyebrow">RECOMMEND</p>

            <h2 className="home-section-title">지금 만나볼 수 있는 선물</h2>
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
