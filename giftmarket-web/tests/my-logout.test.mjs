import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";

const source = readFileSync(
  resolve(import.meta.dirname, "../app/my/page.tsx"),
  "utf8",
);
const api = readFileSync(resolve(import.meta.dirname, "../lib/api.ts"), "utf8");

test("my logout clears local session state and replaces the protected route with home", () => {
  const logout = source.slice(
    source.indexOf("const handleLogout"),
    source.indexOf("if (!user)"),
  );

  assert.match(logout, /logoutSession\(\)[\s\S]*resetCart\(\)[\s\S]*resetWishlist\(\)[\s\S]*router\.replace\("\/"\)/);
  assert.doesNotMatch(logout, /router\.refresh\(\)/);
  assert.doesNotMatch(logout, /setIsLoggingOut\(false\)/);
  assert.match(source, /if \(\(!isAuthenticated \|\| !user\) && !isLoggingOut\)\s*\{\s*router\.replace\(getLoginRedirectUrl\(\)\)/);
});

function browser(storage, fetch, pathname = "/", loginIntent = null) {
  const store = {
    accessToken: null,
    clearAuth() { this.accessToken = null; this.user = null; },
    setAccessToken(token) { this.accessToken = token; },
    setUser(user) { this.user = user; },
    setInitialized(value) { this.initialized = value; },
    setInitializationError(error) { this.error = error; },
  };
  const localStorage = {
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: (key) => storage.delete(key),
  };
  const window = Object.assign(new EventTarget(), { localStorage, location: { pathname } });
  const auth = { useAuthStore: { getState: () => store } };
  const evaluate = (code, require) => {
    const exports = {};
    const output = ts.transpileModule(code, {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    }).outputText;
    vm.runInNewContext(output, {
      exports, require, window, sessionStorage: { getItem: () => loginIntent },
      fetch, process: { env: {} }, Event, Headers, AbortSignal, TypeError,
    });
    return exports;
  };
  const client = evaluate(api, () => auth);
  const init = evaluate(readFileSync(new URL("../lib/auth-initialization.ts", import.meta.url), "utf8"),
    (name) => name === "@/lib/api" ? client : name === "@/stores/auth-store" ? auth : {
      useWishlistStore: { getState: () => ({ resetWishlist() {}, loadWishlist() {} }) },
    });
  return { ...client, ...init, store };
}

const success = (data = null) => Response.json({ success: true, data });

test("failed logout clears local auth and blocks refresh across reload and browser restart until retry succeeds", async () => {
  const storage = new Map();
  const offline = browser(storage, async () => { throw new TypeError("offline"); });
  offline.store.accessToken = "previous-access";
  assert.equal(await offline.logoutSession(), false);
  assert.equal(offline.store.accessToken, null);
  assert.equal(offline.hasPendingLogout(), true);

  const calls = [];
  let validCookie = true;
  const onlineFetch = async (url) => {
    calls.push(url);
    if (url === "/api/auth/logout") validCookie = false;
    return success(url === "/api/auth/token" && validCookie ? { accessToken: "rotated" } : null);
  };
  for (const pathname of ["/", "/my", "/oauth/callback"]) {
    const reopened = browser(storage, onlineFetch, pathname);
    await reopened.initializeAuth();
    assert.equal(reopened.store.initialized, true);
    assert.equal(reopened.store.accessToken, null);
  }
  assert.deepEqual(calls, []);
  const reopened = browser(storage, onlineFetch);
  const retry = reopened.logoutSession();
  assert.equal(reopened.logoutSession(), retry);
  assert.equal(await retry, true);
  assert.equal(reopened.hasPendingLogout(), false);
  await browser(storage, onlineFetch).initializeAuth();
  assert.deepEqual(calls, ["/api/auth/logout", "/api/auth/token"]);
});

test("explicit OAuth callback can initialize a new session after failed server logout", async () => {
  const storage = new Map();
  await browser(storage, async () => Response.json({ success: false }, { status: 503 })).logoutSession();
  const calls = [];
  const callback = browser(storage, async (url) => {
    calls.push(url);
    return success(url === "/api/auth/token" ? { accessToken: "new-access" } : { id: 42 });
  }, "/oauth/callback", "/");
  await callback.initializeAuth();
  assert.equal(callback.hasPendingLogout(), false);
  assert.equal(callback.store.user.id, 42);
  assert.deepEqual(calls, ["/api/auth/token", "/api/auth/me"]);
});

test("logout invalidates an in-flight token refresh before it can restore auth state", () => {
  assert.match(api, /export function invalidateAuthSession\(\): void/);
  assert.match(api, /if \(sessionVersion !== authSessionVersion\) return null;/);
});
