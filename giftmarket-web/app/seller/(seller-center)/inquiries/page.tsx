"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import Pagination from "@/components/common/Pagination";
import { getSellerProductInquiries } from "@/lib/inquiry-api";
import { useAuthStore } from "@/stores/auth-store";
import type { ProductInquiryPage, ProductInquiryStatus } from "@/types/inquiry";

const filters: Array<{ label: string; value?: ProductInquiryStatus }> = [
  { label: "전체" },
  { label: "답변 대기", value: "WAITING" },
  { label: "답변 완료", value: "ANSWERED" },
];

export default function SellerInquiriesPage() {
  const router = useRouter();
  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const authenticated = useAuthStore((state) => state.isAuthenticated);
  const [status, setStatus] = useState<ProductInquiryStatus | undefined>();
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<ProductInquiryPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError("");
      setResult(await getSellerProductInquiries(status, page));
    } catch (failure) {
      setError(
        failure instanceof Error
          ? failure.message
          : "문의 목록을 불러오지 못했습니다.",
      );
    } finally {
      setLoading(false);
    }
  }, [page, status]);

  useEffect(() => {
    if (!initialized) return;
    if (!authenticated || !user) {
      router.replace("/login");
      return;
    }

    // 인증 확인 후 판매자 문의 목록을 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [authenticated, initialized, load, router, user]);

  if (!initialized || !authenticated || !user) return null;

  return (
    <main className="seller-inquiries-page">
      <div className="seller-inquiries-container">
        <header className="seller-inquiries-header">
          <div>
            <p>PRODUCT Q&amp;A</p>
            <h1>상품 문의 관리</h1>
          </div>
          <strong>
            {loading || error || !result ? "-" : result.totalElements}건
          </strong>
        </header>

        <div className="seller-inquiry-filters">
          {filters.map((filter) => (
            <button
              key={filter.label}
              className={status === filter.value ? "active" : ""}
              onClick={() => {
                setStatus(filter.value);
                setPage(0);
              }}
            >
              {filter.label}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="seller-inquiry-state">
            문의 목록을 불러오고 있습니다.
          </div>
        ) : error ? (
          <div className="seller-inquiry-state">
            <p>{error}</p>
            <button onClick={() => void load()}>다시 시도</button>
          </div>
        ) : result && result.inquiries.length ? (
          <>
            <table className="seller-inquiry-table">
              <thead>
                <tr>
                  <th>문의번호</th>
                  <th>상품</th>
                  <th>제목</th>
                  <th>작성자</th>
                  <th>작성일</th>
                  <th>상태</th>
                  <th>관리</th>
                </tr>
              </thead>
              <tbody>
                {result.inquiries.map((inquiry) => (
                  <tr key={inquiry.id}>
                    <td data-label="문의번호">#{inquiry.id}</td>
                    <td data-label="상품">{inquiry.productName}</td>
                    <td data-label="문의 제목">
                      {inquiry.isPrivate ? "🔒 " : ""}
                      {inquiry.title}
                    </td>
                    <td data-label="작성자">{inquiry.writerName}</td>
                    <td data-label="작성일">
                      {new Date(inquiry.createdAt).toLocaleDateString("ko-KR")}
                    </td>
                    <td data-label="상태">
                      <span
                        className={`seller-inquiry-status ${
                          inquiry.status === "ANSWERED" ? "answered" : ""
                        }`}
                      >
                        {inquiry.status === "ANSWERED" ? "답변 완료" : "답변 대기"}
                      </span>
                    </td>
                    <td data-label="관리">
                      <Link href={`/seller/inquiries/${inquiry.id}`}>상세</Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            <Pagination
              currentPage={result.page}
              totalPages={result.totalPages}
              ariaLabel="판매자 상품 문의 페이지"
              disabled={loading}
              onPageChange={setPage}
              className="seller-inquiry-pagination"
            />
          </>
        ) : (
          <div className="seller-inquiry-state">
            해당 상태의 상품 문의가 없습니다.
          </div>
        )}
      </div>
    </main>
  );
}
