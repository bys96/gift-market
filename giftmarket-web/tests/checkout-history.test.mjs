import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";

const require = createRequire(import.meta.url);
const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
function evaluate(source, context = {}) {
  const exports = {};
  vm.runInNewContext(ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, { exports, URL, ...context });
  return exports;
}
function browser() {
  const values = new Map();
  const window = new EventTarget();
  window.location = { href: "https://shop.example/order?productId=48&quantity=1", reload: () => { window.reloads++; } };
  window.reloads = 0;
  window.history = { state: {}, replaceState: (_state, _title, path) => {
    window.location.href = new URL(path, window.location.href).href;
  } };
  window.sessionStorage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
  return window;
}
const preparation = { orderId: 56, paymentId: 70, merchantPaymentId: "test-order-70", amount: 25000 };
const tick = () => new Promise(setImmediate);

test("the checkout reference survives successful session cleanup and refresh", () => {
  const window = browser();
  const sessions = evaluate(read("lib/payment-session.ts"), { window });
  sessions.saveCheckoutPreparation(preparation);
  sessions.savePaymentSession(preparation, "test-customer", sessions.checkoutReturnPath(70));
  assert.equal(sessions.checkoutReturnPath(70), "/order?productId=48&quantity=1&paymentId=70");
  sessions.clearCompletedPaymentSession("test-order-70");
  assert.equal(sessions.getPaymentSession("test-order-70"), null);
  const restored = evaluate(read("lib/payment-session.ts"), { window });
  assert.equal(restored.getCheckoutPreparation(70).orderId, 56);
  assert.equal(restored.getCheckoutPreparation(71), null);
});

test("BFCache and history restore reload a payment checkout without blocking navigation", () => {
  const window = browser();
  const sessions = evaluate(read("lib/payment-session.ts"), { window });
  const cleanup = sessions.observeCheckoutRestoration();
  const show = (persisted) => {
    const event = new Event("pageshow");
    Object.defineProperty(event, "persisted", { value: persisted });
    window.dispatchEvent(event);
  };
  show(true);
  assert.equal(window.reloads, 0);
  window.location.href += "&paymentId=70";
  show(false);
  assert.equal(window.reloads, 0);
  show(true);
  window.dispatchEvent(new Event("popstate"));
  show(true);
  assert.equal(window.reloads, 3);
  window.location.href = "https://shop.example/products/48";
  window.dispatchEvent(new Event("popstate"));
  assert.equal(window.reloads, 3);
  cleanup();
  window.location.href = "https://shop.example/order?paymentId=70";
  show(true);
  assert.equal(window.reloads, 3);
});

