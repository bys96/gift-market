"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import OrderHistoryCard from "@/components/order/OrderHistoryCard";
import { getMyOrders } from "@/lib/order-api";
import { useAuthStore } from "@/stores/auth-store";
import type { BuyerOrderPage } from "@/types/order";

const PAGE_SIZE = 10;
const PAGE_GROUP_SIZE = 5;

function parsePage(value: string | null) {
  const parsed = Number(value ?? "0");
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;
}

function createPageNumbers(currentPage: number, totalPages: number) {
  const start = Math.floor(currentPage / PAGE_GROUP_SIZE) * PAGE_GROUP_SIZE;
  const end = Math.min(start + PAGE_GROUP_SIZE, totalPages);
  return Array.from(
    { length: Math.max(0, end - start) },
    (_, index) => start + index,
  );
}

function MyOrdersContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const requestIdRef = useRef(0);
  const page = parsePage(searchParams.get("page"));

  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [orderPage, setOrderPage] = useState<BuyerOrderPage | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const createOrdersUrl = useCallback((nextPage: number) => {
    return nextPage > 0 ? `/my/orders?page=${nextPage}` : "/my/orders";
  }, []);

  const pageNumbers = useMemo(
    () => createPageNumbers(orderPage?.page ?? 0, orderPage?.totalPages ?? 0),
    [orderPage],
  );

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/login");
    }
  }, [initialized, isAuthenticated, user, router]);

  useEffect(() => {
    if (!initialized || !isAuthenticated || !user) {
      return;
    }

    const requestId = ++requestIdRef.current;

    const loadOrders = async () => {
      try {
        setIsLoading(true);
        setErrorMessage("");

        const response = await getMyOrders(page, PAGE_SIZE);

        if (requestId !== requestIdRef.current) {
          return;
        }

        if (
          page > 0 &&
          (response.totalPages === 0 || page >= response.totalPages)
        ) {
          router.replace(createOrdersUrl(Math.max(response.totalPages - 1, 0)), {
            scroll: false,
          });
          return;
        }

        setOrderPage(response);
      } catch (error) {
        if (requestId !== requestIdRef.current) {
          return;
        }

        setErrorMessage(
          error instanceof Error
            ? error.message
            : "주문 내역을 불러오지 못했습니다.",
        );
      } finally {
        if (requestId === requestIdRef.current) {
          setIsLoading(false);
        }
      }
    };

    void loadOrders();

    return () => {
      requestIdRef.current += 1;
    };
  }, [createOrdersUrl, initialized, isAuthenticated, page, router, user]);

  if (!initialized) {
    return null;
  }

  if (!isAuthenticated || !user) {
    return null;
  }

  return (
    <div className="my-orders-page">
      <div className="my-orders-header">
        <div>
          <p className="my-orders-eyebrow">나의 쇼핑</p>

          <h1 className="my-orders-title">주문 내역</h1>
        </div>

        <Link href="/my" className="my-orders-back-link">
          마이페이지
        </Link>
      </div>

      {isLoading && orderPage === null ? (
        <section className="order-history-empty">
          <h2>주문 내역을 불러오고 있습니다.</h2>

          <p>잠시 후 주문 정보를 확인할 수 있습니다.</p>
        </section>
      ) : errorMessage && orderPage === null ? (
        <section className="order-history-empty">
          <h2>주문 내역을 불러오지 못했습니다.</h2>

          <p>{errorMessage}</p>

          <button
            type="button"
            className="order-history-empty-link"
            onClick={() => window.location.reload()}
          >
            다시 시도
          </button>
        </section>
      ) : orderPage && orderPage.content.length > 0 ? (
        <>
          {errorMessage && (
            <p className="order-history-page-error" role="alert">
              {errorMessage}
            </p>
          )}
          <div
            className={`order-history-list ${isLoading ? "is-loading" : ""}`}
            aria-busy={isLoading}
          >
            {orderPage.content.map((order) => (
              <OrderHistoryCard key={order.id} order={order} />
            ))}
          </div>

          {orderPage.totalPages > 1 && (
            <nav
              className="order-history-pagination"
              aria-label="주문 내역 페이지"
            >
              <Link
                href={createOrdersUrl(Math.max(orderPage.page - 1, 0))}
                scroll={false}
                className={orderPage.first ? "is-disabled" : ""}
                aria-disabled={orderPage.first}
                tabIndex={orderPage.first ? -1 : undefined}
                onClick={(event) => orderPage.first && event.preventDefault()}
              >
                이전
              </Link>
              {pageNumbers.map((pageNumber) => (
                <Link
                  key={pageNumber}
                  href={createOrdersUrl(pageNumber)}
                  scroll={false}
                  className={pageNumber === orderPage.page ? "is-active" : ""}
                  aria-current={
                    pageNumber === orderPage.page ? "page" : undefined
                  }
                >
                  {pageNumber + 1}
                </Link>
              ))}
              <Link
                href={createOrdersUrl(
                  Math.min(orderPage.page + 1, orderPage.totalPages - 1),
                )}
                scroll={false}
                className={orderPage.last ? "is-disabled" : ""}
                aria-disabled={orderPage.last}
                tabIndex={orderPage.last ? -1 : undefined}
                onClick={(event) => orderPage.last && event.preventDefault()}
              >
                다음
              </Link>
            </nav>
          )}
        </>
      ) : (
        <section className="order-history-empty">
          <div className="order-history-empty-icon" aria-hidden="true">
            📦
          </div>

          <h2>아직 주문 내역이 없습니다.</h2>

          <p>마음을 전할 상품을 찾아보세요.</p>

          <Link href="/products" className="order-history-empty-link">
            상품 보러 가기
          </Link>
        </section>
      )}
    </div>
  );
}

export default function MyOrdersPage() {
  return (
    <Suspense fallback={null}>
      <MyOrdersContent />
    </Suspense>
  );
}
