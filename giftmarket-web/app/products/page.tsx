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
import type { Category, ProductPage, ProductSort } from "@/types/product";

const DEFAULT_PAGE_SIZE = 20;
const PRODUCT_PAGE_SIZES = [20, 40, 60] as const;
const PRODUCT_SORT_OPTIONS: Array<{ value: ProductSort; label: string }> = [
  { value: "LATEST", label: "최신순" },
  { value: "POPULAR", label: "인기순" },
  { value: "PRICE_ASC", label: "낮은 가격순" },
  { value: "PRICE_DESC", label: "높은 가격순" },
];
const QUICK_PRICE_RANGES = [
  { label: "전체", minPrice: null, maxPrice: null },
  { label: "1만원 이하", minPrice: 0, maxPrice: 10_000 },
  { label: "1~3만원", minPrice: 10_001, maxPrice: 30_000 },
  { label: "3~5만원", minPrice: 30_001, maxPrice: 50_000 },
  { label: "5만원 이상", minPrice: 50_001, maxPrice: null },
] as const;
interface ProductListScrollPosition {
  scrollY: number;
  productId: number;
}

const productListScrollPositions = new Map<string, ProductListScrollPosition>();

interface ProductsUrlOptions {
  categoryIds?: number[];
  giftOccasionId?: number | null;
  keyword?: string;
  excludeSoldOut?: boolean;
  minPrice?: number | null;
  maxPrice?: number | null;
  sort?: ProductSort;
  page?: number;
  size?: number;
}

interface ProductQuerySnapshot {
  categoryIdsKey: string;
  giftOccasionId: number | null;
  keyword: string;
  excludeSoldOut: boolean;
  minPrice: number | null;
  maxPrice: number | null;
  sort: ProductSort;
  page: number;
  size: number;
}

