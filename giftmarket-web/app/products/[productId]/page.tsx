"use client";

import Image from "next/image";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

import ProductDetailActions from "@/components/product/ProductDetailActions";
import { getProduct } from "@/lib/product-api";
import type { ProductDetail } from "@/types/product";
import { resolveImageUrl } from "@/utils/image-url";

export default function ProductDetailPage() {
  const params = useParams<{ productId: string }>();

  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [selectedImageUrl, setSelectedImageUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const productId = Number(params.productId);

  useEffect(() => {
    if (!Number.isSafeInteger(productId) || productId <= 0) {
      setErrorMessage("올바르지 않은 상품 번호입니다.");
      setIsLoading(false);
      return;
    }

    const loadProduct = async () => {
      try {
        setIsLoading(true);
        setErrorMessage("");

        const productResponse = await getProduct(productId);

        setProduct(productResponse);

        const representativeImageUrl = resolveImageUrl(
          productResponse.representativeImageKey,
        );

        const firstGalleryImageUrl = productResponse.galleryImageKeys
          .map(resolveImageUrl)
          .find((imageUrl): imageUrl is string => Boolean(imageUrl));

        setSelectedImageUrl(
          representativeImageUrl ?? firstGalleryImageUrl ?? null,
        );
      } catch (error) {
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "상품 정보를 불러오지 못했습니다.",
        );
      } finally {
        setIsLoading(false);
      }
    };

    void loadProduct();
  }, [productId]);

  const productImages = useMemo(() => {
    if (!product) {
      return [];
    }

    const imageUrls = [
      resolveImageUrl(product.representativeImageKey),
      ...product.galleryImageKeys.map(resolveImageUrl),
    ].filter((imageUrl): imageUrl is string => Boolean(imageUrl));

    return [...new Set(imageUrls)];
  }, [product]);

  if (isLoading) {
    return (
      <main className="product-not-found">
        <h1 className="product-not-found-title">
          상품 정보를 불러오는 중입니다.
        </h1>
      </main>
    );
  }

  if (errorMessage || !product) {
    return (
      <main className="product-not-found">
        <h1 className="product-not-found-title">상품을 찾을 수 없습니다.</h1>

        <p className="product-not-found-description">
          {errorMessage || "판매 중인 상품이 아니거나 삭제된 상품입니다."}
        </p>

        <Link href="/products" className="product-not-found-link">
          상품 목록으로 이동
        </Link>
      </main>
    );
  }

  const isSoldOut = product.status === "SOLD_OUT" || product.stockQuantity <= 0;

  const shippingText = product.freeShipping
    ? "무료배송"
    : `${product.shippingFee.toLocaleString("ko-KR")}원`;

  const actionProduct = {
    id: product.id,
    name: product.name,
    brandName: product.brandName ?? "브랜드 미등록",
    price: product.price,
    imageUrl: selectedImageUrl ?? "",
    stockQuantity: product.stockQuantity,
    isFreeShipping: product.freeShipping,
  };

  return (
    <main className="product-detail-page">
      <nav className="product-detail-breadcrumb" aria-label="현재 위치">
        <Link href="/">홈</Link>
        <span aria-hidden="true">/</span>
        <Link href="/products">상품</Link>
        <span aria-hidden="true">/</span>
        <span>{product.categoryName}</span>
      </nav>

      <section className="product-detail">
        <div>
          <div className="product-detail-image-wrapper">
            {selectedImageUrl ? (
              <Image
                src={selectedImageUrl}
                alt={product.name}
                fill
                priority
                sizes="(max-width: 768px) 100vw, 640px"
                className="product-detail-image"
              />
            ) : (
              <div className="product-detail-image-empty">
                등록된 상품 이미지가 없습니다.
              </div>
            )}

            {isSoldOut && (
              <div className="product-detail-sold-out-overlay">품절</div>
            )}
          </div>

          {productImages.length > 1 && (
            <div
              className="product-detail-thumbnail-list"
              aria-label="상품 이미지 목록"
            >
              {productImages.map((imageUrl, index) => {
                const isSelected = selectedImageUrl === imageUrl;

                return (
                  <button
                    key={imageUrl}
                    type="button"
                    className={[
                      "product-detail-thumbnail-button",
                      isSelected
                        ? "product-detail-thumbnail-button-active"
                        : "",
                    ]
                      .filter(Boolean)
                      .join(" ")}
                    aria-label={`${index + 1}번째 상품 이미지 보기`}
                    aria-pressed={isSelected}
                    onClick={() => setSelectedImageUrl(imageUrl)}
                  >
                    <Image
                      src={imageUrl}
                      alt={`${product.name} 이미지 ${index + 1}`}
                      fill
                      sizes="88px"
                      className="product-detail-thumbnail-image"
                    />
                  </button>
                );
              })}
            </div>
          )}
        </div>

        <div className="product-detail-info">
          <Link
            href={`/products?categoryId=${product.categoryId}`}
            className="product-detail-category"
          >
            {product.categoryName}
          </Link>

          <p className="product-detail-brand">
            {product.brandName ?? product.storeName}
          </p>

          <h1 className="product-detail-name">{product.name}</h1>

          {product.summary && (
            <p className="product-detail-description">{product.summary}</p>
          )}

          <strong className="product-detail-price">
            {product.price.toLocaleString("ko-KR")}원
          </strong>

          <dl className="product-detail-meta">
            <div className="product-detail-meta-row">
              <dt>판매자</dt>
              <dd>{product.storeName}</dd>
            </div>

            <div className="product-detail-meta-row">
              <dt>배송비</dt>
              <dd>{shippingText}</dd>
            </div>

            <div className="product-detail-meta-row">
              <dt>판매 상태</dt>
              <dd>
                {isSoldOut ? (
                  <span className="product-detail-sold-out-text">품절</span>
                ) : (
                  "판매 중"
                )}
              </dd>
            </div>
          </dl>

          {isSoldOut ? (
            <div className="product-detail-sold-out-notice">
              현재 품절된 상품입니다.
            </div>
          ) : (
            <ProductDetailActions product={actionProduct} />
          )}
        </div>
      </section>

      <section className="product-detail-content">
        <div className="product-detail-tab-list">
          <a href="#product-description">상품 상세</a>
          <button type="button" disabled>
            리뷰 준비 중
          </button>
          <button type="button" disabled>
            상품 문의 준비 중
          </button>
          <a href="#shipping-information">배송·교환</a>
        </div>

        <article
          id="product-description"
          className="product-detail-description-content"
        >
          <h2 className="product-detail-content-title">상품 상세</h2>

          {product.description ? (
            <div
              className="product-detail-editor-content"
              dangerouslySetInnerHTML={{
                __html: product.description,
              }}
            />
          ) : (
            <div className="product-detail-content-empty">
              등록된 상품 상세 설명이 없습니다.
            </div>
          )}
        </article>

        <section
          id="shipping-information"
          className="product-detail-shipping-information"
        >
          <h2 className="product-detail-content-title">배송·교환 안내</h2>

          <dl>
            <div>
              <dt>배송비</dt>
              <dd>{shippingText}</dd>
            </div>

            <div>
              <dt>배송 안내</dt>
              <dd>결제 완료 후 판매자가 상품을 준비해 배송합니다.</dd>
            </div>

            <div>
              <dt>교환·반품</dt>
              <dd>상품 수령 후 교환·반품 정책에 따라 신청할 수 있습니다.</dd>
            </div>
          </dl>
        </section>
      </section>
    </main>
  );
}
