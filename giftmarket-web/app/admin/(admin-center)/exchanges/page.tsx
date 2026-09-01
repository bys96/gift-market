"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useCallback, useEffect, useState } from "react";
import Pagination from "@/components/common/Pagination";
import { getAdminExchanges } from "@/lib/admin-api";
import type { AdminExchangePage, AdminExchangeResponsibility, AdminExchangeStatus } from "@/types/admin";

const statuses: AdminExchangeStatus[] = ["REQUESTED","APPROVED","PAYMENT_PENDING","COLLECTING","RECEIVED","INSPECTED","RESHIPPING","COMPLETED","REJECTED","CANCELED","FAILED"];
const responsibilities: AdminExchangeResponsibility[] = ["BUYER", "SELLER"];
const label: Record<string, string> = { REQUESTED:"요청", APPROVED:"승인", PAYMENT_PENDING:"배송비 결제 대기", COLLECTING:"회수 중", RECEIVED:"입고", INSPECTED:"검수 완료", RESHIPPING:"재출고", COMPLETED:"완료", REJECTED:"거절", CANCELED:"취소", FAILED:"실패", BUYER:"구매자", SELLER:"판매자" };

function Content() {
  const router = useRouter(); const searchParams = useSearchParams();
  const page = Math.max(0, Number(searchParams.get("page")) || 0);
  const keyword = searchParams.get("keyword")?.trim() ?? "";
  const statusValue = searchParams.get("status"); const responsibilityValue = searchParams.get("responsibility");
  const status = statuses.includes(statusValue as AdminExchangeStatus) ? statusValue as AdminExchangeStatus : undefined;
  const responsibility = responsibilities.includes(responsibilityValue as AdminExchangeResponsibility) ? responsibilityValue as AdminExchangeResponsibility : undefined;
  const [query, setQuery] = useState(keyword); const [data, setData] = useState<AdminExchangePage | null>(null);
  const [loading, setLoading] = useState(true); const [error, setError] = useState("");
  const href = useCallback((changes: Record<string, string | number | undefined>) => { const next = new URLSearchParams(searchParams.toString()); Object.entries(changes).forEach(([key,value]) => value === undefined || value === "" || (key === "page" && value === 0) ? next.delete(key) : next.set(key,String(value))); return next.toString() ? `/admin/exchanges?${next}` : "/admin/exchanges"; }, [searchParams]);
  const load = useCallback(async () => { try { setLoading(true); setError(""); const result = await getAdminExchanges({ page, size:20, keyword:keyword || undefined, status, responsibility }); if (page > 0 && (result.totalPages === 0 || page >= result.totalPages)) { router.replace(href({ page:Math.max(0,result.totalPages - 1) }), { scroll:false }); return; } setData(result); } catch (caught) { setError(caught instanceof Error ? caught.message : "교환 목록을 불러오지 못했습니다."); } finally { setLoading(false); } }, [href,keyword,page,responsibility,router,status]);
  useEffect(() => { // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);
  const submit = (event: FormEvent) => { event.preventDefault(); router.push(href({ keyword:query.trim() || undefined, page:0 }), { scroll:false }); };
  return <main className="admin-users-page">
    <header className="admin-users-header"><div><p>EXCHANGE MANAGEMENT</p><h1>교환 관리</h1><span>회수·검수·배송비 결제·재출고 현황을 조회합니다.</span></div><div><small>검색 결과</small><strong>{loading || error || !data ? "-" : data.totalElements}</strong><span>건</span></div></header>
    <section className="admin-user-filter-panel"><form className="admin-user-search" onSubmit={submit}><label className="admin-user-search-input"><span className="sr-only">교환 검색</span><input value={query} onChange={event => setQuery(event.target.value)} placeholder="주문번호, 구매자, 판매자, 상품명" /></label><button>검색</button></form><div className="admin-user-filters"><label><span>상태</span><select value={status ?? ""} onChange={event => router.push(href({ status:event.target.value || undefined, page:0 }), { scroll:false })}><option value="">전체</option>{statuses.map(value => <option key={value} value={value}>{label[value]}</option>)}</select></label><label><span>귀책</span><select value={responsibility ?? ""} onChange={event => router.push(href({ responsibility:event.target.value || undefined, page:0 }), { scroll:false })}><option value="">전체</option>{responsibilities.map(value => <option key={value} value={value}>{label[value]}</option>)}</select></label></div></section>
    {error && <div className="admin-dashboard-error"><span>{error}</span><button onClick={load}>다시 시도</button></div>}
    <section className="admin-user-list-section"><div className="admin-user-list-heading"><h2>교환 요청 목록</h2>{data && !error && <span>총 {data.totalElements}건</span>}</div>
      {loading && !data ? <div className="admin-user-state">교환 요청을 불러오고 있습니다.</div> : data?.content.length ? <div className="admin-user-table-wrap"><table className="admin-user-table admin-cancellation-table"><thead><tr><th>교환번호</th><th>주문번호</th><th>구매자</th><th>판매자</th><th>상품</th><th>수량</th><th>귀책</th><th>상태</th><th>요청일</th><th></th></tr></thead><tbody>{data.content.map(exchange => <tr key={exchange.exchangeId}><td data-label="교환번호">#{exchange.exchangeId}</td><td data-label="주문번호"><Link href={`/admin/orders/${exchange.orderId}`}>{exchange.orderNumber}</Link></td><td data-label="구매자"><Link href={`/admin/users/${exchange.userId}`}>{exchange.userName}</Link></td><td data-label="판매자"><Link href={`/admin/sellers/${exchange.sellerId}`}>{exchange.storeName}</Link></td><td data-label="상품">{exchange.representativeProductName || "-"}{exchange.productTypeCount > 1 ? ` 외 ${exchange.productTypeCount - 1}종` : ""}</td><td data-label="수량">{exchange.requestedQuantity}개</td><td data-label="귀책">{exchange.responsibility ? label[exchange.responsibility] : "미확정"}</td><td data-label="상태"><span className={`admin-cancellation-badge is-${exchange.status.toLowerCase()}`}>{label[exchange.status]}</span></td><td data-label="요청일">{new Date(exchange.requestedAt).toLocaleString("ko-KR")}</td><td><Link href={`/admin/exchanges/${exchange.exchangeId}`} className="admin-user-detail-link">상세</Link></td></tr>)}</tbody></table></div> : <div className="admin-user-state"><strong>조건에 맞는 교환 요청이 없습니다.</strong></div>}
      <Pagination currentPage={data?.page ?? page} totalPages={data?.totalPages ?? 0} ariaLabel="교환 목록 페이지" mode="numbers" disabled={loading} getPageHref={value => href({ page:value })} scroll={false} className="admin-user-pagination" />
    </section>
  </main>;
}
export default function Page() { return <Suspense fallback={<div className="admin-user-state">교환 목록을 준비하고 있습니다.</div>}><Content /></Suspense>; }
