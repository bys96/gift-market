"use client";

import { useEffect, useRef, useSyncExternalStore } from "react";
import { usePathname } from "next/navigation";
import type { RefObject } from "react";
import {
  finishScrollRestoration, getScrollEntry, getScrollPosition, hasRestoredScroll,
  isScrollRestorationExcluded, saveScrollPosition, setScrollCapture, subscribeScrollNavigation,
} from "@/lib/scroll-restoration";

interface Options {
  ready: boolean;
  failed?: boolean;
  state?: Record<string, string | number>;
  container?: RefObject<HTMLElement | null>;
}

export function useScrollRestoration({ ready, failed = false, state, container }: Options) {
  const pathname = usePathname();
  const navigation = useSyncExternalStore(subscribeScrollNavigation,
    () => getScrollEntry()?.revision ?? 0, () => 0);
  const stateKey = state ? JSON.stringify(state) : "";
  const previousState = useRef(stateKey);
  const pendingStateReset = useRef(false);

  useEffect(() => {
    if (isScrollRestorationExcluded(pathname)) return;
    const url = `${pathname}${window.location.search}`;
    let stop = () => {};
    const changedState = previousState.current !== stateKey;
    previousState.current = stateKey;
    if (changedState) pendingStateReset.current = true;

    const check = () => {
      stop();
      stop = () => {};
      const entry = getScrollEntry();
      if (!entry || entry.url !== url || !ready || failed) return;
      const target = container?.current;
      const read = () => ({ x: target ? target.scrollLeft : window.scrollX, y: target ? target.scrollTop : window.scrollY });
      const write = (x: number, y: number) => {
        if (target) target.scrollTo({ left: x, top: y, behavior: "instant" });
        else window.scrollTo({ left: x, top: y, behavior: "instant" });
      };
      const capture = () => {
        const point = read();
        saveScrollPosition(entry.id, url, stateKey, point.x, point.y);
      };
      const key = `${entry.revision}:${stateKey}`;
      const saved = getScrollPosition(entry.id);
      const restore = entry.kind === "traverse" && saved?.url === url && saved.state === stateKey;
      const reset = (entry.kind === "navigate" || pendingStateReset.current) && !window.location.hash;
      if ((hasRestoredScroll(key) && !pendingStateReset.current) || (!restore && !reset) || window.location.hash) {
        stop = setScrollCapture(capture);
        return;
      }

      let frame: number | null = null;
      let timer: ReturnType<typeof setTimeout> | null = null;
      let observer: ResizeObserver | null = null;
      let releaseCapture = () => {};
      let finished = false;
      let layoutReady = false;
      const x = restore ? saved.x : 0;
      const y = restore ? saved.y : 0;
      const dispose = () => {
        finished = true;
        if (frame !== null) window.cancelAnimationFrame(frame);
        if (timer !== null) clearTimeout(timer);
        observer?.disconnect();
        window.removeEventListener("wheel", cancel);
        window.removeEventListener("touchstart", cancel);
        window.removeEventListener("keydown", cancel);
        releaseCapture();
      };
      const complete = () => {
        finished = true;
        dispose();
        finishScrollRestoration(key);
        pendingStateReset.current = false;
        releaseCapture = setScrollCapture(capture);
        stop = releaseCapture;
      };
      const cancel = () => { if (!finished) complete(); };
      const attempt = (final = false) => {
        if (finished) return;
        const current = read();
        const height = target ? target.scrollHeight - target.clientHeight
          : Math.max(document.documentElement.scrollHeight, document.body.scrollHeight) - window.innerHeight;
        if (Math.abs(current.y - y) <= 2 && Math.abs(current.x - x) <= 2) {
          complete();
        } else if (height >= y || final) {
          write(x, Math.min(y, Math.max(0, height)));
          complete();
        }
      };
      const schedule = () => {
        if (frame !== null || finished || !layoutReady) return;
        frame = window.requestAnimationFrame(() => { frame = null; attempt(); });
      };
      // Allow native/Next restoration two layout frames before applying a fallback.
      frame = window.requestAnimationFrame(() => {
        frame = window.requestAnimationFrame(() => {
          frame = null;
          layoutReady = true;
          attempt();
        });
      });
      if (typeof ResizeObserver !== "undefined") {
        observer = new ResizeObserver(schedule);
        observer.observe(target ?? document.body);
        if (target?.firstElementChild) observer.observe(target.firstElementChild);
      }
      timer = setTimeout(() => attempt(true), 4_000);
      window.addEventListener("wheel", cancel, { passive: true });
      window.addEventListener("touchstart", cancel, { passive: true });
      window.addEventListener("keydown", cancel);
      stop = dispose;
    };

    check();
    return () => { stop(); };
  }, [navigation, pathname, ready, failed, stateKey, container]);
}
