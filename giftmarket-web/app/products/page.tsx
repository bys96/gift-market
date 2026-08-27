"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
  FormEvent,
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import ProductCard from "@/components/product/ProductCard";
import Pagination from "@/components/common/Pagination";
import { getCategories, getProducts } from "@/lib/product-api";
import type { Category, ProductPage } from "@/types/product";

const DEFAULT_PAGE_SIZE = 20;
const PRODUCT_PAGE_SIZES = [20, 50, 100] as const;

interface ProductsUrlOptions {
  categoryIds?: number[];
  keyword?: string;
  excludeSoldOut?: boolean;
  page?: number;
  size?: number;
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

function parsePageSize(value: string | null) {
  const parsedValue = Number(value);

  return PRODUCT_PAGE_SIZES.includes(
    parsedValue as (typeof PRODUCT_PAGE_SIZES)[number],
  )
    ? parsedValue
    : DEFAULT_PAGE_SIZE;
}

function flattenCategories(categories: Category[]): Category[] {
  return categories.flatMap((category) => [
    category,
    ...flattenCategories(category.children ?? []),
  ]);
}

function getDescendantIds(category: Category) {
  return flattenCategories(category.children ?? []).map((child) => child.id);
}

function isSameNumberArray(first: number[], second: number[]) {
  if (first.length !== second.length) {
    return false;
  }

  const firstSorted = [...first].sort((a, b) => a - b);
  const secondSorted = [...second].sort((a, b) => a - b);

  return firstSorted.every((value, index) => value === secondSorted[index]);
}

function ProductsContent() {
  const router = useRouter();
  const searchParams = useSearchParams();

  /*
   * 연속으로 필터/검색/페이지 요청이 발생했을 때
   * 가장 마지막 요청의 결과만 화면에 반영한다.
   */
  const productRequestIdRef = useRef(0);

  const categoryIdsKey = [
    ...searchParams.getAll("categoryIds"),
    searchParams.get("categoryId"),
  ]
    .filter((value): value is string => value !== null && value !== "")
    .join(",");

  const categoryIds = useMemo(() => {
    const ids = categoryIdsKey
      .split(",")
      .map(Number)
      .filter((id) => Number.isInteger(id) && id > 0);

    return [...new Set(ids)];
  }, [categoryIdsKey]);

  const keyword = searchParams.get("keyword")?.trim() ?? "";

  const excludeSoldOut = searchParams.get("excludeSoldOut") === "true";

  const page = parsePage(searchParams.get("page"));
  const size = parsePageSize(searchParams.get("size"));

  const [categories, setCategories] = useState<Category[]>([]);

  const [activeRootCategoryId, setActiveRootCategoryId] = useState<
    number | null
  >(null);

  /*
   * URL 변경이 반영되기 전에
   * 카테고리 UI를 즉시 변경하기 위한 상태.
   */
  const [optimisticCategoryIds, setOptimisticCategoryIds] = useState<
    number[] | null
  >(null);

  /*
   * 한 번 조회된 상품 목록은
   * 다음 조회가 완료될 때까지 유지한다.
   */
  const [productPage, setProductPage] = useState<ProductPage | null>(null);

  const [searchKeyword, setSearchKeyword] = useState(keyword);

  /*
   * isLoading은 API 요청 상태만 나타낸다.
   *
   * 실제 Loading 화면 노출 여부는
   * productPage 존재 여부와 함께 판단한다.
   */
  const [isLoading, setIsLoading] = useState(true);

  const [isCategoryLoading, setIsCategoryLoading] = useState(true);

  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const displayedCategoryIds = optimisticCategoryIds ?? categoryIds;

  /*
   * 최초 진입 시에만 전체 Loading 화면을 표시한다.
   *
   * 이미 상품 데이터가 있다면 재조회 중이어도
   * 기존 상품 목록을 계속 보여준다.
   */
  const isInitialLoading = isLoading && productPage === null;

  const flatCategories = useMemo(
    () => flattenCategories(categories),
    [categories],
  );

  const activeRootCategory = useMemo(
    () =>
      categories.find((category) => category.id === activeRootCategoryId) ??
      null,
    [activeRootCategoryId, categories],
  );

  const selectedCategories = useMemo(
    () =>
      flatCategories.filter(
        (category) =>
          displayedCategoryIds.includes(category.id) &&
          (category.children?.length ?? 0) === 0,
      ),
    [displayedCategoryIds, flatCategories],
  );

  const filteredRootCategoryIds = useMemo(
    () =>
      categories
        .filter((category) => {
          const descendantIds = getDescendantIds(category);

          return descendantIds.some((id) => displayedCategoryIds.includes(id));
        })
        .map((category) => category.id),
    [categories, displayedCategoryIds],
  );

  const createProductsUrl = useCallback(
    (options: ProductsUrlOptions = {}) => {
      const nextCategoryIds =
        options.categoryIds === undefined ? categoryIds : options.categoryIds;

      const nextKeyword =
        options.keyword === undefined ? keyword : options.keyword.trim();

      const nextExcludeSoldOut =
        options.excludeSoldOut === undefined
          ? excludeSoldOut
          : options.excludeSoldOut;

      const nextPage = options.page === undefined ? page : options.page;
      const nextSize = options.size === undefined ? size : options.size;

      const params = new URLSearchParams();

      [...new Set(nextCategoryIds)]
        .filter((id) => Number.isInteger(id) && id > 0)
        .forEach((id) => {
          params.append("categoryIds", String(id));
        });

      if (nextKeyword) {
        params.set("keyword", nextKeyword);
      }

      if (nextExcludeSoldOut) {
        params.set("excludeSoldOut", "true");
      }

      if (nextPage > 0) {
        params.set("page", String(nextPage));
      }

      if (nextSize !== DEFAULT_PAGE_SIZE) {
        params.set("size", String(nextSize));
      }

      const queryString = params.toString();

      return queryString ? `/products?${queryString}` : "/products";
    },
    [categoryIds, excludeSoldOut, keyword, page, size],
  );

  const loadProducts = useCallback(async () => {
    const requestId = ++productRequestIdRef.current;

    try {
      setIsLoading(true);
      setErrorMessage(null);

      const response = await getProducts({
        categoryIds,
        keyword,
        excludeSoldOut,
        page,
        size,
      });

      /*
       * 이 요청 이후에 새로운 요청이 시작됐다면
       * 오래된 응답이므로 무시한다.
       */
      if (requestId !== productRequestIdRef.current) {
        return;
      }

      /*
       * 존재하지 않는 페이지로 접근한 경우
       * 마지막 페이지 URL로 보정한다.
       *
       * 이때도 기존 상품 목록은 유지한다.
       */
      if (response.totalPages > 0 && page >= response.totalPages) {
        router.replace(
          createProductsUrl({
            page: response.totalPages - 1,
          }),
          {
            scroll: false,
          },
        );

        return;
      }

      /*
       * API 응답이 완료된 시점에만
       * 기존 상품 목록을 새 목록으로 한 번에 교체한다.
       */
      setProductPage(response);
    } catch (error) {
      if (requestId !== productRequestIdRef.current) {
        return;
      }

      console.error(error);

      /*
       * 기존 productPage는 제거하지 않는다.
       *
       * 최초 조회 실패라면 에러 화면이 나오고,
       * 이후 재조회 실패라면 기존 상품 목록을 유지한다.
       */
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "상품 목록을 불러오지 못했습니다.",
      );
    } finally {
      if (requestId === productRequestIdRef.current) {
        setIsLoading(false);
      }
    }
  }, [categoryIds, createProductsUrl, excludeSoldOut, keyword, page, router, size]);

