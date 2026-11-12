import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";

const token = (seconds = 3600) => `header.${Buffer.from(JSON.stringify({ exp: Date.now() / 1000 + seconds })).toString("base64url")}.signature`;
const json = (data, status = 200) => new Response(JSON.stringify(data), { status });
const refreshed = () => json({ success: true, data: { accessToken: token() } });

function evaluate(path, context) {
  const exports = {};
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(path, import.meta.url), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText, { exports, ...context });
  return exports;
}

function client(fetch, accessToken = token(-3600), clock = Date) {
  const state = {
    accessToken, initialized: true, isAuthenticated: true,
    setAccessToken(value) { this.accessToken = value; this.isAuthenticated = true; },
    clearAuth() { this.accessToken = null; this.isAuthenticated = false; },
  };
  const api = evaluate("../lib/api.ts", {
    require: () => ({ useAuthStore: { getState: () => state } }),
    process: { env: {} }, fetch, Headers, AbortSignal, atob, Error, TypeError, Date: clock,
  });
  return { api, state };
}

test("sleep resume recovers an expired access token without replaying business requests", async () => {
  const calls = [];
  const { api, state } = client(async (url) => { calls.push(url); return refreshed(); });
  await api.recoverAuthSession();
  assert.deepEqual(calls, ["/api/auth/token"]);
  assert.equal(api.isAccessTokenExpired(state.accessToken), false);
  await api.recoverAuthSession();
  assert.equal(calls.length, 1);
});

test("network refresh failure stays latched for requests and is recoverable on a later resume", async () => {
  let calls = 0;
  const { api, state } = client(async () => {
    calls++;
    if (calls === 1) throw new TypeError("offline");
    return refreshed();
  });
  await assert.rejects(api.refreshAccessToken(), api.ApiNetworkError);
  await assert.rejects(api.refreshAccessToken(), api.ApiNetworkError);
  assert.equal(calls, 1);
  assert.equal(state.isAuthenticated, true);
  await api.recoverAuthSession();
  assert.equal(calls, 2);
  assert.equal(api.isAccessTokenExpired(state.accessToken), false);
});

test("concurrent recovery and expired-token requests share one refresh", async () => {
  let release;
  let refreshes = 0;
  const { api } = client(async (url) => {
    if (url === "/api/auth/token") {
      refreshes++;
      await new Promise((resolve) => { release = resolve; });
      return refreshed();
    }
    return json({ success: true, data: {} });
  });
  const requests = [api.recoverAuthSession(), api.apiFetch("/api/orders"), api.apiFetch("/api/cart")];
  release();
  await Promise.all(requests);
  assert.equal(refreshes, 1);
});

test("expired refresh session clears authentication while a PG-independent network error preserves it", async () => {
  const expired = client(async () => json({}, 401));
  await expired.api.recoverAuthSession();
  assert.equal(expired.state.isAuthenticated, false);
  for (const status of [502, 503, 504]) {
    const transient = client(async () => json({}, status));
    await assert.rejects(transient.api.recoverAuthSession(), transient.api.ApiError);
    assert.equal(transient.state.isAuthenticated, true);
  }
});

test("write APIs refresh before sending an expired token and send the request only once", async () => {
  for (const method of ["POST", "PATCH", "PUT", "DELETE"]) {
    const calls = [];
    const { api } = client(async (url, options) => {
      calls.push([url, options.method]);
      return url === "/api/auth/token" ? refreshed() : json({ success: true, data: {} });
    });
    await api.apiFetch("/api/orders/1/cancel", { method });
    assert.deepEqual(calls, [["/api/auth/token", "POST"], ["/api/orders/1/cancel", method]]);
  }
});

test("failed authentication preflight never sends an order or payment mutation", async () => {
  const calls = [];
  const { api } = client(async (url) => {
    calls.push(url);
    throw new TypeError("offline");
  });
  await assert.rejects(api.apiFetch("/api/orders", { method: "POST" }), api.ApiNetworkError);
  assert.deepEqual(calls, ["/api/auth/token"]);
});

test("a later user request can recover a latched failure even when no resume event fires", async () => {
  let now = Date.now();
  const calls = [];
  const { api } = client(async (url) => {
    calls.push(url);
    if (calls.length === 1) throw new TypeError("offline");
    return url === "/api/auth/token" ? refreshed() : json({ success: true });
  }, token(-3600), { now: () => now });
  await assert.rejects(api.apiFetch("/api/orders", { method: "POST" }), api.ApiNetworkError);
  await assert.rejects(api.apiFetch("/api/orders", { method: "POST" }), api.ApiNetworkError);
  assert.equal(calls.length, 1);
  now += 30_000;
  await api.apiFetch("/api/orders", { method: "POST" });
  assert.deepEqual(calls, ["/api/auth/token", "/api/auth/token", "/api/orders"]);
});

