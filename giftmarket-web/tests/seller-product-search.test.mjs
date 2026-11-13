import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";
import { parseSellerProductList, sellerProductsHref, SELLER_PRODUCT_KEYWORD_MAX_LENGTH } from "../lib/seller-product-list.ts";

const require = createRequire(import.meta.url);
const source = readFileSync(new URL("../app/seller/(seller-center)/products/page.tsx", import.meta.url), "utf8");
const tree = ts.createSourceFile("products.tsx", source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
function find(node, predicate) {
  if (predicate(node)) return node;
  return ts.forEachChild(node, (child) => find(child, predicate));
}
function evaluate(source, context = {}) {
  const exports = {};
  vm.runInNewContext(ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, { exports, URLSearchParams, require, ...context });
  return exports;
}

test("search resets the page while preserving status, sort and unrelated URL parameters", () => {
  const params = new URLSearchParams("page=4&status=ON_SALE&sort=LATEST&source=menu");
  const url = new URL(sellerProductsHref(params, { keyword: "  디퓨저  ", page: 0 }), "https://shop.example");
  assert.equal(url.searchParams.get("keyword"), "디퓨저");
  assert.equal(url.searchParams.has("page"), false);
  assert.equal(url.searchParams.get("status"), "ON_SALE");
  assert.equal(url.searchParams.get("sort"), "LATEST");
  assert.equal(url.searchParams.get("source"), "menu");
  assert.equal(params.get("page"), "4");
});

test("pagination, status changes and reset retain the other search conditions", () => {
  const params = new URLSearchParams("keyword=디퓨저&status=HIDDEN&page=2");
  const next = new URL(sellerProductsHref(params, { page: 3 }), "https://shop.example");
  assert.deepEqual(parseSellerProductList(next.searchParams), { keyword: "디퓨저", status: "HIDDEN", page: 3 });
  const status = new URL(sellerProductsHref(params, { status: "ON_SALE", page: 0 }), "https://shop.example");
  assert.equal(status.searchParams.get("keyword"), "디퓨저");
  const reset = new URL(sellerProductsHref(params, { keyword: "", page: 0 }), "https://shop.example");
  assert.equal(reset.searchParams.has("keyword"), false);
  assert.equal(reset.searchParams.get("status"), "HIDDEN");
  assert.equal(reset.searchParams.has("page"), false);
});

test("URL state survives refresh and back from editing, with safe defaults for malformed URLs", () => {
  const url = "https://shop.example/seller/products?keyword=%EB%94%94%ED%93%A8%EC%A0%80&status=SOLD_OUT&page=2";
  for (let i = 0; i < 3; i++) {
    assert.deepEqual(parseSellerProductList(new URL(url).searchParams), { keyword: "디퓨저", status: "SOLD_OUT", page: 2 });
  }
  assert.deepEqual(parseSellerProductList(new URLSearchParams("page=-1&status=INVALID&keyword=++")),
    { keyword: "", status: "ALL", page: 0 });
  assert.equal(SELLER_PRODUCT_KEYWORD_MAX_LENGTH, 100);
});

test("search button and Enter share form submission, and reset clears both the draft and query", () => {
  const component = find(tree, (node) => ts.isFunctionDeclaration(node) && node.name?.text === "SellerProductSearch");
  let value;
  const calls = [];
  const { Search } = evaluate(`${component.getText(tree)}; exports.Search = SellerProductSearch;`, {
    useState: (initial) => [value ?? initial, (next) => { value = next; }],
    SELLER_PRODUCT_KEYWORD_MAX_LENGTH,
  });
  let form = Search({ keyword: "디퓨저", onSearch: (keyword) => calls.push(keyword) });
  const input = form.props.children[0].props.children[1];
  assert.equal(input.props.placeholder, "상품명으로 검색");
  assert.equal(input.props.maxLength, 100);
  input.props.onChange({ target: { value: "  머스크  " } });
  assert.equal(calls.length, 0);
  form = Search({ keyword: "디퓨저", onSearch: (keyword) => calls.push(keyword) });
  assert.equal(form.props.children[1].props.type, "submit");
  let prevented = false;
  form.props.onSubmit({ preventDefault: () => { prevented = true; } });
  assert.equal(prevented, true);
  assert.deepEqual(calls, ["머스크"]);
  form.props.children[2].props.onClick();
  assert.equal(value, "");
  assert.deepEqual(calls, ["머스크", ""]);
});

test("seller API transports literal wildcard text and keeps existing optional parameters", async () => {
  const apiSource = readFileSync(new URL("../lib/product-api.ts", import.meta.url), "utf8");
  const apiTree = ts.createSourceFile("api.ts", apiSource, ts.ScriptTarget.Latest, true);
  const fn = find(apiTree, (node) => ts.isFunctionDeclaration(node) && node.name?.text === "getSellerProducts");
  const calls = [];
  const api = evaluate(fn.getText(apiTree), {
    apiFetch: async (url) => { calls.push(url); return { data: { products: [], page: 2 } }; },
    normalizeSellerProductListItem: (item) => item,
  });
  await api.getSellerProducts(2, 20, "ON_SALE", "  100%_디퓨저  ");
  const params = new URL(calls[0], "https://shop.example").searchParams;
  assert.equal(params.get("keyword"), "100%_디퓨저");
  assert.equal(params.get("status"), "ON_SALE");
  assert.equal(params.has("sellerId"), false);
  assert.equal(params.get("page"), "2");
  await api.getSellerProducts();
  assert.equal(new URL(calls[1], "https://shop.example").searchParams.has("keyword"), false);
});

test("a slower prior search cannot overwrite the latest results or error state", async () => {
  const callback = find(tree, (node) => ts.isVariableDeclaration(node) && node.name.getText(tree) === "loadProducts").initializer.arguments[0].getText(tree);
  for (const lateError of [false, true]) {
    const productRequestId = { current: 0 };
    const pending = [];
    const results = [];
    const errors = [];
    const create = (keyword) => evaluate(`exports.load = ${callback};`, {
      productRequestId, currentPage: 0, selectedStatus: "ALL", keyword, PAGE_SIZE: 20,
      setIsLoading: () => {}, setErrorMessage: (value) => { if (value) errors.push(value); },
      setProductPage: (value) => results.push(value.id),
      getSellerProducts: () => new Promise((resolve, reject) => pending.push({ resolve, reject })),
    }).load;
    const first = create("디퓨저")();
    const second = create("머스크")();
    pending[1].resolve({ id: 2, totalPages: 1 });
    await second;
    if (lateError) pending[0].reject(new Error("old failure"));
    else pending[0].resolve({ id: 1, totalPages: 1 });
    await first;
    assert.deepEqual(results, [2]);
    assert.deepEqual(errors, []);
  }
});