const tree = ts.createSourceFile("order.tsx", read("app/order/page.tsx"), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
function find(node, predicate) {
  if (predicate(node)) return node;
  return ts.forEachChild(node, (child) => find(child, predicate));
}
const restorationEffect = find(tree, (node) => ts.isCallExpression(node)
  && node.expression.getText(tree) === "useEffect"
  && node.arguments[0]?.getText(tree).includes("getCheckoutPreparation(checkoutPaymentId)"));

test("checkout re-entry uses server payment state before restoring an unpaid preparation", async () => {
  for (const status of ["PAID", "PARTIALLY_CANCELED", "CANCELING", "CANCELED", "CONFIRMING", "FAILED", "EXPIRED", "READY"]) {
    const navigations = [];
    const restored = [];
    const window = browser();
    window.location.href += "&paymentId=70";
    const context = {
      authInitialized: true, isAuthenticated: true, user: { id: 1 }, checkoutPaymentId: 70, verifiedPaymentId: null,
      getPayment: async () => ({ status, orderId: 56, amount: 25000 }),
      getCheckoutPreparation: () => preparation,
      getPaymentSession: () => ({ paymentId: 70 }),
      window, checkoutReturnPath: () => "/order?productId=48&quantity=1", clearCompletedPaymentSession: () => {},
      router: { replace: (path) => navigations.push(path) },
      setPreparedOrder: (value) => { if (value) restored.push(value); },
      setPaymentSession: () => {}, setVerifiedPaymentId: () => {},
      setPreparedDeliveryFingerprint: () => {}, setErrorMessage: () => {},
      setPaymentCheckError: (message) => assert.fail(message),
    };
    evaluate(`(${restorationEffect.arguments[0].getText(tree)})();`, context);
    await tick();
    assert.equal(restored.length, status === "READY" ? 1 : 0, status);
    assert.deepEqual(navigations, ["READY", "FAILED", "EXPIRED"].includes(status) ? [] : ["/my/orders/56"]);
    if (["FAILED", "EXPIRED"].includes(status)) {
      assert.equal(new URL(window.location.href).searchParams.has("paymentId"), false);
    }
  }
});

test("lookup failure blocks checkout instead of treating the old order as a new order", async () => {
  let errors = 0;
  evaluate(`(${restorationEffect.arguments[0].getText(tree)})();`, {
    authInitialized: true, isAuthenticated: true, user: {}, checkoutPaymentId: 70, verifiedPaymentId: null,
    getPayment: async () => { throw new Error("offline"); },
    setPaymentCheckError: () => errors++,
    setPreparedOrder: () => assert.fail("must not restore checkout"),
    router: { replace: () => assert.fail("must not guess a destination") },
  });
  await tick();
  assert.equal(errors, 1);
});

const submit = find(tree, (node) => ts.isVariableDeclaration(node) && node.name.getText(tree) === "handleSubmit").initializer.getText(tree);
test("completed payment is not launched again, while a canceled or failed PG attempt can retry its READY order", async () => {
  const scenarios = [
    ["PAID", true], ["PARTIALLY_CANCELED", true], ["CANCELED", true],
    ["CANCELING", true], ["CONFIRMING", true], ["READY", true], ["READY", false],
  ];
  for (const [status, snapshotMatches] of scenarios) {
    let launched = 0;
    const navigations = [];
    const recipient = { name: "테스트", phone: "01000000000", postalCode: "00000", address: "테스트 주소", addressDetail: "" };
    const fingerprint = JSON.stringify({ recipientName: recipient.name, recipientPhone: recipient.phone,
      postalCode: recipient.postalCode, address: recipient.address, addressDetail: null });
    const context = {
      process: { env: { NEXT_PUBLIC_TOSS_CLIENT_KEY: "test-client" } },
      submittingRef: { current: false }, checkoutPaymentId: 70, verifiedPaymentId: 70,
      validateOrder: () => true, setErrorMessage: () => {}, setIsSubmitting: () => {}, setSubmitStage: () => {},
      preparedOrder: preparation, moneyError: null, addressMode: "saved", makeSelectedDefault: false,
      getPayment: async () => ({ status }), clearCompletedPaymentSession: () => {},
      router: { replace: (path) => navigations.push(path) },
      paymentLauncher: async () => { launched++; }, paymentSession: { paymentId: 70 },
      recipient, isDirectOrder: false, requestedCartItemIds: [1], preparedDeliveryFingerprint: fingerprint,
      cartOrderItems: [], productAmount: 25000, shippingFee: 0, totalAmount: 25000,
      getMyOrder: async () => ({}), hasMatchingOrderSnapshot: () => snapshotMatches,
      createOrder: () => assert.fail("must not create another order"),
    };
    await evaluate(`exports.run = ${submit};`, context).run();
    await tick();
    assert.equal(launched, status === "READY" && snapshotMatches ? 1 : 0, status);
    const showExistingOrder = ["PAID", "PARTIALLY_CANCELED", "CANCELED", "CANCELING"].includes(status)
      || (status === "READY" && !snapshotMatches);
    assert.deepEqual(navigations, showExistingOrder ? ["/my/orders/56"] : []);
  }
});

test("widget cleanup destroys late agreement rendering and rejects a disposed launcher", async () => {
  for (const lateAgreement of [false, true]) {
    let cleanup;
    let release;
    let launcher;
    let methodDestroyed = 0;
    let agreementDestroyed = 0;
    let opened = 0;
    const widgets = {
      setAmount: async () => {},
      renderPaymentMethods: async () => ({ destroy: () => methodDestroyed++ }),
      renderAgreement: async () => {
        if (lateAgreement) await new Promise((resolve) => { release = resolve; });
        return { destroy: () => agreementDestroyed++ };
      },
      requestPayment: async () => { opened++; },
    };
    const component = evaluate(read("components/payment/TossPaymentWidget.tsx"), {
      process: { env: { NEXT_PUBLIC_TOSS_CLIENT_KEY: "test-client" } },
      require: (name) => {
        if (name === "react") return { useEffect: (effect) => { cleanup = effect(); } };
        if (name === "@/lib/toss-payment") return { loadTossWidgets: async () => widgets };
        if (name === "@/lib/money") return { validatePaymentAmount: (value) => value };
        return require(name);
      },
    }).default;
    component({ amount: 25000, customerKey: "test-customer", onReady: (value) => { if (value) launcher = value; }, onLoadingChange: () => {}, onError: assert.fail });
    await tick();
    cleanup();
    if (lateAgreement) release();
    await tick();
    assert.equal(methodDestroyed, 1);
    assert.equal(agreementDestroyed, 1);
    if (launcher) await assert.rejects(launcher({ amount: 25000 }));
    assert.equal(opened, 0);
  }
});
