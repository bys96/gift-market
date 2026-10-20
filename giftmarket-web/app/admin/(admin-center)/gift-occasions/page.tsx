"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { createAdminGiftOccasion, getAdminGiftOccasions, updateAdminGiftOccasion, updateAdminGiftOccasionActive } from "@/lib/admin-api";
import type { AdminGiftOccasion } from "@/types/admin";

export default function AdminGiftOccasionsPage() {
  const [items, setItems] = useState<AdminGiftOccasion[]>([]);
  const [name, setName] = useState("");
  const [displayOrder, setDisplayOrder] = useState("1");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setLoading(true); setError(""); setItems(await getAdminGiftOccasions());
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "선물 상황 목록을 불러오지 못했습니다.");
    } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    // 서버의 최신 관리 목록과 최초 화면 상태를 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  const resetForm = () => { setEditingId(null); setName(""); setDisplayOrder(String(items.length + 1)); };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const order = Number(displayOrder);
    if (!name.trim() || !Number.isSafeInteger(order) || order < 0) {
      setError("이름과 0 이상의 노출 순서를 확인해주세요."); return;
    }
    try {
      setSubmitting(true); setError("");
      const request = { name: name.trim(), displayOrder: order };
      if (editingId === null) await createAdminGiftOccasion(request);
      else await updateAdminGiftOccasion(editingId, request);
      resetForm(); await load();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "선물 상황 저장에 실패했습니다.");
    } finally { setSubmitting(false); }
  };

  const toggleActive = async (item: AdminGiftOccasion) => {
    try {
      setSubmitting(true); setError("");
      await updateAdminGiftOccasionActive(item.id, !item.active); await load();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "활성 상태 변경에 실패했습니다.");
    } finally { setSubmitting(false); }
  };

  return (
    <main className="admin-users-page admin-gift-occasions-page">
      <header className="admin-users-header"><div><p>GIFT OCCASION MANAGEMENT</p><h1>선물 상황 관리</h1><span>상품에 연결할 선물 상황과 노출 순서를 관리합니다.</span></div></header>
      {error && <div className="admin-dashboard-error" role="alert"><span>{error}</span></div>}
      <form className="admin-occasion-form" onSubmit={submit}>
        <label>이름<input autoComplete="off" value={name} maxLength={100} onChange={(event) => setName(event.target.value)} disabled={submitting} /></label>
        <label>노출 순서<input type="number" autoComplete="off" min="0" value={displayOrder} onChange={(event) => setDisplayOrder(event.target.value)} disabled={submitting} /></label>
        <button type="submit" disabled={submitting}>{editingId === null ? "추가" : "수정 저장"}</button>
        {editingId !== null && <button type="button" className="secondary" onClick={resetForm} disabled={submitting}>취소</button>}
      </form>
      <section className="admin-user-list-section">
        <div className="admin-user-list-heading"><h2>전체 선물 상황</h2><span>총 {items.length}개</span></div>
        {loading && items.length === 0 ? <div className="admin-user-state">목록을 불러오는 중입니다.</div> : (
          <div className="admin-user-table-wrap"><table className="admin-user-table admin-occasion-table">
            <thead><tr><th>이름</th><th>상태</th><th>노출 순서</th><th>관리</th></tr></thead>
            <tbody>{items.map((item) => <tr key={item.id}>
              <td data-label="이름"><strong>{item.name}</strong></td>
              <td data-label="상태"><span className={`admin-occasion-status ${item.active ? "is-active" : "is-inactive"}`}>{item.active ? "활성" : "비활성"}</span></td>
              <td data-label="노출 순서">{item.displayOrder}</td>
              <td data-label="관리" className="admin-occasion-actions">
                <button type="button" onClick={() => { setEditingId(item.id); setName(item.name); setDisplayOrder(String(item.displayOrder)); }} disabled={submitting}>수정</button>
                <button type="button" onClick={() => void toggleActive(item)} disabled={submitting}>{item.active ? "비활성화" : "활성화"}</button>
              </td>
            </tr>)}</tbody>
          </table></div>
        )}
      </section>
    </main>
  );
}
