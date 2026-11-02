import type { ProductSort } from "../types/product";

const SORTS: readonly string[] = ["LATEST", "POPULAR", "PRICE_ASC", "PRICE_DESC"];

export function parseProductSort(value: string | null): ProductSort | undefined {
  return value !== null && SORTS.includes(value) ? value as ProductSort : undefined;
}

export function setProductSort(params: URLSearchParams, sort: ProductSort | null | undefined): void {
  if (sort) params.set("sort", sort);
  else params.delete("sort");
}
