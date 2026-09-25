"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import Modal from "@/components/common/modal/Modal";
import { getAdministrators, revokeAdministrator } from "@/lib/admin-api";
import { useAuthStore } from "@/stores/auth-store";
import type { Administrator } from "@/types/admin";

function formatDate(value: string | null) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(value));
}

export default function AdministratorsPage() {
  const router = useRouter();
  const role = useAuthStore((state) => state.user?.role);
  const [administrators, setAdministrators] = useState<Administrator[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [target, setTarget] = useState<Administrator | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const loadAdministrators = useCallback(async () => {
    try {
      setLoading(true);
      setError("");
      setAdministrators(await getAdministrators());
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "관리자 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (role && role !== "SUPER_ADMIN") {
      router.replace("/admin");
      return;
    }
    // 권한 확인 후 최초 관리자 목록을 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (role === "SUPER_ADMIN") void loadAdministrators();
  }, [loadAdministrators, role, router]);

  const revoke = async () => {
    if (!target || submitting) return;
    try {
      setSubmitting(true);
      setError("");
      await revokeAdministrator(target.id);
      setSuccess(`${target.name}님의 관리자 권한을 해제했습니다.`);
      setTarget(null);
      await loadAdministrators();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "관리자 권한 해제에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  if (role !== "SUPER_ADMIN") {
    return <div className="admin-user-state">접근 권한을 확인하고 있습니다.</div>;
  }

  return (
    <main className="admin-users-page admin-administrators-page">
      <header className="admin-users-header">
        <div>
          <p>ADMINISTRATOR MANAGEMENT</p>
          <h1>관리자 관리</h1>
          <span>관리자 현황을 확인하고 ADMIN 권한을 안전하게 해제합니다.</span>
        </div>
      </header>

      {success && <div className="admin-role-success" role="status">{success}</div>}
      {error && <div className="admin-dashboard-error" role="alert"><span>{error}</span><button type="button" onClick={loadAdministrators}>다시 시도</button></div>}

      <section className="admin-user-list-section">
        <div className="admin-user-list-heading"><h2>현재 관리자</h2><span>총 {administrators.length.toLocaleString("ko-KR")}명</span></div>
        {loading && !administrators.length ? (
          <div className="admin-user-state">관리자 목록을 불러오고 있습니다.</div>
        ) : administrators.length ? (
          <div className="admin-user-table-wrap">
            <table className="admin-user-table admin-administrator-table">
              <thead><tr><th>이름</th><th>이메일</th><th>권한</th><th>가입일</th><th>판매자 여부</th><th>관리자 지정일</th><th><span className="sr-only">관리</span></th></tr></thead>
              <tbody>{administrators.map((administrator) => (
                <tr key={administrator.id}>
                  <td data-label="이름"><strong>{administrator.name}</strong></td>
                  <td data-label="이메일">{administrator.email ?? "-"}</td>
                  <td data-label="권한"><span className={`admin-user-badge admin-user-role-${administrator.role.toLowerCase()}`}>{administrator.role === "SUPER_ADMIN" ? "최고 관리자" : "관리자"}</span></td>
                  <td data-label="가입일">{formatDate(administrator.createdAt)}</td>
                  <td data-label="판매자 여부">{administrator.seller ? "판매자" : "-"}</td>
                  <td data-label="관리자 지정일">{formatDate(administrator.administratorAssignedAt)}</td>
                  <td>{administrator.role === "ADMIN" ? <button type="button" className="admin-role-revoke-button" onClick={() => { setSuccess(""); setTarget(administrator); }}>권한 해제</button> : <span className="admin-role-protected">변경 불가</span>}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        ) : <div className="admin-user-state">등록된 관리자가 없습니다.</div>}
      </section>

      {target && (
        <Modal
          onClose={() => { if (!submitting) setTarget(null); }}
          overlayClassName="admin-user-modal-backdrop"
          contentClassName="admin-user-modal"
          ariaLabelledBy="admin-revoke-title"
          ariaDescribedBy="admin-revoke-description"
          closeOnEscape={!submitting}
          closeOnBackdrop={!submitting}
        >
          <header><h2 id="admin-revoke-title">관리자 권한 해제</h2><button type="button" aria-label="닫기" onClick={() => setTarget(null)} disabled={submitting}>×</button></header>
          <p id="admin-revoke-description"><strong>{target.name}</strong>님의 관리자 권한을 해제하시겠습니까? 판매자 정보가 있으면 SELLER, 없으면 USER 권한으로 복원됩니다.</p>
          <footer><button type="button" onClick={() => setTarget(null)} disabled={submitting}>취소</button><button type="button" className="danger" onClick={revoke} disabled={submitting}>{submitting ? "처리 중..." : "권한 해제"}</button></footer>
        </Modal>
      )}
    </main>
  );
}
