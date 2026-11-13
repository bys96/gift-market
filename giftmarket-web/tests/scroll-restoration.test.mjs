import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";

function evaluate(path, context) {
  const exports = {};
  const source = readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
  vm.runInNewContext(ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText, { exports, URL, ...context });
  return exports;
}

class Surface extends EventTarget {
  listeners = new Map();
  addEventListener(type, listener, options) {
    if (!this.listeners.has(type)) this.listeners.set(type, new Set());
    this.listeners.get(type).add(listener);
    super.addEventListener(type, listener, options);
  }
  removeEventListener(type, listener, options) {
    this.listeners.get(type)?.delete(listener);
    super.removeEventListener(type, listener, options);
  }
  get listenerCount() { return [...this.listeners.values()].reduce((sum, value) => sum + value.size, 0); }
}

function fixture({ path = "/my/orders?page=2", navigation = "navigate", stored = {}, storageBlocked = false } = {}) {
  const window = new Surface();
  const document = new Surface();
  const frames = new Map();
  const timers = new Map();
  const observers = new Set();
  const storage = new Map(Object.entries(stored));
  let sequence = 0;
  let now = Date.now();
  window.location = new URL(path, "https://shop.example");
  window.scrollX = 0;
  window.scrollY = 0;
  window.innerHeight = 600;
  window.moves = [];
  document.body = { scrollHeight: 2400 };
  document.documentElement = { scrollHeight: 2400 };
  window.scrollTo = (point) => {
    window.scrollX = point.left;
    window.scrollY = point.top;
    window.moves.push(point.top);
  };
  window.requestAnimationFrame = (callback) => { const id = ++sequence; frames.set(id, callback); return id; };
  window.cancelAnimationFrame = (id) => frames.delete(id);
  window.sessionStorage = {
    getItem: (key) => { if (storageBlocked) throw Error("blocked"); return storage.get(key) ?? null; },
    setItem: (key, value) => { if (storageBlocked) throw Error("blocked"); storage.set(key, value); },
  };
  const stack = [{ state: { next: "preserved" }, url: window.location.href }];
  let index = 0;
  const history = {
    scrollRestoration: "auto",
    get state() { return stack[index].state; },
    pushState(data, _unused, url) {
      stack.splice(index + 1);
      stack.push({ state: data, url: new URL(url ?? window.location.href, window.location.href).href });
      index++;
      window.location = new URL(stack[index].url);
    },
    replaceState(data, _unused, url) {
      stack[index] = { state: data, url: new URL(url ?? window.location.href, window.location.href).href };
      window.location = new URL(stack[index].url);
    },
  };
  window.history = history;
  const context = {
    window, document, Date: { now: () => now },
    crypto: { randomUUID: () => `entry-${++sequence}` },
    performance: { getEntriesByType: () => [{ type: navigation }] },
    setTimeout: (callback) => { const id = ++sequence; timers.set(id, callback); return id; },
    clearTimeout: (id) => timers.delete(id),
    ResizeObserver: class {
      constructor(callback) { this.callback = callback; observers.add(this); }
      observe() {}
      disconnect() { observers.delete(this); }
    },
  };
  const utility = evaluate("lib/scroll-restoration.ts", context);
  const start = () => utility.startScrollHistoryTracking();
  const frame = () => {
    const pending = [...frames.values()];
    frames.clear();
    pending.forEach((callback) => callback());
  };
  const resize = (height) => {
    document.body.scrollHeight = height;
    document.documentElement.scrollHeight = height;
    [...observers].forEach((observer) => observer.callback());
  };
  const traverse = (offset) => {
    index += offset;
    window.location = new URL(stack[index].url);
    window.dispatchEvent(new Event("popstate"));
  };
  const hook = () => {
    const refs = [];
    let cursor = 0;
    let cleanup;
    let previousDeps;
    const { useScrollRestoration: renderHook } = evaluate("hooks/useScrollRestoration.ts", {
      ...context,
      require: (name) => {
        if (name === "@/lib/scroll-restoration") return utility;
        if (name === "next/navigation") return { usePathname: () => window.location.pathname };
        if (name === "react") return {
          useRef: (value) => { const slot = cursor++; return refs[slot] ??= { current: value }; },
          useSyncExternalStore: (_subscribe, snapshot) => snapshot(),
          useEffect: (effect, deps) => {
            if (!previousDeps || deps.some((value, i) => value !== previousDeps[i])) {
              cleanup?.();
              previousDeps = deps;
              cleanup = effect();
            }
          },
        };
        throw Error(name);
      },
    });
    return {
      render(options = { ready: true }) { cursor = 0; renderHook(options); },
      unmount() { cleanup?.(); },
    };
  };
  return { window, document, utility, start, frame, resize, traverse, hook, storage, timers, observers,
    advance: (value) => { now += value; },
    timeout: () => { const pending = [...timers.values()]; timers.clear(); pending.forEach((callback) => callback()); } };
}

