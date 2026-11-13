const HISTORY_KEY = "giftMarketScrollEntry";
const STORAGE_KEY = "gift-market-scroll-positions-v1";
const MAX_ENTRIES = 100;
const MAX_AGE_MS = 24 * 60 * 60 * 1000;

interface ScrollPosition {
  url: string;
  x: number;
  y: number;
  state: string;
  savedAt: number;
}

interface ScrollEntry {
  id: string;
  url: string;
  kind: "initial" | "reload" | "navigate" | "traverse" | "native";
  revision: number;
}

let positions: Record<string, ScrollPosition> | null = null;
let entry: ScrollEntry | null = null;
let revision = 0;
let capture: (() => void) | null = null;
const subscribers = new Set<() => void>();
const completed = new Set<string>();

export function isScrollRestorationExcluded(pathname: string): boolean {
  return pathname === "/order" || pathname.startsWith("/payment/")
    || pathname.startsWith("/my/exchanges/payment/")
    || pathname === "/login" || pathname.startsWith("/oauth/");
}

function currentUrl() {
  return `${window.location.pathname}${window.location.search}`;
}

function records(): Record<string, ScrollPosition> {
  if (positions) return positions;
  positions = {};
  if (typeof window === "undefined") return positions;
  try {
    const parsed: unknown = JSON.parse(window.sessionStorage.getItem(STORAGE_KEY) ?? "{}");
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      for (const [id, value] of Object.entries(parsed)) {
        const item = value as ScrollPosition;
        if (item && typeof item.url === "string" && typeof item.state === "string"
          && Number.isFinite(item.x) && Number.isFinite(item.y)
          && item.x >= 0 && item.y >= 0 && Number.isFinite(item.savedAt)
          && Date.now() - item.savedAt < MAX_AGE_MS) positions[id] = item;
      }
    }
  } catch {
    // Storage restrictions must not interrupt navigation.
  }
  return positions;
}

function flush() {
  if (typeof window === "undefined") return;
  const retained = Object.entries(records())
    .filter(([, value]) => Date.now() - value.savedAt < MAX_AGE_MS)
    .sort((left, right) => right[1].savedAt - left[1].savedAt)
    .slice(0, MAX_ENTRIES);
  positions = Object.fromEntries(retained);
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(positions));
  } catch {
    // Keep the in-memory fallback when sessionStorage is unavailable.
  }
}

export function getScrollEntry() { return entry; }

export function getScrollPosition(id: string) {
  if (typeof window === "undefined") return null;
  const position = records()[id];
  return position && Date.now() - position.savedAt < MAX_AGE_MS ? position : null;
}

export function saveScrollPosition(id: string, url: string, state: string, x: number, y: number) {
  if (typeof window === "undefined") return;
  records()[id] = { url, state, x, y, savedAt: Date.now() };
}

export function subscribeScrollNavigation(listener: () => void) {
  subscribers.add(listener);
  return () => { subscribers.delete(listener); };
}

export function setScrollCapture(reader: () => void) {
  capture = reader;
  return () => { if (capture === reader) capture = null; };
}

export function hasRestoredScroll(key: string) { return completed.has(key); }
export function finishScrollRestoration(key: string) {
  completed.add(key);
  if (completed.size > MAX_ENTRIES) completed.delete(completed.values().next().value!);
}

// Only local list view state is kept here, never API data or authentication.
export function getRestoredScrollState<T extends Record<string, string | number>>(defaults: T): T {
  if (typeof window === "undefined" || !entry || entry.kind !== "traverse" || entry.url !== currentUrl()) return defaults;
  const position = getScrollPosition(entry.id);
  if (!position || position.url !== entry.url) return defaults;
  try {
    const value = JSON.parse(position.state);
    if (value && Object.keys(defaults).every((key) => typeof value[key] === typeof defaults[key])) {
      return { ...defaults, ...value };
    }
  } catch {
    // A list with URL-based state does not need a separate view state.
  }
  return defaults;
}

export function startScrollHistoryTracking(): () => void {
  if (typeof window === "undefined") return () => {};
  const history = window.history;
  const push = history.pushState;
  const replace = history.replaceState;
  let frame: number | null = null;
  let active = true;

  const selectEntry = (kind: ScrollEntry["kind"]) => {
    capture = null;
    if (isScrollRestorationExcluded(window.location.pathname)) {
      entry = null;
    } else {
      const id = typeof history.state?.[HISTORY_KEY] === "string"
        ? history.state[HISTORY_KEY] : crypto.randomUUID();
      if (history.state?.[HISTORY_KEY] !== id) {
        replace.call(history, { ...history.state, [HISTORY_KEY]: id }, "");
      }
      entry = { id, url: currentUrl(), kind, revision: ++revision };
    }
    subscribers.forEach((listener) => listener());
  };

  const change = (original: History["pushState"], replacing: boolean): History["pushState"] =>
    function (data, unused, url) {
      if (!active) return original.call(history, data, unused, url);
      flush();
      const next = new URL(url?.toString() ?? window.location.href, window.location.href);
      const changed = `${next.pathname}${next.search}` !== currentUrl();
      const excluded = isScrollRestorationExcluded(next.pathname);
      const id = replacing && !changed ? history.state?.[HISTORY_KEY] : crypto.randomUUID();
      original.call(history, excluded ? data : { ...data, [HISTORY_KEY]: id ?? crypto.randomUUID() }, unused, url);
      if (!replacing || changed) selectEntry("navigate");
    };
  const wrappedPush = change(push, false);
  const wrappedReplace = change(replace, true);
  history.pushState = wrappedPush;
  history.replaceState = wrappedReplace;

  const pop = () => { flush(); selectEntry("traverse"); };
  const show = (event: PageTransitionEvent) => { if (event.persisted) selectEntry("native"); };
  const hash = () => { selectEntry("native"); };
  const scroll = () => {
    if (frame !== null) return;
    const id = entry?.id;
    frame = window.requestAnimationFrame(() => {
      frame = null;
      if (entry?.id === id) capture?.();
    });
  };
  const leave = () => { capture?.(); flush(); };
  window.addEventListener("popstate", pop);
  window.addEventListener("pageshow", show);
  window.addEventListener("hashchange", hash);
  window.addEventListener("scroll", scroll, { passive: true, capture: true });
  window.addEventListener("pagehide", leave);
  document.addEventListener("click", leave, true);

  const navigation = performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming | undefined;
  if (!entry || entry.url !== currentUrl()) {
    selectEntry(navigation?.type === "back_forward" ? "traverse" : navigation?.type === "reload" ? "reload" : "initial");
  }

  return () => {
    leave();
    active = false;
    capture = null;
    if (history.pushState === wrappedPush) history.pushState = push;
    if (history.replaceState === wrappedReplace) history.replaceState = replace;
    window.removeEventListener("popstate", pop);
    window.removeEventListener("pageshow", show);
    window.removeEventListener("hashchange", hash);
    window.removeEventListener("scroll", scroll, true);
    window.removeEventListener("pagehide", leave);
    document.removeEventListener("click", leave, true);
    if (frame !== null) window.cancelAnimationFrame(frame);
  };
}
