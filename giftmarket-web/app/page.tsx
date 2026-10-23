"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

import HomeProductCarousel from "@/components/product/HomeProductCarousel";
import { getHomeProducts, getProducts } from "@/lib/product-api";
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
    minPrice: 10_000,
    maxPrice: 30_000,
  },
  {
    code: "FROM_30001_TO_50000",
    label: "3만원 ~ 5만원",
    minPrice: 30_000,
    maxPrice: 50_000,
  },
  { code: "OVER_50000", label: "5만원 이상", minPrice: 50_000 },
];

export default function HomePage() {
  const [homeData, setHomeData] = useState<HomeProductData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedPriceRange, setSelectedPriceRange] =
    useState<HomePriceRangeCode>("UNDER_10000");
  const [selectedOccasionId, setSelectedOccasionId] = useState<number | null>(
    null,
  );
  const [occasionProductsById, setOccasionProductsById] = useState<
    Record<number, ProductSummary[]>
  >({});
  const [occasionLoadingId, setOccasionLoadingId] = useState<number | null>(
    null,
  );
  const [occasionErrorMessage, setOccasionErrorMessage] = useState<
    string | null
  >(null);
  const [occasionErrorId, setOccasionErrorId] = useState<number | null>(null);

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

  useEffect(() => {
    const occasions = homeData?.giftOccasions ?? [];
    if (occasions.length === 0) {
      setSelectedOccasionId(null);
      return;
    }

    setSelectedOccasionId((currentId) =>
      occasions.some((occasion) => occasion.id === currentId)
        ? currentId
        : occasions[0].id,
    );
  }, [homeData]);

  useEffect(() => {
    if (selectedOccasionId === null) return;
    if (occasionProductsById[selectedOccasionId] !== undefined) return;

    let isActive = true;

    const loadOccasionProducts = async () => {
      try {
        setOccasionLoadingId(selectedOccasionId);
        setOccasionErrorMessage(null);
        setOccasionErrorId(null);

        const response = await getProducts({
          giftOccasionId: selectedOccasionId,
          excludeSoldOut: true,
          page: 0,
          size: 8,
        });

        if (isActive) {
          setOccasionProductsById((currentProducts) => ({
            ...currentProducts,
            [selectedOccasionId]: response.products,
          }));
        }
      } catch (error) {
        if (isActive) {
          setOccasionErrorMessage(
            error instanceof Error
              ? error.message
              : "추천 상품을 불러오지 못했습니다.",
          );
          setOccasionErrorId(selectedOccasionId);
        }
      } finally {
        if (isActive) {
          setOccasionLoadingId(null);
        }
      }
    };

    void loadOccasionProducts();

    return () => {
      isActive = false;
    };
  }, [occasionProductsById, selectedOccasionId]);

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

  const selectedOccasionProducts =
    selectedOccasionId === null
      ? []
      : occasionProductsById[selectedOccasionId] ?? [];
  const isSelectedOccasionLoading =
    selectedOccasionId === null ||
    occasionLoadingId === selectedOccasionId ||
    occasionProductsById[selectedOccasionId] === undefined;

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
          <h1 className="home-hero-title">
            특별한 마음을
            <br />
            선물로 보내세요
          </h1>
          <p className="home-hero-description">
            생일, 감사, 축하까지. 마음에 맞는 선물을 Gift Market에서 만나보세요.
          </p>
          <Link href="/products" className="home-hero-link">
            선물 둘러보기
          </Link>
        </div>
        <div className="home-hero-visual" aria-hidden="true">
          <span className="home-hero-gift">🎁</span>
        </div>
      </section>

      <section className="home-section home-occasion-section">
        <div className="home-section-header home-section-header-occasion">
          <div>
            <h2 className="home-section-title">상황에 맞는 선물 추천 🎁</h2>
          </div>
          {selectedOccasionId !== null && (
            <Link
              href={`/products?giftOccasionId=${selectedOccasionId}`}
              className="home-section-more"
            >
              전체 보기
            </Link>
          )}
        </div>

        {isLoading ? (
          <div className="home-occasion-loading">선물 상황을 불러오는 중입니다.</div>
        ) : errorMessage ? (
          <div className="home-occasion-loading">{errorMessage}</div>
        ) : homeData?.giftOccasions.length ? (
          <>
            <div
              className="home-occasion-tabs"
              role="tablist"
              aria-label="선물 상황 선택"
            >
              {homeData.giftOccasions.map((occasion) => (
                <button
                  key={occasion.id}
                  type="button"
                  role="tab"
                  aria-selected={selectedOccasionId === occasion.id}
                  className={
                    selectedOccasionId === occasion.id ? "is-active" : undefined
                  }
                  onClick={() => setSelectedOccasionId(occasion.id)}
                >
                  {occasion.name}
                </button>
              ))}
            </div>

            {occasionErrorId === selectedOccasionId && occasionErrorMessage ? (
              <div className="home-product-empty">{occasionErrorMessage}</div>
            ) : (
              <HomeProductCarousel
                products={selectedOccasionProducts}
                isLoading={isSelectedOccasionLoading}
                viewAllHref={`/products?giftOccasionId=${selectedOccasionId}`}
                resetKey={`occasion-${selectedOccasionId}`}
                emptyMessage="이 상황에 어울리는 판매 중인 상품이 없습니다."
              />
            )}
          </>
        ) : (
          <div className="home-occasion-loading">등록된 선물 상황이 없습니다.</div>
        )}
      </section>

      <section className="home-section">
        <div className="home-section-header">
          <div>
            <h2 className="home-section-title">지금 인기 있는 선물</h2>
          </div>
          <Link href="/products?sort=POPULAR" className="home-section-more">
            전체 보기
          </Link>
        </div>
        {errorMessage ? (
          <div className="home-product-placeholder">{errorMessage}</div>
        ) : (
          <HomeProductCarousel
            products={homeData?.popularProducts ?? []}
            isLoading={isLoading}
            viewAllHref="/products?sort=POPULAR"
            resetKey="popular"
            emptyMessage="아직 인기 선물을 준비하고 있습니다."
          />
        )}
      </section>

      <section className="home-section">
        <div className="home-section-header home-section-header-price">
          <div>
            <h2 className="home-section-title">가격대별 선물</h2>
          </div>
          <Link
            href={`/products?${priceRangeLink.toString()}`}
            className="home-section-more"
          >
            전체 보기
          </Link>
        </div>
        <div className="home-price-range-tabs" role="tablist" aria-label="가격대 선택">
          {PRICE_RANGES.map((priceRange) => (
            <button
              key={priceRange.code}
              type="button"
              role="tab"
              aria-selected={selectedPriceRange === priceRange.code}
              className={
                selectedPriceRange === priceRange.code ? "is-active" : undefined
              }
              onClick={() => setSelectedPriceRange(priceRange.code)}
            >
              {priceRange.label}
            </button>
          ))}
        </div>
        {errorMessage ? (
          <div className="home-product-placeholder">{errorMessage}</div>
        ) : (
          <HomeProductCarousel
            products={priceRangeProducts}
            isLoading={isLoading}
            viewAllHref={`/products?${priceRangeLink.toString()}`}
            resetKey={`price-${activePriceRange.code}`}
            emptyMessage="이 가격대의 판매 중인 상품이 없습니다."
          />
        )}
      </section>

      <section className="home-section">
        <div className="home-section-header">
          <div>
            <h2 className="home-section-title">새로 등록된 상품</h2>
          </div>
          <Link href="/products?sort=LATEST" className="home-section-more">
            전체 보기
          </Link>
        </div>
        {errorMessage ? (
          <div className="home-product-placeholder">{errorMessage}</div>
        ) : (
          <HomeProductCarousel
            products={homeData?.newestProducts ?? []}
            isLoading={isLoading}
            viewAllHref="/products?sort=LATEST"
            resetKey="newest"
            emptyMessage="새로 등록된 판매 중인 상품이 없습니다."
          />
        )}
      </section>
    </div>
  );
}
