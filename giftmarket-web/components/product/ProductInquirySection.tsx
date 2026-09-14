"use client";

import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { usePathname, useRouter } from "next/navigation";

import Pagination from "@/components/common/Pagination";
import Modal from "@/components/common/modal/Modal";
import { formatInquiryDate } from "@/lib/inquiry-date";
import {
  createProductInquiry,
  deleteProductInquiry,
  getProductInquiries,
  updateProductInquiry,
} from "@/lib/inquiry-api";
import { useAuthStore } from "@/stores/auth-store";
import type { ProductInquiry, ProductInquiryPage } from "@/types/inquiry";

const EMPTY = { title: "", content: "", isPrivate: false };

export default function ProductInquirySection({ productId }: { productId: number }) {
  const router = useRouter();
  const pathname = usePathname();
  const authenticated = useAuthStore((state) => state.isAuthenticated);
  const [result, setResult] = useState<ProductInquiryPage | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [openInquiryId, setOpenInquiryId] = useState<number | null>(null);
  const [editing, setEditing] = useState<ProductInquiry | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(EMPTY);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const modalCloseRef = useRef<HTMLButtonElement>(null);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError("");
      const nextResult = await getProductInquiries(productId, page);
      if (page > 0 && (nextResult.totalPages === 0 || page >= nextResult.totalPages)) {
        setPage(Math.max(0, nextResult.totalPages - 1));
        return;
      }
      setResult(nextResult);
      setOpenInquiryId((current) =>
        nextResult.inquiries.some((inquiry) => inquiry.id === current) ? current : null,
      );
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "상품 문의를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [page, productId]);

  useEffect(() => {
    // API 조회 결과를 문의 UI 상태에 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  const startCreate = () => {
    if (!authenticated) {
      router.push(`/login?redirect=${encodeURIComponent(pathname)}`);
      return;
    }
    setEditing(null);
    setForm(EMPTY);
    setFormError("");
    setModalOpen(true);
  };

  const startEdit = (item: ProductInquiry) => {
    setEditing(item);
    setForm({
      title: item.title,
      content: item.content ?? "",
      isPrivate: item.isPrivate,
    });
    setFormError("");
    setModalOpen(true);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!form.title.trim() || !form.content.trim()) {
      setFormError("제목과 내용을 입력해주세요.");
      return;
    }
    try {
      setBusy(true);
      setFormError("");
      if (editing) await updateProductInquiry(productId, editing.id, form);
      else await createProductInquiry(productId, form);
      setModalOpen(false);
      setPage(0);
      await load();
    } catch (failure) {
      setFormError(failure instanceof Error ? failure.message : "문의를 저장하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const remove = async (id: number) => {
    if (!window.confirm("상품 문의를 삭제하시겠습니까?")) return;
    try {
      setBusy(true);
      await deleteProductInquiry(productId, id);
      if (openInquiryId === id) setOpenInquiryId(null);
      await load();
    } catch (failure) {
      alert(failure instanceof Error ? failure.message : "문의를 삭제하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <section id="product-inquiries" className="product-inquiry-section">
      <header>
        <div>
          <p>PRODUCT Q&amp;A</p>
          <h2>상품 문의 <span>{loading || error || !result ? "-" : result.totalElements}</span></h2>
        </div>
        <button type="button" onClick={startCreate}>문의 작성</button>
      </header>

      {loading ? (
        <div className="product-inquiry-state">상품 문의를 불러오고 있습니다.</div>
      ) : error ? (
        <div className="product-inquiry-state is-error">
          <p>{error}</p>
          <button type="button" onClick={() => void load()}>다시 시도</button>
        </div>
      ) : result && result.inquiries.length > 0 ? (
        <div className="product-inquiry-list">
          {result.inquiries.map((item) => {
            const expanded = openInquiryId === item.id;
            return (
              <article key={item.id} className={`${item.masked ? "is-masked" : ""} ${expanded ? "is-open" : ""}`}>
                <button
                  type="button"
                  className="product-inquiry-summary"
                  aria-expanded={expanded}
                  aria-controls={`product-inquiry-detail-${item.id}`}
                  onClick={() => setOpenInquiryId(expanded ? null : item.id)}
                >
                  <span className={`product-inquiry-status status-${item.status.toLowerCase()}`}>
                    {item.status === "ANSWERED" ? "답변 완료" : "답변 대기"}
                  </span>
                  <span className="product-inquiry-title">
                    {item.isPrivate && <b>비공개</b>}
                    <strong>{item.title}</strong>
                  </span>
                  <span className="product-inquiry-writer">{item.writerName}</span>
                  <time dateTime={item.createdAt}>{formatInquiryDate(item.createdAt)}</time>
                  <span className="product-inquiry-chevron" aria-hidden="true">⌄</span>
                </button>

                {expanded && (
                  <div id={`product-inquiry-detail-${item.id}`} className="product-inquiry-detail">
                    {item.masked ? (
                      <p className="product-inquiry-private">비공개 문의입니다.</p>
                    ) : (
                      <>
                        <p className="product-inquiry-content">{item.content}</p>
                        {item.answerContent && (
                          <div className="product-inquiry-answer">
                            <strong>판매자 답변</strong>
                            <p>{item.answerContent}</p>
                          </div>
                        )}
                      </>
                    )}
                    {item.mine && (
                      <div className="product-inquiry-actions">
                        {item.editable && <button type="button" onClick={() => startEdit(item)}>수정</button>}
                        <button type="button" disabled={busy} onClick={() => void remove(item.id)}>삭제</button>
                      </div>
                    )}
                  </div>
                )}
              </article>
            );
          })}
        </div>
      ) : (
        <div className="product-inquiry-state">등록된 상품 문의가 없습니다.</div>
      )}

      {result && (
        <Pagination
          currentPage={result.page}
          totalPages={result.totalPages}
          ariaLabel="상품 문의 페이지"
          disabled={loading}
          onPageChange={setPage}
          className="product-inquiry-pagination"
        />
      )}

      {modalOpen && (
        <Modal
          overlayClassName="product-inquiry-modal-backdrop"
          contentClassName="product-inquiry-modal-container"
          ariaLabelledBy="product-inquiry-modal-title"
          initialFocusRef={modalCloseRef}
          closeOnEscape={!busy}
          closeOnBackdrop={!busy}
          onClose={() => setModalOpen(false)}
        >
          <form className="product-inquiry-modal" onSubmit={submit}>
            <header>
              <h2 id="product-inquiry-modal-title">{editing ? "상품 문의 수정" : "상품 문의 작성"}</h2>
              <button ref={modalCloseRef} type="button" disabled={busy} onClick={() => setModalOpen(false)}>×</button>
            </header>
            <label>
              제목
              <input maxLength={100} value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} />
            </label>
            <label>
              내용
              <textarea rows={7} maxLength={2000} value={form.content} onChange={(event) => setForm({ ...form, content: event.target.value })} />
              <small>{form.content.length}/2000</small>
            </label>
            <label className="product-inquiry-private-check">
              <input type="checkbox" checked={form.isPrivate} onChange={(event) => setForm({ ...form, isPrivate: event.target.checked })} />
              비공개 문의
            </label>
            {formError && <p className="product-inquiry-form-error">{formError}</p>}
            <footer>
              <button type="button" disabled={busy} onClick={() => setModalOpen(false)}>취소</button>
              <button type="submit" disabled={busy}>{busy ? "저장 중..." : editing ? "수정하기" : "등록하기"}</button>
            </footer>
          </form>
        </Modal>
      )}
    </section>
  );
}
