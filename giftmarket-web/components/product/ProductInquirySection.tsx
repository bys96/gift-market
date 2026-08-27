"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { createProductInquiry, deleteProductInquiry, getProductInquiries, updateProductInquiry } from "@/lib/inquiry-api";
import { useAuthStore } from "@/stores/auth-store";
import Pagination from "@/components/common/Pagination";
import type { ProductInquiry, ProductInquiryPage } from "@/types/inquiry";

const EMPTY = { title: "", content: "", isPrivate: false };

export default function ProductInquirySection({ productId }: { productId: number }) {
  const router = useRouter(); const pathname = usePathname();
  const authenticated = useAuthStore((s) => s.isAuthenticated);
  const [result, setResult] = useState<ProductInquiryPage | null>(null);
  const [page, setPage] = useState(0); const [loading, setLoading] = useState(true); const [error, setError] = useState("");
  const [editing, setEditing] = useState<ProductInquiry | null>(null); const [open, setOpen] = useState(false);
  const [form, setForm] = useState(EMPTY); const [busy, setBusy] = useState(false); const [formError, setFormError] = useState("");

  const load = useCallback(async () => { try { setLoading(true); setError(""); setResult(await getProductInquiries(productId, page)); } catch (e) { setError(e instanceof Error ? e.message : "상품 문의를 불러오지 못했습니다."); } finally { setLoading(false); } }, [page, productId]);
  useEffect(() => {
    // API 조회 결과를 문의 UI 상태에 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);
  const startCreate = () => { if (!authenticated) { router.push(`/login?redirect=${encodeURIComponent(pathname)}`); return; } setEditing(null); setForm(EMPTY); setFormError(""); setOpen(true); };
  const startEdit = (item: ProductInquiry) => { setEditing(item); setForm({ title: item.title, content: item.content ?? "", isPrivate: item.isPrivate }); setFormError(""); setOpen(true); };
  const submit = async (e: FormEvent) => { e.preventDefault(); if (!form.title.trim() || !form.content.trim()) { setFormError("제목과 내용을 입력해주세요."); return; } try { setBusy(true); setFormError(""); if (editing) await updateProductInquiry(productId, editing.id, form); else await createProductInquiry(productId, form); setOpen(false); setPage(0); await load(); } catch (err) { setFormError(err instanceof Error ? err.message : "문의를 저장하지 못했습니다."); } finally { setBusy(false); } };
  const remove = async (id: number) => { if (!window.confirm("상품 문의를 삭제하시겠습니까?")) return; try { setBusy(true); await deleteProductInquiry(productId, id); await load(); } catch (e) { alert(e instanceof Error ? e.message : "문의를 삭제하지 못했습니다."); } finally { setBusy(false); } };

  return <section id="product-inquiries" className="product-inquiry-section">
    <header><div><p>PRODUCT Q&amp;A</p><h2>상품 문의 <span>{result?.totalElements ?? 0}</span></h2></div><button type="button" onClick={startCreate}>문의 작성</button></header>
    {loading ? <div className="product-inquiry-state">상품 문의를 불러오고 있습니다.</div> : error ? <div className="product-inquiry-state is-error"><p>{error}</p><button type="button" onClick={() => void load()}>다시 시도</button></div> : result && result.inquiries.length > 0 ? <div className="product-inquiry-list">{result.inquiries.map((item) => <article key={item.id} className={item.masked ? "is-masked" : ""}><div className="product-inquiry-meta"><span className={`status-${item.status.toLowerCase()}`}>{item.status === "ANSWERED" ? "답변 완료" : "답변 대기"}</span>{item.isPrivate && <b>비공개</b>}<span>{item.writerName}</span><time>{new Date(item.createdAt).toLocaleDateString("ko-KR")}</time></div><h3>{item.title}</h3>{item.masked ? <p className="product-inquiry-private">비공개 문의입니다.</p> : <><p className="product-inquiry-content">{item.content}</p>{item.answerContent && <div className="product-inquiry-answer"><strong>판매자 답변</strong><p>{item.answerContent}</p></div>}</>}{item.mine && <div className="product-inquiry-actions">{item.editable && <button type="button" onClick={() => startEdit(item)}>수정</button>}<button type="button" disabled={busy} onClick={() => void remove(item.id)}>삭제</button></div>}</article>)}</div> : <div className="product-inquiry-state">등록된 상품 문의가 없습니다.</div>}
    {result && <Pagination currentPage={result.page} totalPages={result.totalPages} ariaLabel="상품 문의 페이지" disabled={loading} onPageChange={setPage} className="product-inquiry-pagination" />}
    {open && <div className="product-inquiry-modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget && !busy) setOpen(false); }}><form className="product-inquiry-modal" onSubmit={submit}><header><h2>{editing ? "상품 문의 수정" : "상품 문의 작성"}</h2><button type="button" disabled={busy} onClick={() => setOpen(false)}>×</button></header><label>제목<input maxLength={100} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></label><label>내용<textarea rows={7} maxLength={2000} value={form.content} onChange={(e) => setForm({ ...form, content: e.target.value })} /><small>{form.content.length}/2000</small></label><label className="product-inquiry-private-check"><input type="checkbox" checked={form.isPrivate} onChange={(e) => setForm({ ...form, isPrivate: e.target.checked })} />비공개 문의</label>{formError && <p className="product-inquiry-form-error">{formError}</p>}<footer><button type="button" disabled={busy} onClick={() => setOpen(false)}>취소</button><button type="submit" disabled={busy}>{busy ? "저장 중..." : editing ? "수정하기" : "등록하기"}</button></footer></form></div>}
  </section>;
}
