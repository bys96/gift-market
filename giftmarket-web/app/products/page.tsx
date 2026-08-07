"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";

import ProductCard from "@/components/product/ProductCard";
import { getCategories, getProducts } from "@/lib/product-api";
import type { Category, ProductPage } from "@/types/product";

const PAGE_SIZE = 20;
const PAGE_GROUP_SIZE = 5;

interface ProductsUrlOptions {
  categoryId?: number | null;
  keyword?: string;
  excludeSoldOut?: boolean;
  page?: number;
}

function parsePositiveNumber(value: string | null) {
  if (!value) {
    return undefined;
  }

  const parsedValue = Number(value);

  if (!Number.isInteger(parsedValue) || parsedValue <= 0) {
    return undefined;
  }

  return parsedValue;
}

function parsePage(value: string | null) {
  if (!value) {
    return 0;
  }

  const parsedValue = Number(value);

  if (!Number.isInteger(parsedValue) || parsedValue < 0) {
    return 0;
  }

  return parsedValue;
}

function flattenCategories(categories: Category[]): Category[] {
  return categories.flatMap((category) => [
    category,
    ...flattenCategories(category.children ?? []),
  ]);
}

function createPageNumbers(currentPage: number, totalPages: number) {
  if (totalPages <= 0) {
    return [];
  }

  const currentGroup = Math.floor(currentPage / PAGE_GROUP_SIZE);
  const startPage = currentGroup * PAGE_GROUP_SIZE;
  const endPage = Math.min(startPage + PAGE_GROUP_SIZE, totalPages);

  return Array.from(
    { length: endPage - startPage },
    (_, index) => startPage + index,
  );
}

