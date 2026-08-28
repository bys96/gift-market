"use client";

import Image from "next/image";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import {
  deleteProductDraft,
  getNewProductDrafts,
} from "@/lib/product-draft-api";
import { getSellerProducts } from "@/lib/product-api";
import { useAuthStore } from "@/stores/auth-store";
import type {
  ProductStatus,
  SellerProductListItem,
  SellerProductPage,
} from "@/types/product";
import type { ProductDraft, ProductDraftData } from "@/types/product-draft";
import { resolveImageUrl } from "@/utils/image-url";
import Pagination from "@/components/common/Pagination";

const PAGE_SIZE = 20;

const PRODUCT_STATUS_OPTIONS: {
  value: ProductStatus | "ALL";
  label: string;
}[] = [
  {
    value: "ALL",
    label: "전체",
  },
  {
    value: "ON_SALE",
    label: "판매 중",
  },
  {
    value: "SOLD_OUT",
    label: "품절",
  },
  {
    value: "DRAFT",
    label: "기존 임시 상태",
  },
  {
    value: "HIDDEN",
    label: "숨김",
  },
];

const PRODUCT_STATUS_LABEL: Record<ProductStatus, string> = {
  DRAFT: "임시 상태",
  ON_SALE: "판매 중",
  SOLD_OUT: "품절",
  HIDDEN: "숨김",
};

