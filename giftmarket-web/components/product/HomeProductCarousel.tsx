"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState, type CSSProperties } from "react";
import ProductCard from "@/components/product/ProductCard";
import type { ProductSummary } from "@/types/product";

interface HomeProductCarouselProps {
  products: ProductSummary[];
  viewAllHref: string;
  resetKey: string | number;
  isLoading?: boolean;
  emptyMessage: string;
}

export default function HomeProductCarousel({ products, viewAllHref, resetKey, isLoading = false, emptyMessage }: HomeProductCarouselProps) {
  const viewportRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef<{ x: number; left: number; moved: boolean } | null>(null);
  const suppressClickRef = useRef(false);
  const [edges, setEdges] = useState({ start: true, end: true });

  const updateEdges = useCallback(() => {
    const el = viewportRef.current;
    if (el) setEdges({ start: el.scrollLeft <= 1, end: el.scrollLeft + el.clientWidth >= el.scrollWidth - 1 });
  }, []);

  useEffect(() => {
    const el = viewportRef.current;
    if (!el) return;
    el.scrollLeft = 0;
    updateEdges();
    const observer = new ResizeObserver(() => {
      updateEdges();
    });
    observer.observe(el);
    if (el.firstElementChild) observer.observe(el.firstElementChild);
    return () => observer.disconnect();
  }, [resetKey, products.length, isLoading, updateEdges]);

  const stepSize = (el: HTMLDivElement) => {
    const track = el.firstElementChild as HTMLElement;
    const item = track.querySelector<HTMLElement>(".home-product-carousel-item");
    return item ? item.getBoundingClientRect().width + (parseFloat(getComputedStyle(track).columnGap) || 0) : 0;
  };
  const scrollTo = (el: HTMLDivElement, left: number) => {
    el.scrollTo({ left, behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
  };
  const move = (direction: number) => {
    const el = viewportRef.current;
    if (!el) return;
    const step = stepSize(el);
    if (step) {
      const count = window.matchMedia("(min-width: 1024px)").matches ? 4 : 3;
      const target = (Math.round(el.scrollLeft / step) + direction * count) * step;
      scrollTo(el, Math.max(0, Math.min(target, el.scrollWidth - el.clientWidth)));
    }
  };

  if (isLoading) return <div className="home-product-placeholder">상품을 불러오는 중입니다.</div>;
  if (products.length === 0) return <div className="home-product-empty">{emptyMessage}</div>;

  const mobileColumns = Math.ceil((products.length + 1) / 2);
  const trackStyle = { "--desktop-count": products.length, "--mobile-count": mobileColumns } as CSSProperties;

  return (
    <div className="home-product-carousel">
      <div ref={viewportRef} className="home-product-carousel-viewport" onScroll={updateEdges}
        onDragStart={(event) => event.preventDefault()}
        onPointerDown={(event) => {
          if (event.pointerType !== "mouse" || event.button !== 0) return;
          suppressClickRef.current = false;
          dragRef.current = { x: event.clientX, left: event.currentTarget.scrollLeft, moved: false };
        }}
        onPointerMove={(event) => {
          const drag = dragRef.current;
          if (!drag) return;
          if (Math.abs(event.clientX - drag.x) > 5) {
            drag.moved = true;
            event.currentTarget.setPointerCapture(event.pointerId);
            event.currentTarget.classList.add("is-dragging");
            event.currentTarget.scrollLeft = drag.left - (event.clientX - drag.x);
          }
        }}
        onPointerUp={(event) => {
          const drag = dragRef.current;
          if (!drag) return;
          dragRef.current = null;
          event.currentTarget.classList.remove("is-dragging");
          if (drag.moved) {
            suppressClickRef.current = true;
            const step = stepSize(event.currentTarget);
            if (step) scrollTo(event.currentTarget, Math.round(event.currentTarget.scrollLeft / step) * step);
          }
        }}
        onPointerCancel={(event) => { dragRef.current = null; event.currentTarget.classList.remove("is-dragging"); }}
        onClickCapture={(event) => {
          if (suppressClickRef.current) {
            event.preventDefault();
            event.stopPropagation();
            suppressClickRef.current = false;
          }
        }}
      >
        <div className="home-product-carousel-track" style={trackStyle}>
          {products.map((product, index) => (
            <div key={product.id} className="home-product-carousel-item" style={{ "--mobile-column": Math.floor(index / 2) + 1, "--mobile-row": index % 2 + 1 } as CSSProperties}>
              <ProductCard product={product} />
            </div>
          ))}
          <Link href={viewAllHref} className="home-product-carousel-item home-product-carousel-view-all" style={{ "--mobile-column": Math.floor(products.length / 2) + 1, "--mobile-row": products.length % 2 + 1 } as CSSProperties}>
            <span aria-hidden="true" className="home-product-carousel-view-all-arrow">→</span>
            <strong>전체 보기</strong>
            <span>더 많은 선물 만나보기</span>
          </Link>
        </div>
      </div>
      <div className="home-product-carousel-controls">
        <button type="button" aria-label="이전 상품 보기" disabled={edges.start} onClick={() => move(-1)}>←</button>
        <button type="button" aria-label="다음 상품 보기" disabled={edges.end} onClick={() => move(1)}>→</button>
      </div>
    </div>
  );
}
