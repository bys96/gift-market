"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
  Suspense,
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";

import Pagination from "@/components/common/Pagination";
import { formatInquiryDateTime } from "@/lib/inquiry-date";
import { getMyProductInquiries } from "@/lib/inquiry-api";
import { getLoginRedirectUrl } from "@/lib/login-redirect";
import { useAuthStore } from "@/stores/auth-store";
import type { ProductInquiryPage } from "@/types/inquiry";

function parseNonNegativeInteger(value: string | null, fallback: number) {
  if (value === null || value.trim() === "") return fallback;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : fallback;
}

function MyInquiriesContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const authenticated = useAuthStore((state) => state.isAuthenticated);
  const page = parseNonNegativeInteger(searchParams.get("page"), 0);
  const targetInquiryId = parseNonNegativeInteger(
    searchParams.get("inquiryId"),
    -1,
  );
  const handledTargetRef = useRef<number | null>(null);
  const requestIdRef = useRef(0);
  const [result, setResult] = useState<ProductInquiryPage | null>(null);
  const [openInquiryId, setOpenInquiryId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const createPageUrl = useCallback((nextPage: number) => {
    return nextPage > 0 ? `/my/inquiries?page=${nextPage}` : "/my/inquiries";
  }, []);

  const load = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    try {
      setLoading(true);
      setError("");
      const response = await getMyProductInquiries(page);
      if (requestId !== requestIdRef.current) return;

      if (page > 0 && (response.totalPages === 0 || page >= response.totalPages)) {
        router.replace(createPageUrl(Math.max(0, response.totalPages - 1)), {
          scroll: false,
        });
        return;
      }
      setResult(response);
    } catch (failure) {
      if (requestId !== requestIdRef.current) return;
      setError(failure instanceof Error ? failure.message : "내 문의를 불러오지 못했습니다.");
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  }, [createPageUrl, page, router]);

  useEffect(() => {
    if (!initialized) return;
    if (!authenticated || !user) {
      router.replace(getLoginRedirectUrl());
      return;
    }
    // 인증 확인 후 내 문의 목록을 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
    return () => {
      requestIdRef.current += 1;
    };
  }, [authenticated, initialized, load, router, user]);

  useEffect(() => {
    if (!result || targetInquiryId < 1) return;
    if (handledTargetRef.current === targetInquiryId) return;
    handledTargetRef.current = targetInquiryId;
    if (!result.inquiries.some((inquiry) => inquiry.id === targetInquiryId)) return;

    // URL로 지정된 문의만 최초 한 번 펼친 뒤 해당 행으로 이동한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setOpenInquiryId(targetInquiryId);
    window.requestAnimationFrame(() => {
      document.getElementById(`my-inquiry-${targetInquiryId}`)?.scrollIntoView({
        behavior: "smooth",
        block: "center",
      });
    });
  }, [result, targetInquiryId]);

  if (!initialized || !authenticated || !user) return null;

  return (
    <main className="my-inquiries-page">
      <header className="my-inquiries-header">
        <div>
          <p>나의 쇼핑</p>
          <h1>내 문의</h1>
          <span>작성한 상품 문의와 판매자 답변을 확인하세요.</span>
        </div>
        <Link href="/my">마이페이지</Link>
      </header>

      {loading && !result ? (
        <div className="my-inquiry-state">내 문의를 불러오고 있습니다.</div>
      ) : error && !result ? (
        <div className="my-inquiry-state is-error" role="alert">
          <p>{error}</p>
          <button type="button" onClick={() => void load()}>다시 시도</button>
        </div>
      ) : result && result.inquiries.length > 0 ? (
        <>
          {error && <p className="my-inquiry-page-error" role="alert">{error}</p>}
          <section className={`my-inquiry-list ${loading ? "is-loading" : ""}`} aria-busy={loading}>
            {result.inquiries.map((inquiry) => {
              const expanded = openInquiryId === inquiry.id;
              return (
                <article id={`my-inquiry-${inquiry.id}`} key={inquiry.id} className={expanded ? "is-open" : ""}>
                  <button
                    type="button"
                    className="my-inquiry-summary"
                    aria-expanded={expanded}
                    aria-controls={`my-inquiry-detail-${inquiry.id}`}
                    onClick={() => setOpenInquiryId(expanded ? null : inquiry.id)}
                  >
                    <span className={`my-inquiry-status ${inquiry.status === "ANSWERED" ? "is-answered" : ""}`}>
                      {inquiry.status === "ANSWERED" ? "답변 완료" : "답변 대기"}
                    </span>
                    <span className="my-inquiry-heading">
                      <strong>{inquiry.isPrivate && <span aria-label="비공개 문의">🔒 </span>}{inquiry.title}</strong>
                      <span>{inquiry.productName}</span>
                    </span>
                    <time dateTime={inquiry.createdAt}>{formatInquiryDateTime(inquiry.createdAt)}</time>
                    <span className="my-inquiry-chevron" aria-hidden="true">⌄</span>
                  </button>

                  {expanded && (
                    <div id={`my-inquiry-detail-${inquiry.id}`} className="my-inquiry-detail">
                      <section>
                        <strong>문의 내용</strong>
                        <p>{inquiry.content}</p>
                      </section>
                      {inquiry.answerContent ? (
                        <section className="my-inquiry-answer">
                          <div>
                            <strong>판매자 답변</strong>
                            <time dateTime={inquiry.answeredAt ?? undefined}>{formatInquiryDateTime(inquiry.answeredAt)}</time>
                          </div>
                          <p>{inquiry.answerContent}</p>
                        </section>
                      ) : (
                        <p className="my-inquiry-waiting">판매자가 답변을 준비하고 있습니다.</p>
                      )}
                      <Link href={`/products/${inquiry.productId}#product-inquiries`}>상품 문의 영역으로 이동</Link>
                    </div>
                  )}
                </article>
              );
            })}
          </section>
          <Pagination
            currentPage={result.page}
            totalPages={result.totalPages}
            ariaLabel="내 문의 페이지"
            mode="numbers"
            getPageHref={createPageUrl}
            scroll={false}
            disabled={loading}
            className="pagination my-inquiry-pagination"
          />
        </>
      ) : (
        <div className="my-inquiry-state">
          <h2>아직 작성한 상품 문의가 없습니다.</h2>
          <p>상품 상세에서 궁금한 점을 판매자에게 문의할 수 있습니다.</p>
          <Link href="/products">상품 보러 가기</Link>
        </div>
      )}
    </main>
  );
}

export default function MyInquiriesPage() {
  return (
    <Suspense fallback={null}>
      <MyInquiriesContent />
    </Suspense>
  );
}
