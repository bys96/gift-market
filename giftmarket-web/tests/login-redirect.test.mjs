import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";

const root = resolve(import.meta.dirname, "..");
const read = (path) => readFileSync(resolve(root, path), "utf8");
const evaluate = (source, context = {}) => vm.runInNewContext(
  ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText,
  context,
);

function loginUrl(path) {
  const url = new URL(path, "https://shop.example");
  const context = { exports: {}, window: { location: url } };
  evaluate(read("lib/login-redirect.ts"), context);
  return context.exports.getLoginRedirectUrl();
}

function parse(path) {
  return ts.createSourceFile(path, read(path), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
}

function find(node, predicate) {
  if (predicate(node)) return node;
  return ts.forEachChild(node, (child) => find(child, predicate));
}

test("login URL preserves destination, query and hash through OAuth storage round trip", () => {
  for (const path of ["/cart", "/my", "/my/wishlist", "/my/orders?page=2", "/my/orders/42", "/my/profile", "/my/addresses", "/seller", "/seller/products/42/edit", "/admin/seller-applications?page=2", "/cart?item=1&item=2#items"]) {
    const redirect = new URL(loginUrl(path), "https://shop.example").searchParams.get("redirect");
    assert.equal(redirect, path);
    for (const file of ["app/login/page.tsx", "app/oauth/callback/page.tsx"]) {
      const tree = parse(file);
      const resolver = find(tree, (node) => ts.isFunctionDeclaration(node) && node.name?.text === "resolveRedirectUrl");
      assert.equal(evaluate(`const DEFAULT_REDIRECT_URL = "/"; ${resolver.getText(tree)}; resolveRedirectUrl(value);`, { value: redirect }), path);
    }
  }
});

test("login and OAuth callback reject external and browser-normalized external destinations", () => {
  for (const file of ["app/login/page.tsx", "app/oauth/callback/page.tsx"]) {
    const tree = parse(file);
    const resolver = find(tree, (node) => ts.isFunctionDeclaration(node) && node.name?.text === "resolveRedirectUrl");
    for (const value of [null, "", "https://evil.example", "//evil.example", "/\\evil.example", "/\t/evil.example", "/\n/evil.example", "javascript:alert(1)"]) {
      assert.equal(evaluate(`const DEFAULT_REDIRECT_URL = "/"; ${resolver.getText(tree)}; resolveRedirectUrl(value);`, { value }), "/", `${file}: ${JSON.stringify(value)}`);
    }
  }
});

test("OAuth callback delegates token refresh to the shared initializer exactly once", () => {
  const callback = read("app/oauth/callback/page.tsx");
  const initializer = read("components/auth/AuthInitializer.tsx");
  const authInitialization = read("lib/auth-initialization.ts");

  assert.equal((callback.match(/\/api\/auth\/token/g) ?? []).length, 0);
  assert.equal((initializer.match(/\/api\/auth\/token/g) ?? []).length, 0);
  assert.equal((authInitialization.match(/refreshAccessToken\(\)/g) ?? []).length, 1);
  assert.match(callback, /initializeAuth\(\)/);
  assert.match(initializer, /initializeAuth\(\)/);
  assert.match(authInitialization, /if \(initializationPromise\) return initializationPromise/);
});

test("token refresh distinguishes invalid sessions from transient failures without retrying token rotation", () => {
  const api = read("lib/api.ts");
  const authInitialization = read("lib/auth-initialization.ts");

  assert.match(api, /result\?\.success === true && result\.data === null/);
  assert.match(api, /if \(response\.status === 401\) return null/);
  assert.equal((api.match(/fetch\(`\$\{API_BASE_URL\}\/api\/auth\/token`/g) ?? []).length, 1);
  assert.match(authInitialization, /\[502, 503, 504\]/);
  assert.match(authInitialization, /attempt >= 1/);
  assert.match(authInitialization, /skipAuthRefresh: true/);
  assert.match(authInitialization, /auth\.setInitializationError/);
});

function pages(dir) {
  return readdirSync(resolve(root, dir), { withFileTypes: true }).flatMap((entry) =>
    entry.isDirectory() ? pages(`${dir}/${entry.name}`) : [`${dir}/${entry.name}`],
  );
}

test("protected page guards wait for initialization and retain destination only when logged out", () => {
  const files = [
    ...pages("app/seller"), "app/admin/(admin-center)/layout.tsx", "app/cart/page.tsx",
    "app/my/page.tsx", "app/my/profile/page.tsx", "app/my/addresses/page.tsx",
    "app/my/orders/page.tsx", "app/my/orders/[orderId]/page.tsx", "app/my/wishlist/page.tsx",
  ];
  for (const file of files) {
    if (!file.endsWith(".tsx")) continue;
    const tree = parse(file);
    const effect = find(tree, (node) => ts.isCallExpression(node) && node.expression.getText(tree) === "useEffect"
      && /getLoginRedirectUrl\(\)|login\?redirect=/.test(node.arguments[0]?.getText(tree) ?? ""));
    if (!effect) continue;
    // Execute the actual initialization/authentication branches without invoking API data effects.
    const branches = effect.arguments[0].body.statements.slice(0, 2).map((node) => node.getText(tree)).join("\n");
    for (const [initialized, authenticated, expected] of [[false, false, false], [false, true, false], [true, false, true], [true, true, false]]) {
      const calls = [];
      evaluate(`(() => { ${branches} })();`, {
        initialized, authInitialized: initialized, authenticated, isAuthenticated: authenticated,
        user: authenticated ? { role: "USER" } : null,
        router: { replace: (url) => calls.push(url) }, getLoginRedirectUrl: () => loginUrl("/cart"),
      });
      assert.equal(calls.length, expected ? 1 : 0, `${file}: initialized=${initialized}, authenticated=${authenticated}`);
      if (expected) assert.match(calls[0], /^\/login\?redirect=/);
    }
  }
});

test("mobile drawer link clicks close even when pathname does not change", () => {
  const tree = parse("components/layout/Header.tsx");
  const aside = find(tree, (node) => ts.isJsxOpeningElement(node) && node.tagName.getText(tree) === "aside");
  const handler = aside.attributes.properties.find((node) => node.name?.getText(tree) === "onClick").initializer.expression;
  class Element {
    constructor(link) { this.link = link; }
    closest() { return this.link; }
  }
  for (const [target, expected] of [[new Element(true), [false]], [new Element(false), []], [{}, []]]) {
    const states = [];
    evaluate(`(${handler.getText(tree)})({ target });`, { target, Element, setIsMobileMenuOpen: (state) => states.push(state) });
    assert.deepEqual(states, expected);
  }
});
