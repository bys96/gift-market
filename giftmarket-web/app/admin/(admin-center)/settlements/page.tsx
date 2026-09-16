"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { type FormEvent, Suspense, useCallback, useEffect, useRef, useState } from "react";

import Modal from "@/components/common/modal/Modal";
import Pagination from "@/components/common/Pagination";
import { generateAdminSettlement, getAdminSettlements } from "@/lib/admin-settlement-api";
import { settlementDate, settlementWon } from "@/lib/admin-settlement-format";
import {
  ADMIN_SETTLEMENT_STATUS_LABEL,
  type AdminSettlementGenerateRequest,
  type AdminSettlementPage,
  type AdminSettlementStatus,
} from "@/types/admin-settlement";

const STATUSES: AdminSettlementStatus[] = ["READY", "ON_HOLD", "CONFIRMED"];

function AdminSettlementListContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const page = Math.max(0, Number(searchParams.get("page")) || 0);
  const sellerIdParam = searchParams.get("sellerId") ?? "";
  const sellerId = Number(sellerIdParam);
  const statusParam = searchParams.get("status");
  const status = STATUSES.includes(statusParam as AdminSettlementStatus)
    ? statusParam as AdminSettlementStatus : undefined;
  const periodStart = searchParams.get("periodStart") ?? "";
  const periodEnd = searchParams.get("periodEnd") ?? "";
  const [sellerInput, setSellerInput] = useState(sellerIdParam);
  const [statusInput, setStatusInput] = useState<AdminSettlementStatus | "">(status ?? "");
  const [startInput, setStartInput] = useState(periodStart);
  const [endInput, setEndInput] = useState(periodEnd);
  const [filterError, setFilterError] = useState("");
  const [data, setData] = useState<AdminSettlementPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [generateOpen, setGenerateOpen] = useState(false);
  const [generateInput, setGenerateInput] = useState({ sellerId: "", periodStart: "", periodEnd: "", cutoff: "" });
  const [generateError, setGenerateError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [resultMessage, setResultMessage] = useState("");
  const [generatedId, setGeneratedId] = useState<number | null>(null);
  const generateSellerRef = useRef<HTMLInputElement>(null);

  const href = useCallback((changes: Record<string, string | number | undefined>) => {
    const next = new URLSearchParams(searchParams.toString());
    Object.entries(changes).forEach(([key, value]) => {
      if (value === undefined || value === "" || key === "page" && value === 0) next.delete(key);
      else next.set(key, String(value));
    });
    return next.toString() ? `/admin/settlements?${next}` : "/admin/settlements";
  }, [searchParams]);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError("");
      const response = await getAdminSettlements({
        sellerId: sellerIdParam ? sellerId : undefined,
        status,
        periodStart: periodStart || undefined,
        periodEnd: periodEnd || undefined,
        page,
        size: 20,
      });
      if (page > 0 && (response.totalPages === 0 || page >= response.totalPages)) {
        router.replace(href({ page: Math.max(0, response.totalPages - 1) }), { scroll: false });
        return;
      }
      setData(response);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "정산 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [href, page, periodEnd, periodStart, router, sellerId, sellerIdParam, status]);

  useEffect(() => {
    // URL 필터 및 페이지에 맞춰 관리자 정산 목록을 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const normalizedSeller = sellerInput.trim();
    if (normalizedSeller && (!Number.isSafeInteger(Number(normalizedSeller)) || Number(normalizedSeller) <= 0)) {
      setFilterError("판매자 ID는 1 이상의 정수로 입력해주세요.");
      return;
    }
    if (startInput && endInput && startInput >= endInput) {
      setFilterError("기간 시작은 종료보다 이전이어야 합니다.");
      return;
    }
    setFilterError("");
    router.push(href({ sellerId: normalizedSeller || undefined, status: statusInput || undefined,
      periodStart: startInput || undefined, periodEnd: endInput || undefined, page: 0 }), { scroll: false });
  };

  const closeGenerate = () => {
    if (submitting) return;
    setGenerateOpen(false);
    setGenerateError("");
  };

  const submitGenerate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting) return;
    const normalizedSeller = generateInput.sellerId.trim();
    if (!Number.isSafeInteger(Number(normalizedSeller)) || Number(normalizedSeller) <= 0
      || !generateInput.periodStart || !generateInput.periodEnd || !generateInput.cutoff) {
      setGenerateError("판매자 ID와 기간, 기준 시각을 모두 확인해주세요.");
      return;
    }
    if (generateInput.periodStart >= generateInput.periodEnd) {
      setGenerateError("기간 시작은 종료보다 이전이어야 합니다.");
      return;
    }
    try {
      setSubmitting(true);
      setGenerateError("");
      const request: AdminSettlementGenerateRequest = {
        sellerId: Number(normalizedSeller),
        periodStart: generateInput.periodStart,
        periodEnd: generateInput.periodEnd,
        cutoff: generateInput.cutoff,
      };
      const result = await generateAdminSettlement(request);
      setGenerateOpen(false);
      if (result.created && result.settlement) {
        setGeneratedId(result.settlement.settlementId);
        setResultMessage("정산이 생성되었습니다. 정산 상세에서 집계와 원장을 확인해주세요.");
        void load();
      } else {
        setGeneratedId(null);
        setResultMessage("해당 조건에 생성 가능한 정산 내역이 없습니다.");
      }
    } catch (failure) {
      setGenerateError(failure instanceof Error ? failure.message : "정산을 생성하지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="admin-users-page admin-settlements-page">
      <header className="admin-users-header">
        <div><p>SETTLEMENT MANAGEMENT</p><h1>정산 관리</h1><span>판매자별 정산 집계와 확정 상태를 관리합니다. 정산 확정은 실제 송금을 의미하지 않습니다.</span></div>
        <div><small>검색 결과</small><strong>{loading || error || !data ? "-" : data.totalElements.toLocaleString("ko-KR")}</strong><span>건</span></div>
      </header>

      <section className="admin-user-filter-panel admin-settlement-filter-panel" aria-label="정산 조회 조건">
        <form onSubmit={applyFilters} className="admin-settlement-filter-form">
          <label><span>판매자 ID</span><input type="number" min="1" step="1" value={sellerInput} onChange={(event) => setSellerInput(event.target.value)} placeholder="전체 판매자" /></label>
          <label><span>상태</span><select value={statusInput} onChange={(event) => setStatusInput(event.target.value as AdminSettlementStatus | "")}><option value="">전체</option>{STATUSES.map((item) => <option key={item} value={item}>{ADMIN_SETTLEMENT_STATUS_LABEL[item]}</option>)}</select></label>
          <label><span>기간 시작 이후</span><input type="datetime-local" value={startInput} onChange={(event) => setStartInput(event.target.value)} /></label>
          <label><span>기간 종료 이전</span><input type="datetime-local" value={endInput} onChange={(event) => setEndInput(event.target.value)} /></label>
          <button type="submit">조회</button>
          {filterError && <p className="admin-settlement-filter-error" role="alert">{filterError}</p>}
        </form>
      </section>

      <div className="admin-settlement-toolbar"><button type="button" onClick={() => { setGenerateInput({ sellerId: sellerIdParam, periodStart: "", periodEnd: "", cutoff: "" }); setGenerateError(""); setGenerateOpen(true); }}>정산 생성</button><span>대상 원장과 금액은 시스템이 계산합니다.</span></div>
      {resultMessage && <div className="admin-settlement-result" role="status"><span>{resultMessage}</span>{generatedId && <Link href={`/admin/settlements/${generatedId}`}>생성된 정산 보기 →</Link>}</div>}
      {error && <div className="admin-dashboard-error" role="alert"><span>{error}</span><button type="button" onClick={() => void load()}>다시 시도</button></div>}

      <section className="admin-user-list-section">
        <div className="admin-user-list-heading"><h2>정산 목록</h2>{data && !error && <span>총 {data.totalElements.toLocaleString("ko-KR")}건</span>}</div>
        {loading && !data ? <div className="admin-user-state">정산 목록을 불러오고 있습니다.</div>
          : !error && data?.content.length ? <div className="admin-user-table-wrap admin-settlement-table-wrap"><table className="admin-user-table admin-settlement-table"><thead><tr><th>정산번호</th><th>상점</th><th>정산 기간</th><th>상품 판매</th><th>배송비</th><th>취소·반품</th><th>수수료</th><th>조정</th><th>정산금액</th><th>상태</th><th>생성일</th><th>확정일</th><th></th></tr></thead><tbody>{data.content.map((item) => <tr key={item.settlementId}>
            <td data-label="정산번호"><Link href={`/admin/settlements/${item.settlementId}`} className="admin-settlement-number">{item.settlementNumber}</Link></td>
            <td data-label="상점"><Link href={`/admin/sellers/${item.sellerId}`}>{item.storeName}</Link><small>#{item.sellerId}</small></td>
            <td data-label="정산 기간">{settlementDate(item.periodStart)} ~ {settlementDate(item.periodEnd)}</td>
            <td data-label="상품 판매">{settlementWon(item.productSalesAmount)}</td>
            <td data-label="배송비">{settlementWon(item.shippingSalesAmount)}</td>
            <td data-label="취소·반품">{settlementWon(item.cancellationAmount + item.returnAmount)}</td>
            <td data-label="수수료">{settlementWon(item.commissionAmount)}</td>
            <td data-label="조정">{settlementWon(item.adjustmentAmount)}</td>
            <td data-label="정산금액"><strong>{settlementWon(item.settlementAmount)}</strong></td>
            <td data-label="상태"><span className={`admin-settlement-status is-${item.status.toLowerCase()}`}>{ADMIN_SETTLEMENT_STATUS_LABEL[item.status]}</span></td>
            <td data-label="생성일">{settlementDate(item.createdAt, true)}</td>
            <td data-label="확정일">{settlementDate(item.confirmedAt, true)}</td>
            <td><Link href={`/admin/settlements/${item.settlementId}`} className="admin-user-detail-link">상세</Link></td>
          </tr>)}</tbody></table></div>
          : !error && !loading ? <div className="admin-user-state"><strong>조건에 맞는 정산 내역이 없습니다.</strong><span>다른 판매자 또는 기간을 선택해 보세요.</span></div> : null}
        <Pagination currentPage={data?.page ?? page} totalPages={data?.totalPages ?? 0} ariaLabel="정산 목록 페이지" mode="numbers" disabled={loading} getPageHref={(target) => href({ page: target })} scroll={false} className="admin-user-pagination" />
      </section>

      {generateOpen && <Modal onClose={closeGenerate} overlayClassName="admin-user-modal-backdrop" contentClassName="admin-user-modal admin-settlement-generate-modal" ariaLabelledBy="admin-settlement-generate-title" ariaDescribedBy="admin-settlement-generate-description" initialFocusRef={generateSellerRef} closeOnEscape={!submitting} closeOnBackdrop={!submitting}>
        <form onSubmit={submitGenerate}>
          <header><h2 id="admin-settlement-generate-title">정산 생성</h2><button type="button" aria-label="닫기" onClick={closeGenerate} disabled={submitting}>×</button></header>
          <p id="admin-settlement-generate-description">판매자와 관리 기간, 정산 대상 기준 시각만 지정합니다. 원장 선정과 금액 계산은 서버가 수행합니다.</p>
          <div className="admin-settlement-generate-fields">
            <label>판매자 ID<input ref={generateSellerRef} type="number" min="1" step="1" required value={generateInput.sellerId} onChange={(event) => setGenerateInput({ ...generateInput, sellerId: event.target.value })} disabled={submitting} /></label>
            <label>기간 시작 (포함)<input type="datetime-local" required value={generateInput.periodStart} onChange={(event) => setGenerateInput({ ...generateInput, periodStart: event.target.value })} disabled={submitting} /></label>
            <label>기간 종료 (미포함)<input type="datetime-local" required value={generateInput.periodEnd} onChange={(event) => setGenerateInput({ ...generateInput, periodEnd: event.target.value })} disabled={submitting} /></label>
            <label>대상 기준 시각 (cutoff)<input type="datetime-local" required value={generateInput.cutoff} onChange={(event) => setGenerateInput({ ...generateInput, cutoff: event.target.value })} disabled={submitting} /></label>
          </div>
          <p className="admin-settlement-help">이전 회차에서 미귀속된 원장도 기간 종료·기준 시각 조건에 맞으면 포함될 수 있습니다.</p>
          {generateError && <p className="admin-user-action-error" role="alert">{generateError}</p>}
          <footer><button type="button" onClick={closeGenerate} disabled={submitting}>취소</button><button type="submit" className="primary" disabled={submitting}>{submitting ? "생성 중..." : "정산 생성"}</button></footer>
        </form>
      </Modal>}
    </main>
  );
}

export default function AdminSettlementsPage() {
  return <Suspense fallback={<div className="admin-user-state">정산 목록을 준비하고 있습니다.</div>}><AdminSettlementListContent /></Suspense>;
}