function isSameQueryExceptPage(
  first: ProductQuerySnapshot,
  second: ProductQuerySnapshot,
) {
  return (
    first.categoryIdsKey === second.categoryIdsKey &&
    first.giftOccasionId === second.giftOccasionId &&
    first.keyword === second.keyword &&
    first.excludeSoldOut === second.excludeSoldOut &&
    first.minPrice === second.minPrice &&
    first.maxPrice === second.maxPrice &&
    first.sort === second.sort &&
    first.size === second.size
  );
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

function parsePositiveInteger(value: string | null) {
  const parsedValue = Number(value);
  return Number.isSafeInteger(parsedValue) && parsedValue > 0
    ? parsedValue
    : null;
}

function parseNonNegativeInteger(value: string | null) {
  if (value === null || value.trim() === "") {
    return null;
  }

  const parsedValue = Number(value);
  return Number.isSafeInteger(parsedValue) && parsedValue >= 0
    ? parsedValue
    : null;
}

function parseSort(value: string | null): ProductSort {
  return PRODUCT_SORT_OPTIONS.some((option) => option.value === value)
    ? (value as ProductSort)
    : "LATEST";
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
  const displayedProductQueryRef = useRef<ProductQuerySnapshot | null>(null);
  const productResultsStartRef = useRef<HTMLDivElement>(null);
  const pendingScrollRestoreRef = useRef<ProductListScrollPosition | null>(null);

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
  const giftOccasionId = parsePositiveInteger(searchParams.get("giftOccasionId"));
  const minPrice = parseNonNegativeInteger(searchParams.get("minPrice"));
  const maxPrice = parseNonNegativeInteger(searchParams.get("maxPrice"));
  const sort = parseSort(searchParams.get("sort"));

  const page = parsePage(searchParams.get("page"));
  const size = parsePageSize(searchParams.get("size"));

  const productListLocationKey = useMemo(() => {
    const queryString = searchParams.toString();
    return queryString ? `/products?${queryString}` : "/products";
  }, [searchParams]);

  const [categories, setCategories] = useState<Category[]>([]);
  const [isPriceFilterOpen, setIsPriceFilterOpen] = useState(false);
  const [draftMinPrice, setDraftMinPrice] = useState("");
  const [draftMaxPrice, setDraftMaxPrice] = useState("");
  const [draftExcludeSoldOut, setDraftExcludeSoldOut] = useState(false);

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

      const nextGiftOccasionId =
        options.giftOccasionId === undefined
          ? giftOccasionId
          : options.giftOccasionId;

      const nextExcludeSoldOut =
        options.excludeSoldOut === undefined
          ? excludeSoldOut
          : options.excludeSoldOut;

      const nextMinPrice =
        options.minPrice === undefined ? minPrice : options.minPrice;
      const nextMaxPrice =
        options.maxPrice === undefined ? maxPrice : options.maxPrice;
      const nextSort = options.sort === undefined ? sort : options.sort;

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

      if (nextGiftOccasionId !== null) {
        params.set("giftOccasionId", String(nextGiftOccasionId));
      }

      if (nextExcludeSoldOut) {
        params.set("excludeSoldOut", "true");
      }

      if (nextMinPrice !== null) {
        params.set("minPrice", String(nextMinPrice));
      }

      if (nextMaxPrice !== null) {
        params.set("maxPrice", String(nextMaxPrice));
      }
      if (nextSort !== "LATEST") params.set("sort", nextSort);

      if (nextPage > 0) {
        params.set("page", String(nextPage));
      }

      if (nextSize !== DEFAULT_PAGE_SIZE) {
        params.set("size", String(nextSize));
      }

      const queryString = params.toString();

      return queryString ? `/products?${queryString}` : "/products";
    },
    [
      categoryIds,
      excludeSoldOut,
      giftOccasionId,
      keyword,
      maxPrice,
      minPrice,
      sort,
      page,
      size,
    ],
  );

  const loadProducts = useCallback(async () => {
    const requestId = ++productRequestIdRef.current;
    const requestedQuery: ProductQuerySnapshot = {
      categoryIdsKey: [...categoryIds].sort((a, b) => a - b).join(","),
      giftOccasionId,
      keyword,
      excludeSoldOut,
      minPrice,
      maxPrice,
      sort,
      page,
      size,
    };

    try {
      setIsLoading(true);
      setErrorMessage(null);

      const response = await getProducts({
        categoryIds,
        keyword,
        excludeSoldOut,
        giftOccasionId: giftOccasionId ?? undefined,
        minPrice: minPrice ?? undefined,
        maxPrice: maxPrice ?? undefined,
        sort,
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
      const displayedQuery = displayedProductQueryRef.current;
      const shouldScrollToResults =
        displayedQuery !== null &&
        displayedQuery.page !== requestedQuery.page &&
        isSameQueryExceptPage(displayedQuery, requestedQuery);

      setProductPage(response);
      displayedProductQueryRef.current = requestedQuery;

      if (shouldScrollToResults) {
        window.requestAnimationFrame(() => {
          if (
            requestId === productRequestIdRef.current &&
            displayedProductQueryRef.current === requestedQuery
          ) {
            productResultsStartRef.current?.scrollIntoView({
              behavior: "smooth",
              block: "start",
            });
          }
        });
      }
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
  }, [
    categoryIds,
    createProductsUrl,
    excludeSoldOut,
    giftOccasionId,
    keyword,
    maxPrice,
    minPrice,
    page,
    router,
    size,
    sort,
  ]);

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
    pendingScrollRestoreRef.current =
      productListScrollPositions.get(productListLocationKey) ?? null;
  }, [productListLocationKey]);

  useEffect(() => {
    // query parameter 변경을 상품 API 조회로 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadProducts();
  }, [loadProducts]);

  useEffect(() => {
    if (!isPriceFilterOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setIsPriceFilterOpen(false);
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [isPriceFilterOpen]);

  useEffect(() => {
    const scrollPosition = pendingScrollRestoreRef.current;
    if (!productPage || !scrollPosition) return;

    pendingScrollRestoreRef.current = null;

    const restoreFrame = window.requestAnimationFrame(() => {
      const maximumScrollY = Math.max(
        0,
        document.documentElement.scrollHeight - window.innerHeight,
      );
      const restoredScrollY = Math.min(scrollPosition.scrollY, maximumScrollY);

      window.scrollTo({ top: restoredScrollY, behavior: "auto" });

      if (restoredScrollY < scrollPosition.scrollY) {
        document
          .querySelector<HTMLElement>(
            `[data-product-id="${scrollPosition.productId}"]`,
          )
          ?.scrollIntoView({ block: "center" });
      }
    });

    return () => window.cancelAnimationFrame(restoreFrame);
  }, [productListLocationKey, productPage]);

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

  const openPriceFilter = () => {
    setDraftMinPrice(minPrice === null ? "" : String(minPrice));
    setDraftMaxPrice(maxPrice === null ? "" : String(maxPrice));
    setDraftExcludeSoldOut(excludeSoldOut);
    setIsPriceFilterOpen(true);
  };

  const closePriceFilter = () => setIsPriceFilterOpen(false);
  const draftMinValue = draftMinPrice === "" ? null : Number(draftMinPrice);
  const draftMaxValue = draftMaxPrice === "" ? null : Number(draftMaxPrice);
  const selectedQuickPriceRange = QUICK_PRICE_RANGES.find(
    (range) =>
      range.minPrice === draftMinValue && range.maxPrice === draftMaxValue,
  )?.label;
  const isPriceRangeInvalid =
    (draftMinValue !== null && (!Number.isSafeInteger(draftMinValue) || draftMinValue < 0)) ||
    (draftMaxValue !== null && (!Number.isSafeInteger(draftMaxValue) || draftMaxValue < 0)) ||
    (draftMinValue !== null && draftMaxValue !== null && draftMinValue > draftMaxValue);

  const applyPriceFilter = () => {
    if (isPriceRangeInvalid) return;
    router.replace(createProductsUrl({ minPrice: draftMinValue, maxPrice: draftMaxValue, excludeSoldOut: draftExcludeSoldOut, page: 0 }), { scroll: false });
    closePriceFilter();
  };

  const resetPriceFilter = () => {
    setDraftMinPrice("");
    setDraftMaxPrice("");
  };

  const handleQuickPriceRangeSelect = (
    range: (typeof QUICK_PRICE_RANGES)[number],
  ) => {
    setDraftMinPrice(range.minPrice === null ? "" : String(range.minPrice));
    setDraftMaxPrice(range.maxPrice === null ? "" : String(range.maxPrice));
  };

  const handleSortChange = (nextSort: ProductSort) => {
    router.replace(createProductsUrl({ sort: nextSort, page: 0 }), { scroll: false });
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

  const handleProductCardNavigation = (
    event: React.MouseEvent<HTMLDivElement>,
  ) => {
    if (
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey ||
      !(event.target instanceof Element)
    ) {
      return;
    }

    const productLink = event.target.closest<HTMLAnchorElement>(
      ".product-card-image-link, .product-card-content",
    );
    const productId = Number(productLink?.getAttribute("href")?.split("/").at(-1));

    if (!Number.isSafeInteger(productId) || productId <= 0) return;

    productListScrollPositions.set(productListLocationKey, {
      scrollY: window.scrollY,
      productId,
    });
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
          {(minPrice !== null || maxPrice !== null) && (
            <button type="button" className="product-page-selected-filter" onClick={() => router.replace(createProductsUrl({ minPrice: null, maxPrice: null, page: 0 }), { scroll: false })}>
              <span>{minPrice !== null && maxPrice !== null ? `${minPrice.toLocaleString("ko-KR")}원 ~ ${maxPrice.toLocaleString("ko-KR")}원` : minPrice !== null ? `${minPrice.toLocaleString("ko-KR")}원 이상` : `${maxPrice?.toLocaleString("ko-KR")}원 이하`}</span><span aria-hidden="true" className="product-page-selected-filter-remove">×</span>
            </button>
          )}
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
          <button type="button" className="product-page-filter-button" onClick={openPriceFilter}>⚙ 필터</button>
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
          <label className="product-page-sort-select">
            <span>정렬</span>
            <select value={sort} onChange={(event) => handleSortChange(event.target.value as ProductSort)}>
              {PRODUCT_SORT_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </label>

        </div>
      </div>

      {isPriceFilterOpen && (
        <div className="product-filter-modal-backdrop" role="presentation" onMouseDown={closePriceFilter}>
          <section className="product-filter-modal" role="dialog" aria-modal="true" aria-labelledby="price-filter-title" onMouseDown={(event) => event.stopPropagation()}>
            <header><h2 id="price-filter-title">가격 범위</h2><button type="button" aria-label="필터 닫기" onClick={closePriceFilter}>×</button></header>
            <div className="product-filter-modal-body">
              <div className="product-filter-quick-ranges" aria-label="빠른 가격대 선택">
                {QUICK_PRICE_RANGES.map((range) => (
                  <button
                    key={range.label}
                    type="button"
                    className={selectedQuickPriceRange === range.label ? "is-selected" : ""}
                    aria-pressed={selectedQuickPriceRange === range.label}
                    onClick={() => handleQuickPriceRangeSelect(range)}
                  >
                    {range.label}
                  </button>
                ))}
              </div>
              <div className="product-filter-price-inputs">
                <label>최소 금액<input inputMode="numeric" value={draftMinPrice} onChange={(event) => setDraftMinPrice(event.target.value.replace(/[^0-9]/g, ""))} placeholder="제한 없음" /></label>
                <span>~</span>
                <label>최대 금액<input inputMode="numeric" value={draftMaxPrice} onChange={(event) => setDraftMaxPrice(event.target.value.replace(/[^0-9]/g, ""))} placeholder="제한 없음" /></label>
              </div>
              {isPriceRangeInvalid && <p className="product-filter-error">최소 금액은 최대 금액보다 클 수 없습니다.</p>}
              <label className="product-filter-sold-out">
                <input type="checkbox" checked={draftExcludeSoldOut} onChange={(event) => setDraftExcludeSoldOut(event.target.checked)} />
                <span>품절 상품 제외</span>
              </label>
            </div>
            <footer><button type="button" onClick={resetPriceFilter}>초기화</button><button type="button" disabled={isPriceRangeInvalid} onClick={applyPriceFilter}>상품 보기</button></footer>
          </section>
        </div>
      )}

      <div
        ref={productResultsStartRef}
        className="product-page-results-anchor"
        aria-hidden="true"
      />

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

      {!isLoading && errorMessage && productPage !== null && (
        <div className="product-page-stale-notice" role="alert">
          <p>
            <strong>새 조건의 상품을 불러오지 못했습니다.</strong>
            <span>현재 목록은 이전 검색 결과입니다.</span>
          </p>
          <button type="button" onClick={handleRetry}>
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
          <div className="product-list" onClickCapture={handleProductCardNavigation}>
            {productPage.products.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>

          <Pagination
            currentPage={productPage.page}
            totalPages={productPage.totalPages}
            ariaLabel="상품 목록 페이지"
            mode="numbers"
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