test("token timeout preserves the session and does not automatically retry", async () => {
  let calls = 0;
  const { api, state } = client(async () => {
    calls++;
    const error = new Error("timeout");
    error.name = "TimeoutError";
    throw error;
  });
  await assert.rejects(api.recoverAuthSession(), api.ApiNetworkError);
  assert.equal(calls, 1);
  assert.equal(state.isAuthenticated, true);
});

test("network response-body loss is reported without replaying a write", async () => {
  let calls = 0;
  const { api } = client(async () => {
    calls++;
    return { text: async () => { throw new TypeError("response lost"); } };
  }, token());
  await assert.rejects(api.apiFetch("/api/payments/confirm", { method: "POST" }), api.ApiNetworkError);
  assert.equal(calls, 1);
});

test("401 and response loss never automatically replay writes", async () => {
  for (const method of ["POST", "PATCH", "PUT", "DELETE"]) {
    for (const networkError of [false, true]) {
      let writes = 0;
      const { api } = client(async (url) => {
        if (url === "/api/auth/token") return refreshed();
        writes++;
        if (networkError) throw new TypeError("response lost");
        return json({}, 401);
      }, token());
      await assert.rejects(api.apiFetch("/api/payments/confirm", { method }));
      assert.equal(writes, 1);
    }
  }
});

test("GET 401 refreshes once and retries once, then reports an expired session", async () => {
  for (const failAgain of [false, true]) {
    let reads = 0;
    const { api, state } = client(async (url) => {
      if (url === "/api/auth/token") return refreshed();
      reads++;
      return reads === 1 || failAgain ? json({}, 401) : json({ success: true });
    }, token());
    if (failAgain) {
      await assert.rejects(api.apiFetch("/api/orders"), api.ApiError);
      assert.equal(state.isAuthenticated, false);
    } else await api.apiFetch("/api/orders");
    assert.equal(reads, 2);
  }
});

test("logout during refresh cannot restore the session", async () => {
  let release;
  const { api, state } = client(async () => {
    await new Promise((resolve) => { release = resolve; });
    return refreshed();
  });
  const pending = api.recoverAuthSession();
  api.invalidateAuthSession();
  state.clearAuth();
  release();
  await pending;
  assert.equal(state.isAuthenticated, false);
  assert.equal(state.accessToken, null);
});

test("visibility, pageshow and online events are debounced, throttled and cleaned up", async () => {
  const document = new EventTarget();
  document.visibilityState = "hidden";
  const window = new EventTarget();
  const navigator = { onLine: true };
  const timers = new Map();
  let nextId = 0;
  let now = 100_000;
  let calls = 0;
  const observer = evaluate("../lib/auth-recovery.ts", {
    require: () => ({ recoverAuthSession: async () => { calls++; } }),
    document, window, navigator, Date: { now: () => now },
    setTimeout: (callback, delay) => { const id = ++nextId; timers.set(id, { callback, delay }); return id; },
    clearTimeout: (id) => timers.delete(id),
  });
  const cleanup = observer.observeAuthRecovery();
  window.dispatchEvent(new Event("pageshow"));
  assert.equal(timers.size, 0);
  document.visibilityState = "visible";
  navigator.onLine = false;
  document.dispatchEvent(new Event("visibilitychange"));
  assert.equal(timers.size, 0);
  navigator.onLine = true;
  window.dispatchEvent(new Event("online"));
  document.dispatchEvent(new Event("visibilitychange"));
  window.dispatchEvent(new Event("pageshow"));
  assert.equal(timers.size, 1);
  const [{ callback }] = timers.values();
  timers.clear();
  callback();
  await new Promise(setImmediate);
  assert.equal(calls, 1);
  now += 1000;
  window.dispatchEvent(new Event("online"));
  assert.equal([...timers.values()][0].delay, 29_000);
  cleanup();
  assert.equal(timers.size, 0);
  window.dispatchEvent(new Event("online"));
  document.dispatchEvent(new Event("visibilitychange"));
  assert.equal(timers.size, 0);
});

test("online events during recovery are retained as one throttled follow-up", async () => {
  const document = new EventTarget();
  document.visibilityState = "visible";
  const window = new EventTarget();
  const timers = new Map();
  let release;
  let nextId = 0;
  const observer = evaluate("../lib/auth-recovery.ts", {
    require: () => ({ recoverAuthSession: () => new Promise((resolve) => { release = resolve; }) }),
    document, window, navigator: { onLine: true }, Date: { now: () => 100_000 },
    setTimeout: (callback, delay) => { const id = ++nextId; timers.set(id, { callback, delay }); return id; },
    clearTimeout: (id) => timers.delete(id),
  });
  const cleanup = observer.observeAuthRecovery();
  window.dispatchEvent(new Event("pageshow"));
  const [{ callback }] = timers.values();
  timers.clear();
  callback();
  window.dispatchEvent(new Event("online"));
  window.dispatchEvent(new Event("online"));
  assert.equal(timers.size, 0);
  release();
  await new Promise(setImmediate);
  assert.equal(timers.size, 1);
  assert.equal([...timers.values()][0].delay, 30_000);
  cleanup();
});
