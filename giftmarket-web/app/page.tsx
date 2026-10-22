"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

import ProductCard from "@/components/product/ProductCard";
import { getHomeProducts } from "@/lib/product-api";
import type {
  HomePriceRangeCode,
  HomeProductData,
  ProductSummary,
} from "@/types/product";

const PRICE_RANGES: Array<{
  code: HomePriceRangeCode;
  label: string;
  minPrice: number;
  maxPrice?: number;
}> = [
  { code: "UNDER_10000", label: "1만원 이하", minPrice: 0, maxPrice: 10_000 },
  {
    code: "FROM_10001_TO_30000",
    label: "1만원 ~ 3만원",
    minPrice: 10_001,
    maxPrice: 30_000,
  },
  {
    code: "FROM_30001_TO_50000",
    label: "3만원 ~ 5만원",
    minPrice: 30_001,
    maxPrice: 50_000,
  },
  { code: "OVER_50000", label: "5만원 이상", minPrice: 50_001 },
];

function ProductSection({
  products,
  isLoading,
  emptyMessage,
}: {
  products: ProductSummary[];
  isLoading: boolean;
  emptyMessage: string;
}) {
  if (isLoading) {
    return <div className="home-product-placeholder">상품을 불러오는 중입니다.</div>;
  }

  if (products.length === 0) {
    return <div className="home-product-empty">{emptyMessage}</div>;
  }

  return (
    <div className="product-list">
      {products.map((product) => (
        <ProductCard key={product.id} product={product} />
      ))}
    </div>
  );
}

export default function HomePage() {
  const [homeData, setHomeData] = useState<HomeProductData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedPriceRange, setSelectedPriceRange] =
    useState<HomePriceRangeCode>("UNDER_10000");

  useEffect(() => {
    let isMounted = true;

    const loadHomeProducts = async () => {
      try {
        setIsLoading(true);
        setErrorMessage(null);

        const response = await getHomeProducts();
        if (isMounted) {
          setHomeData(response);
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage(
            error instanceof Error
              ? error.message
              : "메인 상품 정보를 불러오지 못했습니다.",
          );
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    void loadHomeProducts();

    return () => {
      isMounted = false;
    };
  }, []);

  const activePriceRange = useMemo(
    () =>
      PRICE_RANGES.find((priceRange) => priceRange.code === selectedPriceRange) ??
      PRICE_RANGES[0],
    [selectedPriceRange],
  );

  const priceRangeProducts = useMemo(
    () =>
      homeData?.priceRanges.find(
        (priceRange) => priceRange.code === activePriceRange.code,
      )?.products ?? [],
    [activePriceRange.code, homeData],
  );

  const priceRangeLink = new URLSearchParams({
    excludeSoldOut: "true",
    minPrice: String(activePriceRange.minPrice),
  });
  if (activePriceRange.maxPrice !== undefined) {
    priceRangeLink.set("maxPrice", String(activePriceRange.maxPrice));
  }

  return (
    <div className="home">
      <section className="home-hero">
        <div className="home-hero-content">
          <p className="home-hero-eyebrow">마음을 전하는 가장 쉬운 방법</p>
          <h1 className="home-hero-title">특별한 마음을<br />선물로 보내세요</h1>
          <p className="home-hero-description">생일, 감사, 축하까지. 마음에 맞는 선물을 Gift Market에서 만나보세요.</p>
          <Link href="/products" className="home-hero-link">선물 둘러보기</Link>
        </div>
        <div className="home-hero-visual" aria-hidden="true"><span className="home-hero-gift">🎁</span></div>
      </section>

      <section className="home-section home-occasion-section">
        <div className="home-section-header"><div><p className="home-section-eyebrow">GIFT OCCASIONS</p><h2 className="home-section-title">어떤 마음을 전할까요?</h2></div></div>
        {isLoading ? <div className="home-occasion-loading">선물 상황을 불러오는 중입니다.</div> : errorMessage ? <div className="home-occasion-loading">{errorMessage}</div> : homeData?.giftOccasions.length ? (
          <div className="home-occasion-list">
            {homeData.giftOccasions.map((occasion) => (
              <Link key={occasion.id} href={`/products?giftOccasionId=${occasion.id}`} className="home-occasion-item"><span className="home-occasion-icon" aria-hidden="true">✦</span><span>{occasion.name}</span></Link>
            ))}
          </div>
        ) : <div className="home-occasion-loading">등록된 선물 상황이 없습니다.</div>}
      </section>

      <section className="home-section">
        <div className="home-section-header"><div><p className="home-section-eyebrow">POPULAR GIFTS</p><h2 className="home-section-title">지금 인기 있는 선물</h2></div></div>
        {errorMessage ? <div className="home-product-placeholder">{errorMessage}</div> : <ProductSection products={homeData?.popularProducts ?? []} isLoading={isLoading} emptyMessage="아직 인기 선물을 준비하고 있습니다." />}
      </section>

      <section className="home-section">
        <div className="home-section-header home-section-header-price"><div><p className="home-section-eyebrow">PRICE RANGE</p><h2 className="home-section-title">가격대별 선물</h2></div><Link href={`/products?${priceRangeLink.toString()}`} className="home-section-more">전체 보기</Link></div>
        <div className="home-price-range-tabs" role="tablist" aria-label="가격대 선택">
          {PRICE_RANGES.map((priceRange) => <button key={priceRange.code} type="button" role="tab" aria-selected={selectedPriceRange === priceRange.code} className={selectedPriceRange === priceRange.code ? "is-active" : undefined} onClick={() => setSelectedPriceRange(priceRange.code)}>{priceRange.label}</button>)}
        </div>
        {errorMessage ? <div className="home-product-placeholder">{errorMessage}</div> : <ProductSection products={priceRangeProducts} isLoading={isLoading} emptyMessage="이 가격대의 판매 중인 상품이 없습니다." />}
      </section>

      <section className="home-section">
        <div className="home-section-header"><div><p className="home-section-eyebrow">NEW PRODUCTS</p><h2 className="home-section-title">새로 등록된 상품</h2></div><Link href="/products?excludeSoldOut=true" className="home-section-more">전체 보기</Link></div>
        {errorMessage ? <div className="home-product-placeholder">{errorMessage}</div> : <ProductSection products={homeData?.newestProducts ?? []} isLoading={isLoading} emptyMessage="새로 등록된 판매 중인 상품이 없습니다." />}
      </section>
    </div>
  );
}