export default function ProductsPage() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const categoryId = parsePositiveNumber(searchParams.get("categoryId"));

  const keyword = searchParams.get("keyword")?.trim() ?? "";

  const excludeSoldOut = searchParams.get("excludeSoldOut") === "true";

  const page = parsePage(searchParams.get("page"));

  const [categories, setCategories] = useState<Category[]>([]);
  const [productPage, setProductPage] = useState<ProductPage | null>(null);

  const [searchKeyword, setSearchKeyword] = useState(keyword);
  const [isLoading, setIsLoading] = useState(true);
  const [isCategoryLoading, setIsCategoryLoading] = useState(true);

  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const flatCategories = useMemo(
    () => flattenCategories(categories),
    [categories],
  );

  const selectedCategory = useMemo(
    () => flatCategories.find((category) => category.id === categoryId),
    [categoryId, flatCategories],
  );

  const pageNumbers = useMemo(
    () =>
      createPageNumbers(productPage?.page ?? 0, productPage?.totalPages ?? 0),
    [productPage],
  );

  const createProductsUrl = useCallback(
    (options: ProductsUrlOptions = {}) => {
      const nextCategoryId =
        options.categoryId === undefined ? categoryId : options.categoryId;

      const nextKeyword =
        options.keyword === undefined ? keyword : options.keyword.trim();

      const nextExcludeSoldOut =
        options.excludeSoldOut === undefined
          ? excludeSoldOut
          : options.excludeSoldOut;

      const nextPage = options.page === undefined ? page : options.page;

      const params = new URLSearchParams();

      if (nextCategoryId !== null && nextCategoryId !== undefined) {
        params.set("categoryId", String(nextCategoryId));
      }

      if (nextKeyword) {
        params.set("keyword", nextKeyword);
      }

      if (nextExcludeSoldOut) {
        params.set("excludeSoldOut", "true");
      }

      if (nextPage > 0) {
        params.set("page", String(nextPage));
      }

      const queryString = params.toString();

      return queryString ? `/products?${queryString}` : "/products";
    },
    [categoryId, excludeSoldOut, keyword, page],
  );

  const loadProducts = useCallback(async () => {
    try {
      setIsLoading(true);
      setErrorMessage(null);

      const response = await getProducts({
        categoryId,
        keyword,
        excludeSoldOut,
        page,
        size: PAGE_SIZE,
      });

      if (response.totalPages > 0 && page >= response.totalPages) {
        router.replace(
          createProductsUrl({
            page: response.totalPages - 1,
          }),
        );

        return;
      }

      setProductPage(response);
    } catch (error) {
      console.error(error);

      setProductPage(null);
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "상품 목록을 불러오지 못했습니다.",
      );
    } finally {
      setIsLoading(false);
    }
  }, [categoryId, createProductsUrl, excludeSoldOut, keyword, page, router]);

  useEffect(() => {
    setSearchKeyword(keyword);
  }, [keyword]);

  useEffect(() => {
    let isMounted = true;

    const loadCategories = async () => {
      try {
        setIsCategoryLoading(true);

        const response = await getCategories();

        if (isMounted) {
          setCategories(response);
        }
      } catch (error) {
        console.error("카테고리 조회 실패:", error);

        if (isMounted) {
          setCategories([]);
        }
      } finally {
        if (isMounted) {
          setIsCategoryLoading(false);
        }
      }
    };

    void loadCategories();

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    void loadProducts();
  }, [loadProducts]);

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    router.push(
      createProductsUrl({
        keyword: searchKeyword,
        page: 0,
      }),
    );
  };

  const handleExcludeSoldOutChange = () => {
    router.push(
      createProductsUrl({
        excludeSoldOut: !excludeSoldOut,
        page: 0,
      }),
      { scroll: false },
    );
  };

  const handleRetry = () => {
    void loadProducts();
  };

  return (
    <main className="product-page">
      <header className="product-page-header">
        <div>
          <p className="product-page-eyebrow">PRODUCTS</p>
          <h1 className="product-page-title">선물 전체보기</h1>
        </div>

        <p className="product-page-count">
          총{" "}
          <strong>
            {productPage?.totalElements.toLocaleString("ko-KR") ?? 0}
          </strong>
          개의 상품
        </p>
      </header>

      <form className="product-page-search-form" onSubmit={handleSearch}>
        <label htmlFor="product-search" className="sr-only">
          상품 검색
        </label>

        <input
          id="product-search"
          type="search"
          value={searchKeyword}
          placeholder="상품명으로 검색"
          className="product-page-search-input"
          onChange={(event) => setSearchKeyword(event.target.value)}
        />

        <button type="submit" className="product-page-search-button">
          검색
        </button>
      </form>

      <div className="product-page-category-list">
        <Link
          href={createProductsUrl({
            categoryId: null,
            page: 0,
          })}
          scroll={false}
          className={[
            "product-page-category",
            categoryId === undefined ? "product-page-category-active" : "",
          ]
            .filter(Boolean)
            .join(" ")}
        >
          전체
        </Link>

        {isCategoryLoading && (
          <span className="product-page-category-loading">
            카테고리를 불러오는 중입니다.
          </span>
        )}

        {!isCategoryLoading &&
          flatCategories.map((category) => (
            <Link
              key={category.id}
              href={createProductsUrl({
                categoryId: category.id,
                page: 0,
              })}
              scroll={false}
              className={[
                "product-page-category",
                category.id === categoryId
                  ? "product-page-category-active"
                  : "",
              ]
                .filter(Boolean)
                .join(" ")}
            >
              {category.name}
            </Link>
          ))}
      </div>

      <div className="product-page-toolbar">
        <p className="product-page-selected-category">
          {selectedCategory ? `${selectedCategory.name} 상품` : "전체 상품"}
        </p>

        <label className="product-page-sold-out-filter">
          <input
            type="checkbox"
            checked={excludeSoldOut}
            onChange={handleExcludeSoldOutChange}
          />

          <span>품절 상품 제외</span>
        </label>
      </div>

      {isLoading && (
        <div className="product-page-state">
          <p>상품을 불러오는 중입니다.</p>
        </div>
      )}

      {!isLoading && errorMessage && (
        <div className="product-page-state">
          <p>{errorMessage}</p>

          <button
            type="button"
            className="product-page-retry-button"
            onClick={handleRetry}
          >
            다시 시도
          </button>
        </div>
      )}

      {!isLoading && !errorMessage && productPage?.products.length === 0 && (
        <div className="product-page-state">
          <p>조건에 맞는 상품이 없습니다.</p>

          <Link href="/products" className="product-page-reset-link">
            전체 상품 보기
          </Link>
        </div>
      )}

      {!isLoading &&
        !errorMessage &&
        productPage &&
        productPage.products.length > 0 && (
          <>
            <div className="product-list">
              {productPage.products.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>

            {productPage.totalPages > 1 && (
              <nav
                className="product-page-pagination"
                aria-label="상품 목록 페이지"
              >
                <Link
                  href={createProductsUrl({
                    page: Math.max(productPage.page - 1, 0),
                  })}
                  className={[
                    "product-page-pagination-button",
                    productPage.first
                      ? "product-page-pagination-button-disabled"
                      : "",
                  ]
                    .filter(Boolean)
                    .join(" ")}
                  aria-disabled={productPage.first}
                  tabIndex={productPage.first ? -1 : undefined}
                  onClick={(event) => {
                    if (productPage.first) {
                      event.preventDefault();
                    }
                  }}
                >
                  이전
                </Link>

                <div className="product-page-pagination-pages">
                  {pageNumbers.map((pageNumber) => (
                    <Link
                      key={pageNumber}
                      href={createProductsUrl({
                        page: pageNumber,
                      })}
                      className={[
                        "product-page-pagination-number",
                        pageNumber === productPage.page
                          ? "product-page-pagination-number-active"
                          : "",
                      ]
                        .filter(Boolean)
                        .join(" ")}
                      aria-current={
                        pageNumber === productPage.page ? "page" : undefined
                      }
                    >
                      {pageNumber + 1}
                    </Link>
                  ))}
                </div>

                <Link
                  href={createProductsUrl({
                    page: Math.min(
                      productPage.page + 1,
                      productPage.totalPages - 1,
                    ),
                  })}
                  className={[
                    "product-page-pagination-button",
                    productPage.last
                      ? "product-page-pagination-button-disabled"
                      : "",
                  ]
                    .filter(Boolean)
                    .join(" ")}
                  aria-disabled={productPage.last}
                  tabIndex={productPage.last ? -1 : undefined}
                  onClick={(event) => {
                    if (productPage.last) {
                      event.preventDefault();
                    }
                  }}
                >
                  다음
                </Link>
              </nav>
            )}
          </>
        )}
    </main>
  );
}
