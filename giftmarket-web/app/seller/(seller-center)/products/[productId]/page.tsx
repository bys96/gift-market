"use client";

import Image from "next/image";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

import {
  deleteSellerProduct,
  getProductOptions,
  getProductVariants,
  getSellerProduct,
  updateProductStatus,
} from "@/lib/product-api";
import { useAuthStore } from "@/stores/auth-store";
import type {
  ProductOptionResponse,
  ProductStatus,
  ProductVariantListResponse,
  SellerProduct,
} from "@/types/product";
import { resolveImageUrl } from "@/utils/image-url";

const PRODUCT_STATUS_LABEL: Record<ProductStatus, string> = {
  DRAFT: "임시 상태",
  ON_SALE: "판매 중",
  SOLD_OUT: "품절",
  HIDDEN: "판매 중단",
};

function formatPrice(value: number): string {
  return new Intl.NumberFormat("ko-KR").format(value);
}

function formatDate(dateTime: string): string {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(dateTime));
}

export default function SellerProductDetailPage() {
  const params = useParams<{ productId: string }>();
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [product, setProduct] = useState<SellerProduct | null>(null);
  const [optionResponse, setOptionResponse] =
    useState<ProductOptionResponse | null>(null);
  const [variantResponse, setVariantResponse] =
    useState<ProductVariantListResponse | null>(null);

  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [statusErrorMessage, setStatusErrorMessage] = useState("");

  const productId = Number(params.productId);

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }

    if (user.role !== "SELLER" && user.role !== "ADMIN") {
      router.replace("/seller");
    }
  }, [initialized, isAuthenticated, user, router]);

  useEffect(() => {
    if (
      !initialized ||
      !isAuthenticated ||
      !user ||
      (user.role !== "SELLER" && user.role !== "ADMIN")
    ) {
      return;
    }

    if (!Number.isSafeInteger(productId) || productId <= 0) {
      // route parameter 검증 결과를 기존 오류 UI에 반영한다.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setErrorMessage("올바르지 않은 상품 번호입니다.");
      setIsLoading(false);
      return;
    }

    let cancelled = false;

    const loadProduct = async () => {
      try {
        setIsLoading(true);
        setErrorMessage("");

        const [productResult, optionResult, variantResult] = await Promise.all([
          getSellerProduct(productId),
          getProductOptions(productId),
          getProductVariants(productId),
        ]);

        if (cancelled) {
          return;
        }

        setProduct(productResult);
        setOptionResponse(optionResult);
        setVariantResponse(variantResult);
      } catch (error) {
        if (cancelled) {
          return;
        }

        setErrorMessage(
          error instanceof Error
            ? error.message
            : "상품 정보를 불러오지 못했습니다.",
        );
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    void loadProduct();

    return () => {
      cancelled = true;
    };
  }, [initialized, isAuthenticated, user, productId]);

  const handleToggleSaleStatus = async () => {
    if (!product || isUpdatingStatus) {
      return;
    }

    const isHidden = product.status === "HIDDEN";

    if (
      product.status !== "ON_SALE" &&
      product.status !== "SOLD_OUT" &&
      product.status !== "HIDDEN"
    ) {
      setStatusErrorMessage(
        "현재 상품 상태에서는 판매 상태를 변경할 수 없습니다.",
      );
      return;
    }

    if (!isHidden) {
      const confirmed = window.confirm(
        "상품 판매를 중단하시겠습니까?\n\n판매중단 후에는 구매자 상품 목록과 상품 상세에서 판매되지 않습니다.",
      );

      if (!confirmed) {
        return;
      }
    }

    try {
      setIsUpdatingStatus(true);
      setStatusErrorMessage("");

      const updatedProduct = await updateProductStatus(product.id, {
        status: isHidden ? "ON_SALE" : "HIDDEN",
      });

      setProduct(updatedProduct);
    } catch (error) {
      setStatusErrorMessage(
        error instanceof Error
          ? error.message
          : "상품 판매 상태를 변경하지 못했습니다.",
      );
    } finally {
      setIsUpdatingStatus(false);
    }
  };

  const handleDeleteProduct = async () => {
    if (!product || isDeleting || isUpdatingStatus) {
      return;
    }

    const confirmed = window.confirm(
      "이 상품을 삭제하시겠습니까?\n\n삭제한 상품은 구매자와 판매자 상품 목록에서 더 이상 노출되지 않습니다.",
    );

    if (!confirmed) {
      return;
    }

    try {
      setIsDeleting(true);
      setStatusErrorMessage("");

      await deleteSellerProduct(product.id);

      router.replace("/seller/products");
      router.refresh();
    } catch (error) {
      setStatusErrorMessage(
        error instanceof Error ? error.message : "상품을 삭제하지 못했습니다.",
      );
    } finally {
      setIsDeleting(false);
    }
  };

  const representativeImageUrl = useMemo(
    () => resolveImageUrl(product?.representativeImageKey),
    [product?.representativeImageKey],
  );

  const galleryImages = useMemo(
    () =>
      (product?.galleryImageKeys ?? []).flatMap((imageKey) => {
        const imageUrl = resolveImageUrl(imageKey);

        return imageUrl ? [imageUrl] : [];
      }),
    [product?.galleryImageKeys],
  );

  const hasOptions = (optionResponse?.optionGroups.length ?? 0) > 0;
  const variants = variantResponse?.variants ?? [];

  if (
    !initialized ||
    !isAuthenticated ||
    !user ||
    (user.role !== "SELLER" && user.role !== "ADMIN")
  ) {
    return (
      <main className="seller-product-detail-page">
        <div className="common-inner">
          <div className="seller-application-loading">
            <span className="seller-application-loading-spinner" />
            <p>판매자 정보를 확인하고 있습니다.</p>
          </div>
        </div>
      </main>
    );
  }

  if (isLoading) {
    return (
      <main className="seller-product-detail-page">
        <div className="common-inner">
          <div className="seller-application-loading">
            <span className="seller-application-loading-spinner" />
            <p>상품 정보를 불러오고 있습니다.</p>
          </div>
        </div>
      </main>
    );
  }

  if (errorMessage || !product) {
    return (
      <main className="seller-product-detail-page">
        <div className="common-inner">
          <div className="seller-product-detail-container">
            <div className="seller-product-form-error" role="alert">
              {errorMessage || "상품 정보를 확인할 수 없습니다."}
            </div>

            <button
              type="button"
              className="seller-product-form-back-button"
              onClick={() => router.push("/seller/products")}
            >
              ← 상품 관리로 돌아가기
            </button>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="seller-product-detail-page">
      <div className="common-inner">
        <div className="seller-product-detail-container">
          <header className="seller-product-detail-header">
            <div>
              <button
                type="button"
                className="seller-product-form-back-button"
                onClick={() => router.push("/seller/products")}
              >
                ← 상품 관리
              </button>

              <p className="seller-product-detail-header-label">
                PRODUCT INFORMATION
              </p>

              <div className="seller-product-detail-title-row">
                <h1 className="seller-product-detail-title">상품 정보</h1>

                <span
                  className={`seller-products-status seller-products-status-${product.status.toLowerCase()}`}
                >
                  {PRODUCT_STATUS_LABEL[product.status]}
                </span>
              </div>

              <p className="seller-product-detail-description">
                현재 구매자에게 적용되는 실제 상품 정보를 확인합니다.
              </p>
            </div>
          </header>

          {/* ========================================
              기본 정보
          ======================================== */}

          <section className="seller-product-detail-section">
            <header className="seller-product-detail-section-header">
              <div>
                <span className="seller-product-detail-section-number">01</span>
                <h2>기본 정보</h2>
              </div>

              <p>현재 저장되어 있는 상품 기본 정보입니다.</p>
            </header>

            <div className="seller-product-detail-info-grid">
              <div className="seller-product-detail-info-item">
                <span>상품 번호</span>
                <strong>{product.id}</strong>
              </div>

              <div className="seller-product-detail-info-item">
                <span>판매 상태</span>
                <strong>{PRODUCT_STATUS_LABEL[product.status]}</strong>
              </div>

              <div className="seller-product-detail-info-item">
                <span>카테고리</span>
                <strong>{product.categoryName}</strong>
              </div>

              <div className="seller-product-detail-info-item">
                <span>브랜드</span>
                <strong>{product.brandName || "-"}</strong>
              </div>

              <div className="seller-product-detail-info-item seller-product-detail-info-item-full">
                <span>상품명</span>
                <strong>{product.name}</strong>
              </div>

              <div className="seller-product-detail-info-item">
                <span>판매가</span>
                <strong>{formatPrice(product.price)}원</strong>
              </div>

              <div className="seller-product-detail-info-item">
                <span>총 재고</span>
                <strong>{formatPrice(product.stockQuantity)}개</strong>
              </div>

              <div className="seller-product-detail-info-item">
                <span>등록일</span>
                <strong>{formatDate(product.createdAt)}</strong>
              </div>

              <div className="seller-product-detail-info-item">
                <span>최근 수정일</span>
                <strong>{formatDate(product.updatedAt)}</strong>
              </div>
            </div>

            <div className="seller-product-detail-text-block">
              <span>상품 요약</span>

              <p>{product.summary || "등록된 상품 요약이 없습니다."}</p>
            </div>
          </section>

          {/* ========================================
              상품 이미지
          ======================================== */}

          <section className="seller-product-detail-section">
            <header className="seller-product-detail-section-header">
              <div>
                <span className="seller-product-detail-section-number">02</span>
                <h2>상품 이미지</h2>
              </div>

              <p>대표 이미지와 추가 상품 이미지를 확인합니다.</p>
            </header>

            <div className="seller-product-detail-image-layout">
              <div className="seller-product-detail-representative">
                <span className="seller-product-detail-field-label">
                  대표 이미지
                </span>

                <div className="seller-product-detail-representative-image">
                  {representativeImageUrl ? (
                    <Image
                      src={representativeImageUrl}
                      alt={`${product.name} 대표 이미지`}
                      fill
                      sizes="(max-width: 768px) 100vw, 360px"
                      priority
                    />
                  ) : (
                    <span>등록된 대표 이미지가 없습니다.</span>
                  )}
                </div>
              </div>

              <div className="seller-product-detail-gallery">
                <span className="seller-product-detail-field-label">
                  추가 이미지
                </span>

                {galleryImages.length > 0 ? (
                  <div className="seller-product-detail-gallery-grid">
                    {galleryImages.map((imageUrl, index) => (
                      <div
                        key={`${imageUrl}-${index}`}
                        className="seller-product-detail-gallery-image"
                      >
                        <Image
                          src={imageUrl}
                          alt={`${product.name} 추가 이미지 ${index + 1}`}
                          fill
                          sizes="160px"
                        />
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="seller-product-detail-empty-box">
                    등록된 추가 이미지가 없습니다.
                  </div>
                )}
              </div>
            </div>
          </section>

          {/* ========================================
              상세 설명
          ======================================== */}

          <section className="seller-product-detail-section">
            <header className="seller-product-detail-section-header">
              <div>
                <span className="seller-product-detail-section-number">03</span>
                <h2>상품 상세 설명</h2>
              </div>

              <p>구매자 상품 상세 화면에 표시되는 내용입니다.</p>
            </header>

            {product.description?.trim() ? (
              <div
                className="seller-product-detail-content product-content"
                dangerouslySetInnerHTML={{
                  __html: product.description,
                }}
              />
            ) : (
              <div className="seller-product-detail-empty-box">
                등록된 상품 상세 설명이 없습니다.
              </div>
            )}
          </section>

          {/* ========================================
              옵션 / SKU
          ======================================== */}

          <section className="seller-product-detail-section">
            <header className="seller-product-detail-section-header">
              <div>
                <span className="seller-product-detail-section-number">04</span>
                <h2>상품 옵션 · SKU</h2>
              </div>

              <p>
                옵션 상품은 SKU별 추가금, 재고와 판매 여부를 확인할 수 있습니다.
              </p>
            </header>

            {!hasOptions ? (
              <div className="seller-product-detail-empty-box">
                옵션이 없는 일반 상품입니다. 재고는 상품 단위로 관리됩니다.
              </div>
            ) : (
              <>
                <div className="seller-product-detail-option-groups">
                  {optionResponse?.optionGroups.map((group) => (
                    <div
                      key={group.id}
                      className="seller-product-detail-option-group"
                    >
                      <strong>{group.name}</strong>

                      <div className="seller-product-detail-option-values">
                        {group.values.map((value) => (
                          <span key={value.id}>{value.value}</span>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>

                <div className="seller-product-detail-variant-table-wrapper">
                  <table className="seller-product-detail-variant-table">
                    <thead>
                      <tr>
                        <th scope="col">옵션 조합</th>
                        <th scope="col">SKU</th>
                        <th scope="col">추가금</th>
                        <th scope="col">재고</th>
                        <th scope="col">판매 여부</th>
                      </tr>
                    </thead>

                    <tbody>
                      {variants.map((variant) => (
                        <tr key={variant.id}>
                          <td>
                            <div className="seller-product-detail-variant-options">
                              {variant.optionValues.map((optionValue) => (
                                <span key={optionValue.optionValueId}>
                                  <small>{optionValue.optionGroupName}</small>
                                  {optionValue.optionValue}
                                </span>
                              ))}
                            </div>
                          </td>

                          <td>
                            <span className="seller-product-detail-sku">
                              {variant.skuCode || "-"}
                            </span>
                          </td>

                          <td>
                            {variant.additionalPrice > 0
                              ? `+${formatPrice(variant.additionalPrice)}원`
                              : variant.additionalPrice < 0
                                ? `${formatPrice(variant.additionalPrice)}원`
                                : "0원"}
                          </td>

                          <td>{formatPrice(variant.stockQuantity)}개</td>

                          <td>
                            <span
                              className={[
                                "seller-product-detail-variant-status",
                                variant.active
                                  ? "seller-product-detail-variant-status-active"
                                  : "seller-product-detail-variant-status-inactive",
                              ].join(" ")}
                            >
                              {variant.active ? "판매" : "판매 중지"}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </section>

          {/* ========================================
              배송 / 반품
          ======================================== */}

          <section className="seller-product-detail-section">
            <header className="seller-product-detail-section-header">
              <div>
                <span className="seller-product-detail-section-number">05</span>
                <h2>배송 · 반품 정보</h2>
              </div>

              <p>현재 적용 중인 배송 및 교환/반품 정책입니다.</p>
            </header>

            <div className="seller-product-detail-info-grid">
              <div className="seller-product-detail-info-item">
                <span>배송 방식</span>
                <strong>
                  {product.freeShipping ? "무료배송" : "유료배송"}
                </strong>
              </div>

              <div className="seller-product-detail-info-item">
                <span>배송비</span>
                <strong>
                  {product.freeShipping
                    ? "무료"
                    : `${formatPrice(product.shippingFee)}원`}
                </strong>
              </div>

              <div className="seller-product-detail-info-item">
                <span>출고 소요일</span>
                <strong>{product.shippingPreparationDays}일</strong>
              </div>

              <div className="seller-product-detail-info-item">
                <span>반품 배송비</span>
                <strong>{formatPrice(product.returnShippingFee)}원</strong>
              </div>

              <div className="seller-product-detail-info-item">
                <span>교환 배송비</span>
                <strong>{formatPrice(product.exchangeShippingFee)}원</strong>
              </div>
            </div>
          </section>

          {/* ========================================
              Actions
          ======================================== */}

          {statusErrorMessage && (
            <div className="seller-product-detail-status-error" role="alert">
              {statusErrorMessage}
            </div>
          )}

          <div className="seller-product-detail-actions">
            <button
              type="button"
              className="seller-product-detail-list-button"
              onClick={() => router.push("/seller/products")}
              disabled={isUpdatingStatus || isDeleting}
            >
              목록
            </button>

            <button
              type="button"
              className="seller-product-detail-edit-button"
              onClick={() => router.push(`/seller/products/${product.id}/edit`)}
              disabled={isUpdatingStatus || isDeleting}
            >
              수정
            </button>

            {product.status !== "DRAFT" && (
              <button
                type="button"
                className={[
                  "seller-product-detail-status-button",
                  product.status === "HIDDEN"
                    ? "seller-product-detail-status-button-start"
                    : "seller-product-detail-status-button-stop",
                ].join(" ")}
                onClick={() => void handleToggleSaleStatus()}
                disabled={isUpdatingStatus || isDeleting}
              >
                {isUpdatingStatus
                  ? "처리 중..."
                  : product.status === "HIDDEN"
                    ? "판매시작"
                    : "판매중단"}
              </button>
            )}

            <button
              type="button"
              className="seller-product-detail-delete-button"
              onClick={() => void handleDeleteProduct()}
              disabled={isDeleting || isUpdatingStatus}
            >
              {isDeleting ? "삭제 중..." : "삭제"}
            </button>
          </div>
        </div>
      </div>
    </main>
  );
}