function formatPrice(price: number): string {
  return new Intl.NumberFormat("ko-KR").format(price);
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

function parseDraftData(draft: ProductDraft): ProductDraftData | null {
  try {
    return JSON.parse(draft.draftData) as ProductDraftData;
  } catch {
    return null;
  }
}

function getDraftName(draft: ProductDraft): string {
  const data = parseDraftData(draft);

  const name = data?.name?.trim();

  return name ? name : "제목 없는 상품";
}

function getDraftImageKey(draft: ProductDraft): string | null {
  return parseDraftData(draft)?.representativeImageKey ?? null;
}

export default function SellerProductsPage() {
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);

  const user = useAuthStore((state) => state.user);

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [productPage, setProductPage] = useState<SellerProductPage | null>(
    null,
  );

  const [newProductDrafts, setNewProductDrafts] = useState<ProductDraft[]>([]);

  const [selectedStatus, setSelectedStatus] = useState<ProductStatus | "ALL">(
    "ALL",
  );

  const [currentPage, setCurrentPage] = useState(0);

  const [isLoading, setIsLoading] = useState(true);

  const [isLoadingDrafts, setIsLoadingDrafts] = useState(true);

  const [deletingDraftId, setDeletingDraftId] = useState<number | null>(null);

  const [errorMessage, setErrorMessage] = useState("");

  const loadProducts = useCallback(async () => {
    try {
      setIsLoading(true);
      setErrorMessage("");

      const response = await getSellerProducts(
        currentPage,
        PAGE_SIZE,
        selectedStatus === "ALL" ? undefined : selectedStatus,
      );

      setProductPage(response);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "상품 목록을 불러오지 못했습니다.",
      );
    } finally {
      setIsLoading(false);
    }
  }, [currentPage, selectedStatus]);

  const loadDrafts = useCallback(async () => {
    try {
      setIsLoadingDrafts(true);

      const drafts = await getNewProductDrafts();

      setNewProductDrafts(drafts);
    } catch (error) {
      console.error(error);
    } finally {
      setIsLoadingDrafts(false);
    }
  }, []);

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }

    // 인증 완료 후 상품과 임시저장 목록을 최초 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void Promise.all([loadProducts(), loadDrafts()]);
  }, [initialized, isAuthenticated, user, router, loadProducts, loadDrafts]);

  const handleStatusChange = (status: ProductStatus | "ALL") => {
    setSelectedStatus(status);
    setCurrentPage(0);
  };

  const handleContinueDraft = (draftId: number) => {
    router.push(`/seller/products/new?draftId=${draftId}`);
  };

  const handleDeleteDraft = async (draftId: number) => {
    if (deletingDraftId !== null) {
      return;
    }

    const confirmed = window.confirm("임시저장한 상품을 삭제하시겠습니까?");

    if (!confirmed) {
      return;
    }

    try {
      setDeletingDraftId(draftId);

      await deleteProductDraft(draftId);

      setNewProductDrafts((current) =>
        current.filter((draft) => draft.id !== draftId),
      );
    } catch (error) {
      alert(
        error instanceof Error
          ? error.message
          : "임시저장 상품을 삭제하지 못했습니다.",
      );
    } finally {
      setDeletingDraftId(null);
    }
  };

  if (
    !initialized ||
    !isAuthenticated ||
    !user
  ) {
    return (
      <main className="seller-products-page">
        <div className="common-inner">
          <div className="seller-application-loading">
            <span className="seller-application-loading-spinner" />

            <p>상품 정보를 확인하고 있습니다.</p>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="seller-products-page">
      <div className="common-inner">
        <div className="seller-products-container">
          <header className="seller-products-header">
            <div>
              <p className="seller-products-header-label">PRODUCT MANAGEMENT</p>

              <h1 className="seller-products-title">상품 관리</h1>

              <p className="seller-products-description">
                등록 상품과 작성 중인 상품을 관리할 수 있습니다.
              </p>
            </div>

            <button
              type="button"
              className="seller-products-create-button"
              onClick={() => {
                router.push("/seller/products/new");
              }}
            >
              상품 등록
            </button>
          </header>

          {/* ========================================
              작성 중 상품
          ======================================== */}

          <section className="seller-products-panel seller-products-draft-panel">
            <div className="seller-products-draft-header">
              <div>
                <h2>작성 중 상품</h2>

                <p>임시저장한 신규 상품을 이어서 작성할 수 있습니다.</p>
              </div>

              {!isLoadingDrafts && <span>{newProductDrafts.length}개</span>}
            </div>

            {isLoadingDrafts && (
              <div className="seller-products-state seller-products-draft-loading">
                <span className="seller-application-loading-spinner" />

                <p>임시저장 상품을 불러오고 있습니다.</p>
              </div>
            )}

            {!isLoadingDrafts && newProductDrafts.length === 0 && (
              <div className="seller-products-draft-empty">
                임시저장한 신규 상품이 없습니다.
              </div>
            )}

            {!isLoadingDrafts && newProductDrafts.length > 0 && (
              <div className="seller-products-draft-list">
                {newProductDrafts.map((draft) => {
                  const imageUrl = resolveImageUrl(getDraftImageKey(draft));

                  return (
                    <article
                      key={draft.id}
                      className="seller-products-draft-item"
                    >
                      <div className="seller-products-draft-product">
                        <div className="seller-products-draft-image">
                          {imageUrl ? (
                            <Image src={imageUrl} alt="" fill sizes="64px" />
                          ) : (
                            <span>이미지 없음</span>
                          )}
                        </div>

                        <div>
                          <strong>{getDraftName(draft)}</strong>

                          <span>마지막 저장 {formatDate(draft.updatedAt)}</span>
                        </div>
                      </div>

                      <div className="seller-products-draft-actions">
                        <button
                          type="button"
                          className="seller-products-draft-continue-button"
                          disabled={deletingDraftId !== null}
                          onClick={() => handleContinueDraft(draft.id)}
                        >
                          이어쓰기
                        </button>

                        <button
                          type="button"
                          className="seller-products-draft-delete-button"
                          disabled={deletingDraftId !== null}
                          onClick={() => void handleDeleteDraft(draft.id)}
                        >
                          {deletingDraftId === draft.id ? "삭제 중..." : "삭제"}
                        </button>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </section>

          {/* ========================================
              등록 상품
          ======================================== */}

          <section className="seller-products-panel">
            <div className="seller-products-toolbar">
              <div
                className="seller-products-status-tabs"
                role="tablist"
                aria-label="상품 상태 필터"
              >
                {PRODUCT_STATUS_OPTIONS.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    role="tab"
                    aria-selected={selectedStatus === option.value}
                    className={`seller-products-status-tab ${
                      selectedStatus === option.value
                        ? "seller-products-status-tab-active"
                        : ""
                    }`}
                    onClick={() => {
                      handleStatusChange(option.value);
                    }}
                  >
                    {option.label}
                  </button>
                ))}
              </div>

              <p className="seller-products-total-count">
                총 <strong>{productPage?.totalElements ?? 0}</strong>개
              </p>
            </div>

            {isLoading && (
              <div className="seller-products-state">
                <span className="seller-application-loading-spinner" />

                <p>상품 목록을 불러오고 있습니다.</p>
              </div>
            )}

            {!isLoading && errorMessage && (
              <div className="seller-products-state seller-products-state-error">
                <strong>상품 목록을 불러오지 못했습니다.</strong>

                <p>{errorMessage}</p>

                <button type="button" onClick={() => void loadProducts()}>
                  다시 시도
                </button>
              </div>
            )}

            {!isLoading &&
              !errorMessage &&
              productPage?.products.length === 0 && (
                <div className="seller-products-state">
                  <div className="seller-products-empty-icon">+</div>

                  <strong>등록된 상품이 없습니다.</strong>

                  <p>첫 상품을 등록하고 판매를 시작해보세요.</p>

                  <button
                    type="button"
                    onClick={() => {
                      router.push("/seller/products/new");
                    }}
                  >
                    상품 등록하기
                  </button>
                </div>
              )}

            {!isLoading &&
              !errorMessage &&
              productPage &&
              productPage.products.length > 0 && (
                <>
                  <div className="seller-products-table-wrapper">
                    <table className="seller-products-table">
                      <thead>
                        <tr>
                          <th scope="col">상품 정보</th>
                          <th scope="col">판매가</th>
                          <th scope="col">재고</th>
                          <th scope="col">상태</th>
                          <th scope="col">등록일</th>
                          <th scope="col">관리</th>
                        </tr>
                      </thead>

                      <tbody>
                        {productPage.products.map(
                          (product: SellerProductListItem) => {
                            const imageUrl = resolveImageUrl(
                              product.representativeImageKey,
                            );

                            return (
                              <tr key={product.id}>
                                <td>
                                  <div className="seller-products-product">
                                    <div className="seller-products-product-image">
                                      {imageUrl ? (
                                        <Image
                                          src={imageUrl}
                                          alt={`${product.name} 대표 이미지`}
                                          fill
                                          sizes="72px"
                                        />
                                      ) : (
                                        <span>이미지 없음</span>
                                      )}
                                    </div>

                                    <div className="seller-products-product-info">
                                      <span className="seller-products-category">
                                        {product.categoryName}
                                      </span>

                                      <strong>{product.name}</strong>

                                      {product.brandName && (
                                        <span className="seller-products-brand">
                                          {product.brandName}
                                        </span>
                                      )}
                                    </div>
                                  </div>
                                </td>

                                <td>
                                  <strong className="seller-products-price">
                                    {formatPrice(product.price)}원
                                  </strong>
                                </td>

                                <td>
                                  <span className="seller-products-stock">
                                    {product.stockQuantity}개
                                  </span>
                                </td>

                                <td>
                                  <span
                                    className={`seller-products-status seller-products-status-${product.status.toLowerCase()}`}
                                  >
                                    {PRODUCT_STATUS_LABEL[product.status]}
                                  </span>
                                </td>

                                <td>
                                  <span className="seller-products-date">
                                    {formatDate(product.createdAt)}
                                  </span>
                                </td>

                                <td>
                                  <button
                                    type="button"
                                    className="seller-products-manage-button"
                                    onClick={() => {
                                      router.push(
                                        `/seller/products/${product.id}`,
                                      );
                                    }}
                                  >
                                    관리
                                  </button>
                                </td>
                              </tr>
                            );
                          },
                        )}
                      </tbody>
                    </table>
                  </div>

                  <Pagination
                    currentPage={productPage.page}
                    totalPages={productPage.totalPages}
                    ariaLabel="판매자 상품 목록 페이지"
                    disabled={isLoading}
                    onPageChange={setCurrentPage}
                    className="seller-products-pagination"
                  />
                </>
              )}
          </section>
        </div>
      </div>
    </main>
  );
}
