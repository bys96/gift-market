"use client";

import { useEffect, useState } from "react";
import { hasPendingLogout, logoutSession } from "@/lib/api";
import { initializeAuth } from "@/lib/auth-initialization";
import { observeAuthRecovery } from "@/lib/auth-recovery";

export default function AuthInitializer() {
  const [pendingLogout, setPendingLogout] = useState(false);
  const [retrying, setRetrying] = useState(false);

  useEffect(() => {
    const update = () => setPendingLogout(hasPendingLogout());
    window.addEventListener("gift-market-logout-change", update);
    window.addEventListener("storage", update);
    void initializeAuth();
    update();
    const stopRecovery = observeAuthRecovery();
    return () => {
      stopRecovery();
      window.removeEventListener("gift-market-logout-change", update);
      window.removeEventListener("storage", update);
    };
  }, []);

  if (!pendingLogout) return null;

  return (
    <aside aria-label="로그아웃 상태">
      <p role="status">서버 로그아웃이 아직 완료되지 않았습니다. 연결을 확인한 뒤 다시 시도해주세요.</p>
      <button type="button" disabled={retrying} onClick={async () => {
        setRetrying(true);
        try {
          await logoutSession();
        } finally {
          setRetrying(false);
        }
      }}>
        {retrying ? "로그아웃 처리 중" : "서버 로그아웃 다시 시도"}
      </button>
    </aside>
  );
}
