import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

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

  assert.match(logout, /apiFetch\("\/api\/auth\/logout",\s*\{\s*method: "POST"/s);
  assert.match(logout, /finally\s*\{[\s\S]*invalidateAuthSession\(\)[\s\S]*clearAuth\(\)[\s\S]*resetCart\(\)[\s\S]*resetWishlist\(\)[\s\S]*router\.replace\("\/"\)/);
  assert.match(source, /if \(!isAuthenticated \|\| !user\)\s*\{\s*router\.replace\(getLoginRedirectUrl\(\)\)/);
});

test("logout invalidates an in-flight token refresh before it can restore auth state", () => {
  assert.match(api, /export function invalidateAuthSession\(\): void/);
  assert.match(api, /if \(sessionVersion !== authSessionVersion\) return null;/);
});
