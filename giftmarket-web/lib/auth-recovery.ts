import { recoverAuthSession } from "@/lib/api";

// Only authentication is recovered. Failed business requests are never retained.
export function observeAuthRecovery(): () => void {
  let timer: ReturnType<typeof setTimeout> | null = null;
  let lastAttempt = 0;
  let recovering = false;
  let pendingResume = false;
  let disposed = false;

  const active = () => document.visibilityState === "visible" && navigator.onLine;
  const schedule = () => {
    if (disposed || !active() || timer !== null) return;
    if (recovering) {
      pendingResume = true;
      return;
    }
    timer = setTimeout(() => {
      timer = null;
      if (disposed || !active()) return;
      lastAttempt = Date.now();
      recovering = true;
      void recoverAuthSession().catch(() => {
        // Preserve the session on network failure. apiFetch reports the error
        // on the next user request; only a later resume event retries recovery.
      }).finally(() => {
        recovering = false;
        if (pendingResume) {
          pendingResume = false;
          schedule();
        }
      });
    }, Math.max(250, lastAttempt + 30_000 - Date.now()));
  };

  document.addEventListener("visibilitychange", schedule);
  window.addEventListener("pageshow", schedule);
  window.addEventListener("online", schedule);

  return () => {
    disposed = true;
    document.removeEventListener("visibilitychange", schedule);
    window.removeEventListener("pageshow", schedule);
    window.removeEventListener("online", schedule);
    if (timer !== null) clearTimeout(timer);
  };
}
