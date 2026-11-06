// Read public HTML/chunks only. Never accesses local credentials or deployment environment variables.
const site = process.argv[2] ?? "https://gift-market-test.vercel.app";
const response = await fetch(site, { cache: "no-store" });
if (!response.ok) throw new Error(`Homepage HTTP ${response.status}`);
const html = await response.text();
const scripts = [...html.matchAll(/<script[^>]+src="([^"]+)"/g)].map((match) => match[1]);
console.log(JSON.stringify({ site: response.url, status: response.status, cache: response.headers.get("x-vercel-cache") }));
let found = false;
for (const script of scripts) {
  const chunk = await fetch(new URL(script, response.url));
  if (!chunk.ok) throw new Error(`Chunk HTTP ${chunk.status}`);
  const source = await chunk.text();
  if (!source.includes("search-suggestions")) continue;
  found = true;
  console.log(JSON.stringify({
    chunk: script,
    productMarkup: source.includes("search-suggestion-placeholder"),
    oldLoadingMessage: source.includes("추천 상품을 찾고 있습니다."),
    keywordLoadingMessage: source.includes("추천 검색어를 찾고 있습니다."),
  }));
}
if (!found) throw new Error("SearchBox chunk not found; inspect deployment manually.");