function leaveList(f, url = "/my/orders/56") {
  f.document.dispatchEvent(new Event("click"));
  f.window.history.pushState({ next: "detail" }, "", url);
}

test("order list restores after async content height becomes sufficient, once per navigation", () => {
  const f = fixture();
  const stop = f.start();
  const list = f.hook();
  list.render();
  f.window.scrollY = 950;
  leaveList(f);
  list.unmount();
  f.traverse(-1);
  f.window.scrollY = 0;
  f.resize(600);
  const restored = f.hook();
  restored.render({ ready: false });
  f.frame(); f.frame();
  assert.deepEqual(f.window.moves, []);
  restored.render({ ready: true });
  f.frame(); f.frame();
  assert.deepEqual(f.window.moves, []);
  f.resize(2400); f.frame();
  assert.deepEqual(f.window.moves, [950]);
  f.resize(3000); f.frame();
  assert.deepEqual(f.window.moves, [950]);
  assert.equal(f.observers.size, 0);
  assert.equal(f.timers.size, 0);
  restored.unmount(); stop();
});

test("native restoration is retained without a redundant scrollTo", () => {
  const f = fixture(); f.start();
  const list = f.hook(); list.render();
  f.window.scrollY = 800; leaveList(f); list.unmount();
  f.traverse(-1);
  const restored = f.hook(); restored.render(); f.frame(); f.frame();
  assert.deepEqual(f.window.moves, []);
  assert.equal(f.window.history.scrollRestoration, "auto");
});

test("a scrollable container restores independently of the document", () => {
  const f = fixture(); f.start();
  const moves = [];
  const container = { scrollTop: 0, scrollLeft: 0, scrollHeight: 1600, clientHeight: 500,
    firstElementChild: {}, scrollTo(point) { this.scrollTop = point.top; this.scrollLeft = point.left; moves.push(point.top); } };
  const ref = { current: container };
  const list = f.hook(); list.render({ ready: true, container: ref });
  container.scrollTop = 700;
  leaveList(f); list.unmount(); f.traverse(-1);
  container.scrollTop = 0;
  const restored = f.hook(); restored.render({ ready: true, container: ref });
  f.frame(); f.frame();
  assert.deepEqual(moves, [700]);
  assert.deepEqual(f.window.moves, []);
});

test("scroll events are frame-coalesced without notifying React navigation subscribers", () => {
  const f = fixture(); f.start();
  const list = f.hook(); list.render();
  let notifications = 0;
  const unsubscribe = f.utility.subscribeScrollNavigation(() => notifications++);
  f.window.scrollY = 600;
  for (let i = 0; i < 20; i++) f.window.dispatchEvent(new Event("scroll"));
  f.frame();
  assert.equal(f.utility.getScrollPosition(f.utility.getScrollEntry().id).y, 600);
  assert.equal(notifications, 0);
  unsubscribe();
});

test("back and forward revisits preserve different entries with identical URLs", () => {
  const f = fixture(); f.start();
  const first = f.utility.getScrollEntry();
  f.utility.saveScrollPosition(first.id, first.url, "", 0, 400);
  f.window.history.pushState({}, "", first.url);
  const second = f.utility.getScrollEntry();
  assert.notEqual(first.id, second.id);
  f.utility.saveScrollPosition(second.id, second.url, "", 0, 1000);
  f.traverse(-1);
  assert.equal(f.utility.getScrollPosition(f.utility.getScrollEntry().id).y, 400);
  f.traverse(1);
  assert.equal(f.utility.getScrollPosition(f.utility.getScrollEntry().id).y, 1000);
  f.traverse(-1);
  assert.equal(f.utility.getScrollPosition(f.utility.getScrollEntry().id).y, 400);
});

