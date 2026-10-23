"use client";

import Link from "next/link";
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import type { ProductSummary } from "@/types/product";
import ProductCard from "@/components/product/ProductCard";

interface HomeProductCarouselProps {
  products: ProductSummary[];
  viewAllHref: string;
  resetKey: string | number;
  isLoading?: boolean;
  emptyMessage: string;
}

function getItemsPerPage(width: number) {
  if (width < 600) return 4;
  if (width < 1024) return 3;
  return 4;
}

export default function HomeProductCarousel({
  products,
  viewAllHref,
  resetKey,
  isLoading = false,
  emptyMessage,
}: HomeProductCarouselProps) {
  const viewportRef = useRef<HTMLDivElement>(null);
  const pageRefs = useRef<Array<HTMLDivElement | null>>([]);
  const scrollFrameRef = useRef<number | null>(null);
  const [itemsPerPage, setItemsPerPage] = useState(4);
  const [activePage, setActivePage] = useState(0);

  const pages = useMemo(() => {
    const items = [
      ...products.map((product) => ({ type: "product" as const, product })),
      { type: "view-all" as const },
    ];

    return Array.from(
      { length: Math.ceil(items.length / itemsPerPage) },
      (_, index) => items.slice(index * itemsPerPage, (index + 1) * itemsPerPage),
    );
  }, [itemsPerPage, products]);

  const moveToPage = useCallback((pageIndex: number, behavior: ScrollBehavior = "smooth") => {
    const nextPage = Math.max(0, Math.min(pageIndex, pages.length - 1));
    pageRefs.current[nextPage]?.scrollIntoView({
      behavior:
        typeof window !== "undefined" &&
        window.matchMedia("(prefers-reduced-motion: reduce)").matches
          ? "auto"
          : behavior,
      block: "nearest",
      inline: "start",
    });
    setActivePage(nextPage);
  }, [pages.length]);

  useEffect(() => {
    const viewport = viewportRef.current;
    if (!viewport) return;

    const updateItemsPerPage = () => {
      setItemsPerPage(getItemsPerPage(viewport.clientWidth));
    };

    updateItemsPerPage();
    const observer = new ResizeObserver(updateItemsPerPage);
    observer.observe(viewport);

    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    pageRefs.current = pageRefs.current.slice(0, pages.length);
    setActivePage(0);
    viewportRef.current?.scrollTo({ left: 0, behavior: "auto" });
  }, [pages.length, resetKey]);

  const handleScroll = () => {
    if (scrollFrameRef.current !== null) return;

    scrollFrameRef.current = window.requestAnimationFrame(() => {
      const viewport = viewportRef.current;
      if (!viewport) return;

      if (
        viewport.scrollLeft + viewport.clientWidth >=
        viewport.scrollWidth - 2
      ) {
        setActivePage(pages.length - 1);
        scrollFrameRef.current = null;
        return;
      }

      const closestPage = pageRefs.current.reduce(
        (closestIndex, page, index) => {
          if (!page) return closestIndex;

          const currentDistance = Math.abs(page.offsetLeft - viewport.scrollLeft);
          const closest = pageRefs.current[closestIndex];
          const closestDistance = closest
            ? Math.abs(closest.offsetLeft - viewport.scrollLeft)
            : Number.POSITIVE_INFINITY;

          return currentDistance < closestDistance ? index : closestIndex;
        },
        0,
      );

      setActivePage(closestPage);
      scrollFrameRef.current = null;
    });
  };

  useEffect(() => () => {
    if (scrollFrameRef.current !== null) {
      window.cancelAnimationFrame(scrollFrameRef.current);
    }
  }, []);

  if (isLoading) {
    return <div className="home-product-placeholder">상품을 불러오는 중입니다.</div>;
  }

  if (products.length === 0) {
    return <div className="home-product-empty">{emptyMessage}</div>;
  }

  return (
    <div className="home-product-carousel">
      <div
        ref={viewportRef}
        className="home-product-carousel-viewport"
        onScroll={handleScroll}
      >
        <div className="home-product-carousel-track">
          {pages.map((page, pageIndex) => (
            <div
              key={`${resetKey}-${pageIndex}`}
              ref={(element) => {
                pageRefs.current[pageIndex] = element;
              }}
              className={`home-product-carousel-page ${page.length < itemsPerPage ? "is-incomplete" : ""} ${page.length === 1 ? "is-single" : ""}`}
            >
              {page.map((item) =>
                item.type === "product" ? (
                  <ProductCard key={item.product.id} product={item.product} />
                ) : (
                  <Link
                    key="view-all"
                    href={viewAllHref}
                    className="home-product-carousel-view-all"
                  >
                    <span aria-hidden="true" className="home-product-carousel-view-all-arrow">→</span>
                    <strong>전체 보기</strong>
                    <span>더 많은 선물 만나보기</span>
                  </Link>
                ),
              )}
            </div>
          ))}
        </div>
      </div>

      {pages.length > 1 && (
        <div className="home-product-carousel-controls">
          <button
            type="button"
            aria-label="이전 상품 보기"
            disabled={activePage === 0}
            onClick={() => moveToPage(activePage - 1)}
          >
            ←
          </button>
          <div className="home-product-carousel-indicators" aria-label="상품 캐러셀 페이지">
            {pages.map((_, pageIndex) => (
              <button
                key={pageIndex}
                type="button"
                aria-label={`${pageIndex + 1}번째 페이지 보기`}
                aria-current={activePage === pageIndex ? "true" : undefined}
                className={activePage === pageIndex ? "is-active" : undefined}
                onClick={() => moveToPage(pageIndex)}
              />
            ))}
          </div>
          <button
            type="button"
            aria-label="다음 상품 보기"
            disabled={activePage === pages.length - 1}
            onClick={() => moveToPage(activePage + 1)}
          >
            →
          </button>
        </div>
      )}
    </div>
  );
}
