import test from "node:test";
import assert from "node:assert/strict";
import { scheduleSuggestionRequest, moveSuggestionIndex } from "../lib/suggestion-request.ts";

test("debouncing cancels an earlier query before it is sent", async () => {
  const sent = [];
  const results = [];
  const request = async (query) => { sent.push(query); return query; };
  const cancel = scheduleSuggestionRequest("초", request, () => {}, (value) => results.push(value), () => {});
  cancel();
  scheduleSuggestionRequest("초콜릿", request, () => {}, (value) => results.push(value), () => {});
  await new Promise((resolve) => setTimeout(resolve, 320));
  assert.deepEqual(sent, ["초콜릿"]);
  assert.deepEqual(results, ["초콜릿"]);
});

test("an aborted in-flight request cannot update the result", async () => {
  let resolveRequest;
  let signal;
  const results = [];
  const cancel = scheduleSuggestionRequest("선물", (_query, requestSignal) => {
    signal = requestSignal;
    return new Promise((resolve) => { resolveRequest = resolve; });
  }, () => {}, (value) => results.push(value), () => {});
  await new Promise((resolve) => setTimeout(resolve, 300));
  cancel();
  resolveRequest("old");
  await Promise.resolve();
  assert.equal(signal.aborted, true);
  assert.deepEqual(results, []);
});

test("keyboard navigation wraps in both directions", () => {
  assert.equal(moveSuggestionIndex(-1, 3, 1), 0);
  assert.equal(moveSuggestionIndex(2, 3, 1), 0);
  assert.equal(moveSuggestionIndex(0, 3, -1), 2);
  assert.equal(moveSuggestionIndex(-1, 0, 1), -1);
});

test("침구 → 침구류 → 침구 ignores earlier success and failure even for the same query", async () => {
  const pending = [];
  const results = [];
  const errors = [];
  const request = (query, signal) => new Promise((resolve, reject) => pending.push({ query, signal, resolve, reject }));
  const start = (query) => scheduleSuggestionRequest(query, request, () => {},
    (value) => results.push(value), () => errors.push(query));
  const first = start("침구");
  await new Promise((resolve) => setTimeout(resolve, 300));
  first();
  const second = start("침구류");
  await new Promise((resolve) => setTimeout(resolve, 300));
  second();
  const third = start("침구");
  await new Promise((resolve) => setTimeout(resolve, 300));
  pending[2].resolve(["최신 추천 검색어"]);
  pending[0].resolve(["이전 침구 결과"]);
  pending[1].reject(new Error("old request failed"));
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.deepEqual(results, [["최신 추천 검색어"]]);
  assert.deepEqual(errors, []);
  assert.deepEqual(pending.map(({ query }) => query), ["침구", "침구류", "침구"]);
  third();
});