test("new filters, pagination and a fresh menu visit start at the top", () => {
  for (const url of ["/my/orders?page=3", "/admin/orders?keyword=gift&status=PAID", "/seller/orders?status=DELIVERED&page=2", "/my/orders?page=2"]) {
    const f = fixture(); f.start();
    f.window.scrollY = 900;
    f.window.history.pushState({}, "", url);
    const page = f.hook(); page.render(); f.frame(); f.frame();
    assert.deepEqual(f.window.moves, [0], url);
  }
});

test("a local pagination change waits for new data before resetting", () => {
  const f = fixture({ path: "/seller/products" }); f.start();
  const list = f.hook(); list.render({ ready: true, state: { page: 0, status: "ALL" } });
  f.window.scrollY = 900;
  list.render({ ready: false, state: { page: 1, status: "ALL" } });
  f.frame(); f.frame();
  assert.deepEqual(f.window.moves, []);
  list.render({ ready: true, state: { page: 1, status: "ALL" } });
  f.frame(); f.frame();
  assert.deepEqual(f.window.moves, [0]);
  for (const page of [0, 1]) {
    f.window.scrollY = 900;
    list.render({ ready: false, state: { page, status: "ALL" } });
    list.render({ ready: true, state: { page, status: "ALL" } });
    f.frame(); f.frame();
  }
  assert.deepEqual(f.window.moves, [0, 0, 0]);
});

test("local seller product/settlement view state is restored only on history traversal", () => {
  for (const path of ["/seller/products", "/seller/settlements", "/admin/seller-applications", "/notifications", "/seller/notifications", "/admin/notifications"]) {
    const f = fixture({ path }); f.start();
    const list = f.hook(); list.render({ ready: true, state: { page: 3, status: "ALL" } });
    f.window.scrollY = 700;
    leaveList(f, path + "/42"); list.unmount(); f.traverse(-1);
    assert.equal(f.utility.getRestoredScrollState({ page: 0, status: "ALL" }).page, 3);
    f.window.history.pushState({}, "", path);
    assert.equal(f.utility.getRestoredScrollState({ page: 0, status: "ALL" }).page, 0);
  }
});

test("loading failure does not create a polling loop or overwrite the saved position", () => {
  const f = fixture(); f.start();
  const original = f.utility.getScrollEntry();
  f.utility.saveScrollPosition(original.id, original.url, "", 0, 900);
  leaveList(f); f.traverse(-1);
  const page = f.hook(); page.render({ ready: false, failed: true });
  assert.equal(f.timers.size, 0);
  assert.equal(f.observers.size, 0);
  assert.equal(f.utility.getScrollPosition(original.id).y, 900);
});

test("a bounded layout timeout clamps the position and stops all observers", () => {
  const f = fixture(); f.start();
  const original = f.utility.getScrollEntry();
  f.utility.saveScrollPosition(original.id, original.url, "", 0, 900);
  leaveList(f); f.traverse(-1); f.resize(800);
  const page = f.hook(); page.render(); f.frame(); f.frame();
  f.timeout();
  assert.deepEqual(f.window.moves, [200]);
  f.resize(2500); f.frame();
  assert.deepEqual(f.window.moves, [200]);
  assert.equal(f.observers.size, 0);
});

test("user input cancels a pending restoration", () => {
  const f = fixture(); f.start();
  const original = f.utility.getScrollEntry();
  f.utility.saveScrollPosition(original.id, original.url, "", 0, 900);
  leaveList(f); f.traverse(-1); f.resize(600);
  const page = f.hook(); page.render(); f.frame(); f.frame();
  f.window.dispatchEvent(new Event("touchstart"));
  f.resize(2500); f.frame(); f.timeout();
  assert.deepEqual(f.window.moves, []);
});

test("Strict Mode cleanup avoids duplicate listeners and duplicate restoration", () => {
  const f = fixture();
  const first = f.start(); first();
  assert.equal(f.window.listenerCount + f.document.listenerCount, 0);
  const second = f.start();
  const original = f.utility.getScrollEntry();
  f.utility.saveScrollPosition(original.id, original.url, "", 0, 600);
  leaveList(f); f.traverse(-1);
  const discarded = f.hook(); discarded.render(); discarded.unmount();
  const mounted = f.hook(); mounted.render(); f.frame(); f.frame();
  assert.deepEqual(f.window.moves, [600]);
  const duplicate = f.hook(); duplicate.render(); f.frame(); f.frame();
  assert.deepEqual(f.window.moves, [600]);
  duplicate.unmount(); mounted.unmount(); second();
  assert.equal(f.window.listenerCount + f.document.listenerCount, 0);
});

