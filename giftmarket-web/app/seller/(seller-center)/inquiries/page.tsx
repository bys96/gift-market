"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { getSellerProductInquiries } from "@/lib/inquiry-api";
import Pagination from "@/components/common/Pagination";
import { useAuthStore } from "@/stores/auth-store";
import type { ProductInquiryPage, ProductInquiryStatus } from "@/types/inquiry";

const filters: Array<{ label: string; value?: ProductInquiryStatus }> = [{ label: "전체" }, { label: "답변 대기", value: "WAITING" }, { label: "답변 완료", value: "ANSWERED" }];

export default function SellerInquiriesPage() {
  const router = useRouter(); const initialized = useAuthStore((s) => s.initialized); const user = useAuthStore((s) => s.user); const authenticated = useAuthStore((s) => s.isAuthenticated);
  const [status, setStatus] = useState<ProductInquiryStatus | undefined>(); const [page, setPage] = useState(0); const [result, setResult] = useState<ProductInquiryPage | null>(null); const [loading, setLoading] = useState(true); const [error, setError] = useState("");
  const load = useCallback(async () => { try { setLoading(true); setError(""); setResult(await getSellerProductInquiries(status, page)); } catch (e) { setError(e instanceof Error ? e.message : "문의 목록을 불러오지 못했습니다."); } finally { setLoading(false); } }, [page, status]);
  useEffect(() => { if (!initialized) return; if (!authenticated || !user) { router.replace("/login"); return; }
    // 인증 확인 후 판매자 문의 목록을 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load(); }, [authenticated, initialized, load, router, user]);
  if (!initialized || !authenticated || !user) return null;
  return <main className="seller-inquiries-page"><div className="seller-inquiries-container"><header className="seller-inquiries-header"><div><p>PRODUCT Q&amp;A</p><h1>상품 문의 관리</h1></div><strong>{loading || error || !result ? "-" : result.totalElements}건</strong></header><div className="seller-inquiry-filters">{filters.map((f) => <button key={f.label} className={status === f.value ? "active" : ""} onClick={() => { setStatus(f.value); setPage(0); }}>{f.label}</button>)}</div>{loading ? <div className="seller-inquiry-state">문의 목록을 불러오고 있습니다.</div> : error ? <div className="seller-inquiry-state"><p>{error}</p><button onClick={() => void load()}>다시 시도</button></div> : result && result.inquiries.length ? <><table className="seller-inquiry-table"><thead><tr><th>문의번호</th><th>상품</th><th>제목</th><th>작성자</th><th>작성일</th><th>상태</th><th>관리</th></tr></thead><tbody>{result.inquiries.map((i) => <tr key={i.id}><td>#{i.id}</td><td>{i.productName}</td><td>{i.isPrivate ? "🔒 " : ""}{i.title}</td><td>{i.writerName}</td><td>{new Date(i.createdAt).toLocaleDateString("ko-KR")}</td><td><span className={`seller-inquiry-status ${i.status === "ANSWERED" ? "answered" : ""}`}>{i.status === "ANSWERED" ? "답변 완료" : "답변 대기"}</span></td><td><Link href={`/seller/inquiries/${i.id}`}>상세</Link></td></tr>)}</tbody></table><Pagination currentPage={result.page} totalPages={result.totalPages} ariaLabel="판매자 상품 문의 페이지" disabled={loading} onPageChange={setPage} className="seller-inquiry-pagination" /></> : <div className="seller-inquiry-state">해당 상태의 상품 문의가 없습니다.</div>}</div></main>;
}