  /*
   * URL이 optimistic 상태를 따라오면
   * 임시 상태를 제거하고 다시 URL을 기준으로 사용한다.
   */
  useEffect(() => {
    if (optimisticCategoryIds === null) {
      return;
    }

    if (isSameNumberArray(optimisticCategoryIds, categoryIds)) {
      // URL이 optimistic 선택을 반영한 시점에 임시 UI 상태를 해제한다.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setOptimisticCategoryIds(null);
    }
  }, [categoryIds, optimisticCategoryIds]);

  useEffect(() => {
    // 뒤로가기 등 URL 검색어 변경을 입력 필드에 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
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
    // query parameter 변경을 상품 API 조회로 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadProducts();
  }, [loadProducts]);

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    router.push(
      createProductsUrl({
        keyword: searchKeyword,
        page: 0,
      }),
      {
        scroll: false,
      },
    );
  };

  const handleExcludeSoldOutChange = () => {
    router.replace(
      createProductsUrl({
        excludeSoldOut: !excludeSoldOut,
        page: 0,
      }),
      {
        scroll: false,
      },
    );
  };

  const handlePageSizeChange = (nextSize: number) => {
    router.replace(
      createProductsUrl({
        page: 0,
        size: nextSize,
      }),
      {
        scroll: false,
      },
    );
  };

  const handleRootCategoryClick = (categoryId: number) => {
    setActiveRootCategoryId((currentId) =>
      currentId === categoryId ? null : categoryId,
    );
  };

  const handleAllCategoryClick = () => {
    setOptimisticCategoryIds([]);
    setActiveRootCategoryId(null);

    router.replace(
      createProductsUrl({
        categoryIds: [],
        page: 0,
      }),
      {
        scroll: false,
      },
    );
  };

  const handleChildCategoryToggle = (categoryId: number) => {
    const isSelected = displayedCategoryIds.includes(categoryId);

    const nextCategoryIds = isSelected
      ? displayedCategoryIds.filter((id) => id !== categoryId)
      : [...displayedCategoryIds, categoryId];

    /*
     * 카테고리 UI는 즉시 반영한다.
     * 상품 목록은 기존 데이터를 유지한다.
     */
    setOptimisticCategoryIds(nextCategoryIds);

    router.replace(
      createProductsUrl({
        categoryIds: nextCategoryIds,
        page: 0,
      }),
      {
        scroll: false,
      },
    );
  };

  const handleClearCategoryFilters = () => {
    setOptimisticCategoryIds([]);
    setActiveRootCategoryId(null);

    router.replace(
      createProductsUrl({
        categoryIds: [],
        page: 0,
      }),
      {
        scroll: false,
      },
    );
  };

  const handleRetry = () => {
    void loadProducts();
  };

  const isAllCategoryActive = displayedCategoryIds.length === 0;

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

      <section className="product-page-category-filter">
        <div className="product-page-category-primary">
          <button
            type="button"
            className={[
              "product-page-category-primary-item",
              isAllCategoryActive
                ? "product-page-category-primary-item-all-active"
                : "",
            ]
              .filter(Boolean)
              .join(" ")}
            onClick={handleAllCategoryClick}
          >
            전체
          </button>

          {isCategoryLoading && (
            <span className="product-page-category-loading">
              카테고리를 불러오는 중입니다.
            </span>
          )}

          {!isCategoryLoading &&
            categories.map((category) => {
              const isActive = activeRootCategoryId === category.id;

              const isFiltered = filteredRootCategoryIds.includes(category.id);

              return (
                <button
                  key={category.id}
                  type="button"
                  className={[
                    "product-page-category-primary-item",
                    isFiltered
                      ? "product-page-category-primary-item-filtered"
                      : "",
                    isActive ? "product-page-category-primary-item-active" : "",
                  ]
                    .filter(Boolean)
                    .join(" ")}
                  onClick={() => handleRootCategoryClick(category.id)}
                >
                  {category.name}
                </button>
              );
            })}
        </div>

        {activeRootCategory && activeRootCategory.children.length > 0 && (
          <div className="product-page-category-secondary">
            {activeRootCategory.children.map((category) => {
              const isSelected = displayedCategoryIds.includes(category.id);

              return (
                <button
                  key={category.id}
                  type="button"
                  className={[
                    "product-page-category-secondary-item",
                    isSelected
                      ? "product-page-category-secondary-item-active"
                      : "",
                  ]
                    .filter(Boolean)
                    .join(" ")}
                  onClick={() => handleChildCategoryToggle(category.id)}
                >
                  {category.name}
                </button>
              );
            })}
          </div>
        )}
      </section>

      <div className="product-page-filter-toolbar">
        <div className="product-page-filter-toolbar-left">
          {selectedCategories.map((category) => (
            <button
              key={category.id}
              type="button"
              className="product-page-selected-filter"
              aria-label={`${category.name} 필터 제거`}
              onClick={() => handleChildCategoryToggle(category.id)}
            >
              <span>{category.name}</span>

              <span
                aria-hidden="true"
                className="product-page-selected-filter-remove"
              >
                ×
              </span>
            </button>
          ))}

          {selectedCategories.length > 0 && (
            <button
              type="button"
              className="product-page-selected-filter-reset"
              onClick={handleClearCategoryFilters}
            >
              전체 해제
            </button>
          )}
        </div>

        <div className="product-page-filter-toolbar-right">
          <label className="product-page-size-select">
            <span>페이지당 상품 수</span>
            <select
              value={size}
              onChange={(event) =>
                handlePageSizeChange(Number(event.target.value))
              }
            >
              {PRODUCT_PAGE_SIZES.map((pageSize) => (
                <option key={pageSize} value={pageSize}>
                  {pageSize}개씩
                </option>
              ))}
            </select>
          </label>

          <label className="product-page-sold-out-filter">
            <input
              type="checkbox"
              checked={excludeSoldOut}
              onChange={handleExcludeSoldOutChange}
            />

            <span>품절 상품 제외</span>
          </label>
        </div>
      </div>

      {isInitialLoading && (
        <div className="product-page-state">
          <p>상품을 불러오는 중입니다.</p>
        </div>
      )}

      {!isLoading && errorMessage && productPage === null && (
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

      {productPage && productPage.products.length === 0 && (
        <div className="product-page-state">
          <p>조건에 맞는 상품이 없습니다.</p>

          <Link
            href={
              size === DEFAULT_PAGE_SIZE
                ? "/products"
                : `/products?size=${size}`
            }
            scroll={false}
            className="product-page-reset-link"
          >
            전체 상품 보기
          </Link>
        </div>
      )}

      {productPage && productPage.products.length > 0 && (
        <>
          <div className="product-list">
            {productPage.products.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>

          <Pagination
            currentPage={productPage.page}
            totalPages={productPage.totalPages}
            ariaLabel="상품 목록 페이지"
            mode="numbers"
            pageWindowSize={5}
            getPageHref={(pageNumber) => createProductsUrl({ page: pageNumber })}
            scroll={false}
            className="product-page-pagination"
          />
        </>
      )}
    </main>
  );
}

function ProductsFallback() {
  return (
    <main className="product-page">
      <header className="product-page-header">
        <div>
          <p className="product-page-eyebrow">PRODUCTS</p>
          <h1 className="product-page-title">선물 전체보기</h1>
        </div>
        <p className="product-page-count">총 <strong>0</strong>개의 상품</p>
      </header>
      <form className="product-page-search-form">
        <label htmlFor="product-search-loading" className="sr-only">상품 검색</label>
        <input id="product-search-loading" type="search" placeholder="상품명으로 검색" className="product-page-search-input" disabled />
        <button type="button" className="product-page-search-button" disabled>검색</button>
      </form>
      <section className="product-page-category-filter">
        <div className="product-page-category-primary">
          <button type="button" className="product-page-category-primary-item product-page-category-primary-item-all-active" disabled>전체</button>
          <span className="product-page-category-loading">카테고리를 불러오는 중입니다.</span>
        </div>
      </section>
      <div className="product-page-filter-toolbar">
        <div className="product-page-filter-toolbar-left" />
        <label className="product-page-sold-out-filter"><input type="checkbox" disabled /><span>품절 상품 제외</span></label>
      </div>
      <div className="product-page-state">
        <p>상품을 불러오는 중입니다.</p>
      </div>
    </main>
  );
}

export default function ProductsPage() {
  return (
    <Suspense fallback={<ProductsFallback />}>
      <ProductsContent />
    </Suspense>
  );
}