test("reload and BFCache pageshow leave native restoration in control", () => {
  const f = fixture({ navigation: "reload" }); f.start();
  const list = f.hook(); list.render(); f.frame(); f.frame();
  const event = new Event("pageshow"); Object.defineProperty(event, "persisted", { value: true });
  f.window.dispatchEvent(event); list.render(); f.frame(); f.frame();
  assert.deepEqual(f.window.moves, []);
});

test("hash navigation cancels a pending fallback and retains native anchor scrolling", () => {
  const f = fixture(); f.start();
  const original = f.utility.getScrollEntry();
  f.utility.saveScrollPosition(original.id, original.url, "", 0, 900);
  leaveList(f); f.traverse(-1); f.resize(600);
  const page = f.hook(); page.render(); f.frame(); f.frame();
  f.window.location.hash = "items";
  f.window.dispatchEvent(new Event("hashchange"));
  page.render(); f.resize(2500); f.frame(); f.timeout();
  assert.deepEqual(f.window.moves, []);
});

test("cleanup deactivates a history wrapper retained by another browser integration", () => {
  const f = fixture(); const stop = f.start();
  const wrapped = f.window.history.pushState;
  f.window.history.pushState = function (...args) { wrapped.apply(this, args); };
  const previous = f.utility.getScrollEntry();
  stop();
  f.window.history.pushState({ other: "preserved" }, "", "/admin/orders");
  assert.deepEqual(f.window.history.state, { other: "preserved" });
  assert.equal(f.utility.getScrollEntry(), previous);
  assert.equal(f.window.listenerCount + f.document.listenerCount, 0);
});

test("checkout, payment callbacks and exchange shipping payments are excluded", () => {
  for (const path of ["/order?paymentId=70", "/payment/success", "/payment/fail", "/my/exchanges/payment/success"]) {
    const f = fixture({ path }); f.start();
    assert.equal(f.utility.getScrollEntry(), null);
    assert.deepEqual(f.window.history.state, { next: "preserved" });
    const page = f.hook(); page.render(); f.frame(); f.frame();
    assert.deepEqual(f.window.moves, []);
    assert.equal(f.window.history.scrollRestoration, "auto");
  }
});

test("storage is bounded, expires old entries and tolerates blocked storage", () => {
  const f = fixture(); const stop = f.start();
  for (let i = 0; i < 120; i++) f.utility.saveScrollPosition(`saved-${i}`, "/my/orders", "", 0, i);
  f.document.dispatchEvent(new Event("click"));
  assert.equal(Object.keys(JSON.parse(f.storage.get("gift-market-scroll-positions-v1"))).length, 100);
  f.advance(25 * 60 * 60 * 1000);
  assert.equal(f.utility.getScrollPosition("saved-119"), null);
  stop();
  const blocked = fixture({ storageBlocked: true }); const cleanup = blocked.start();
  blocked.window.history.pushState({}, "", "/my/orders?page=4");
  cleanup();
});

test("SSR utilities work without browser globals", () => {
  const utility = evaluate("lib/scroll-restoration.ts", {});
  assert.equal(utility.getScrollPosition("missing"), null);
  assert.equal(utility.getRestoredScrollState({ page: 0 }).page, 0);
  utility.saveScrollPosition("ignored", "/", "", 0, 0);
  utility.startScrollHistoryTracking()();
});

test("the existing product fallback remains available on back/forward, without affecting new menu visits", () => {
  const source = readFileSync(new URL("../app/products/page.tsx", import.meta.url), "utf8");
  const tree = ts.createSourceFile("products.tsx", source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  function find(node, predicate) {
    if (predicate(node)) return node;
    return ts.forEachChild(node, (child) => find(child, predicate));
  }
  const effect = find(tree, (node) => ts.isCallExpression(node)
    && node.expression.getText(tree) === "useEffect"
    && node.arguments[0]?.getText(tree).includes("productListScrollPositions.get(productListLocationKey)"));
  for (const kind of ["traverse", "navigate", "initial", "reload", "native"]) {
    const position = { scrollY: 900, productId: 48 };
    const pending = { current: null };
    const exports = {};
    vm.runInNewContext(ts.transpileModule(`(${effect.arguments[0].getText(tree)})();`, {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    }).outputText, { exports, pendingScrollRestoreRef: pending,
      productListLocationKey: "products", productListScrollPositions: new Map([["products", position]]),
      getScrollEntry: () => ({ kind }) });
    assert.equal(pending.current, kind === "traverse" ? position : null);
  }
});
