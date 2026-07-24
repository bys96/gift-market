import Link from "next/link";

import ProductCard from "@/components/product/ProductCard";
import type { Product } from "@/types/product";

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

const recommendedProducts: Product[] = [
  {
    id: 1,
    name: "마음을 담은 프리미엄 초콜릿 선물 세트",
    brandName: "스위트하우스",
    price: 32900,
    imageUrl: "/images/products/product-1.jpg",
    isFreeShipping: true,
  },
  {
    id: 2,
    name: "향기로운 핸드크림 & 바디케어 세트",
    brandName: "오브제뷰티",
    price: 27900,
    imageUrl: "/images/products/product-2.jpg",
    isFreeShipping: true,
  },
  {
    id: 3,
    name: "매일 사용하기 좋은 머그컵 세트",
    brandName: "데일리리빙",
    price: 19800,
    imageUrl: "/images/products/product-3.jpg",
    isFreeShipping: false,
  },
  {
    id: 4,
    name: "특별한 날을 위한 꽃다발 선물",
    brandName: "플라워데이",
    price: 45000,
    imageUrl: "/images/products/product-4.jpg",
    isFreeShipping: true,
  },
];

export default function HomePage() {
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
              href={`/products?category=${encodeURIComponent(category.name)}`}
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
            <h2 className="home-section-title">지금 인기 있는 선물</h2>
          </div>

          <Link href="/products" className="home-section-more">
            전체 보기
          </Link>
        </div>

        <div className="product-list">
          {recommendedProducts.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      </section>
    </div>
  );
}
