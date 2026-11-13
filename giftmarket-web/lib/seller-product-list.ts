import type { ProductStatus } from "../types/product";

export const SELLER_PRODUCT_KEYWORD_MAX_LENGTH = 100;
const STATUSES: readonly string[] = ["ON_SALE", "SOLD_OUT", "DRAFT", "HIDDEN"];

export function parseSellerProductList(params: { get(name: string): string | null }) {
  const page = Number(params.get("page") ?? "0");
  const status = params.get("status");
  return {
    page: Number.isSafeInteger(page) && page >= 0 ? page : 0,
    status: status && STATUSES.includes(status) ? status as ProductStatus : "ALL" as const,
    keyword: params.get("keyword")?.trim() ?? "",
  };
}

export function sellerProductsHref(
  params: { toString(): string },
  changes: { keyword?: string; status?: ProductStatus | "ALL"; page?: number },
) {
  const next = new URLSearchParams(params.toString());
  if (changes.keyword !== undefined) {
    const keyword = changes.keyword.trim();
    if (keyword) next.set("keyword", keyword);
    else next.delete("keyword");
  }
  if (changes.status !== undefined) {
    if (changes.status === "ALL") next.delete("status");
    else next.set("status", changes.status);
  }
  if (changes.page !== undefined) {
    if (changes.page === 0) next.delete("page");
    else next.set("page", String(changes.page));
  }
  const query = next.toString();
  return query ? `/seller/products?${query}` : "/seller/products";
}
