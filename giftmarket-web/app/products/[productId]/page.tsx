"use client";

import Image from "next/image";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

import ProductDetailActions from "@/components/product/ProductDetailActions";
import ProductImageModal from "@/components/product/ProductImageModal";
import ProductInquirySection from "@/components/product/ProductInquirySection";
import { getProduct } from "@/lib/product-api";
import type { ProductDetail } from "@/types/product";
import { resolveImageUrl } from "@/utils/image-url";

export default function ProductDetailPage() {
  const params = useParams<{ productId: string }>();

  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [selectedImageUrl, setSelectedImageUrl] = useState<string | null>(null);
  const [isImageModalOpen, setIsImageModalOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const productId = Number(params.productId);

  useEffect(() => {
    if (!Number.isSafeInteger(productId) || productId <= 0) {
      // route parameter 검증 결과를 기존 오류 UI에 반영한다.
      // eslint-disable-next-line react-hooks/set-state-in-effect
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
  const isLowStock = !isSoldOut && product.stockQuantity <= 10;

  const shippingText = product.freeShipping
    ? "무료배송"
    : `${product.shippingFee.toLocaleString("ko-KR")}원`;

  const shippingPreparationDays = product.shippingPreparationDays ?? 3;

  const returnShippingFee = product.returnShippingFee ?? 3000;

  const exchangeShippingFee = product.exchangeShippingFee ?? 6000;

  const actionProduct = {
    id: product.id,
    name: product.name,
    brandName: product.brandName ?? "브랜드 미등록",
    price: product.price,
    imageUrl: selectedImageUrl ?? "",
    stockQuantity: product.stockQuantity,
    isFreeShipping: product.freeShipping,

    hasOptions: product.hasOptions ?? false,
    optionGroups: product.optionGroups ?? [],
    variants: product.variants ?? [],
  };

  return (
    <main className="product-detail-page">
      <nav className="product-detail-breadcrumb" aria-label="현재 위치">
        <Link href="/">홈</Link>
        <span aria-hidden="true">/</span>

        <Link href="/products">상품</Link>
        <span aria-hidden="true">/</span>

        <Link href={`/products?categoryId=${product.categoryId}`}>
          {product.categoryName}
        </Link>
      </nav>

      <section className="product-detail">
        <div className="product-detail-gallery">
          <div className="product-detail-image-wrapper">
            {selectedImageUrl ? (
              <button
                type="button"
                className="product-detail-image-button"
                aria-label={`${product.name} 이미지 확대 보기`}
                onClick={() => setIsImageModalOpen(true)}
              >
                <Image
                  src={selectedImageUrl}
                  alt={product.name}
                  fill
                  priority
                  sizes="(max-width: 768px) 100vw, 640px"
                  className="product-detail-image"
                />

                <span className="product-detail-image-zoom" aria-hidden="true">
                  이미지 확대
                </span>
              </button>
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

          <div className="product-detail-price-row">
            <strong className="product-detail-price">
              {product.price.toLocaleString("ko-KR")}
              <span>원</span>
            </strong>

            {isSoldOut ? (
              <span className="product-detail-status-badge product-detail-status-badge-sold-out">
                품절
              </span>
            ) : isLowStock ? (
              <span className="product-detail-status-badge product-detail-status-badge-low-stock">
                재고 {product.stockQuantity.toLocaleString("ko-KR")}개 남음
              </span>
            ) : (
              <span className="product-detail-status-badge">판매 중</span>
            )}
          </div>

          <dl className="product-detail-meta">
            <div className="product-detail-meta-row">
              <dt>판매자</dt>
              <dd>{product.storeName}</dd>
            </div>

            <div className="product-detail-meta-row">
              <dt>배송비</dt>
              <dd>
                <span
                  className={
                    product.freeShipping
                      ? "product-detail-shipping-free"
                      : undefined
                  }
                >
                  {shippingText}
                </span>

                <span className="product-detail-meta-help">
                  결제 완료 후 최대 {shippingPreparationDays}일 이내 출고 예정
                </span>
              </dd>
            </div>

            <div className="product-detail-meta-row">
              <dt>재고</dt>
              <dd>
                {isSoldOut
                  ? "재고 없음"
                  : `${product.stockQuantity.toLocaleString("ko-KR")}개`}
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

          <a href="#seller-information">판매자 정보</a>

          <a href="#product-inquiries">상품 문의</a>

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
          id="seller-information"
          className="product-detail-seller-information"
        >
          <h2 className="product-detail-content-title">판매자 정보</h2>

          <div className="product-detail-seller-card">
            <div className="product-detail-seller-card-header">
              <div className="product-detail-seller-avatar" aria-hidden="true">
                {product.storeName.trim().charAt(0).toUpperCase()}
              </div>

              <div className="product-detail-seller-heading">
                <span className="product-detail-seller-label">판매자</span>

                <strong className="product-detail-seller-name">
                  {product.storeName}
                </strong>
              </div>
            </div>

            {product.sellerIntroduction ? (
              <p className="product-detail-seller-introduction">
                {product.sellerIntroduction}
              </p>
            ) : (
              <p className="product-detail-seller-introduction product-detail-seller-introduction-empty">
                등록된 판매자 소개가 없습니다.
              </p>
            )}

          </div>
        </section>

        <section
          id="shipping-information"
          className="product-detail-shipping-information"
        >
          <h2 className="product-detail-content-title">배송·교환·반품 안내</h2>

          <dl>
            <div>
              <dt>배송비</dt>
              <dd>{shippingText}</dd>
            </div>

            <div>
              <dt>출고 안내</dt>
              <dd>
                결제 완료 후 최대{" "}
                <strong>{product.shippingPreparationDays}일 이내</strong> 출고
                예정입니다.
              </dd>
            </div>

            <div>
              <dt>반품 배송비</dt>
              <dd>
                구매자 귀책 사유로 반품하는 경우{" "}
                <strong>{returnShippingFee.toLocaleString("ko-KR")}원</strong>의
                배송비가 발생합니다.
              </dd>
            </div>

            <div>
              <dt>교환 배송비</dt>
              <dd>
                구매자 귀책 사유로 교환하는 경우 왕복{" "}
                <strong>{exchangeShippingFee.toLocaleString("ko-KR")}원</strong>
                의 배송비가 발생합니다.
              </dd>
            </div>

            <div>
              <dt>교환·반품 신청</dt>
              <dd>
                상품 수령 후 7일 이내 신청할 수 있습니다. 단, 상품 사용 또는
                훼손 등으로 상품 가치가 감소한 경우 교환·반품이 제한될 수
                있습니다.
              </dd>
            </div>

            <div>
              <dt>판매자 귀책</dt>
              <dd>
                오배송, 상품 불량 등 판매자 귀책 사유로 발생한 교환·반품
                배송비는 판매자가 부담합니다.
              </dd>
            </div>
          </dl>
        </section>

        <ProductInquirySection productId={product.id} />
      </section>

      {isImageModalOpen && selectedImageUrl && (
        <ProductImageModal
          imageUrl={selectedImageUrl}
          productName={product.name}
          onClose={() => setIsImageModalOpen(false)}
        />
      )}
    </main>
  );
}
