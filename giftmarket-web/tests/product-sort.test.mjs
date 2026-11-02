import test from "node:test";
import assert from "node:assert/strict";
import { parseProductSort, setProductSort } from "../lib/product-sort.ts";

test("omitted search sort stays omitted across filter and page URLs", () => {
  const params = new URLSearchParams("keyword=선물&categoryIds=3&minPrice=1000&page=2");
  setProductSort(params, parseProductSort(params.get("sort")));
  assert.equal(params.has("sort"), false);
  assert.equal(params.get("page"), "2");
  assert.equal(params.get("categoryIds"), "3");
  assert.equal(params.get("minPrice"), "1000");
});

test("explicit latest and other sorts survive URL reload", () => {
  for (const sort of ["LATEST", "POPULAR", "PRICE_ASC", "PRICE_DESC"]) {
    const params = new URLSearchParams("keyword=선물&page=1");
    setProductSort(params, sort);
    const reloaded = new URLSearchParams(params.toString());
    assert.equal(parseProductSort(reloaded.get("sort")), sort);
  }
  assert.equal(parseProductSort("invalid"), undefined);
});
