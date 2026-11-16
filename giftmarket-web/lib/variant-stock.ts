export function createVariantStockUpdate(
  id: number | null,
  stockQuantity: number,
  originalStockQuantity?: number,
  version?: number,
): { stockQuantity?: number; expectedVersion?: number } {
  if (id === null) return { stockQuantity };
  if (stockQuantity === originalStockQuantity) return {};
  if (version === undefined || originalStockQuantity === undefined) {
    throw new Error("재고 변경 전 최신 상품·재고를 다시 불러와주세요.");
  }
  return { stockQuantity, expectedVersion: version };
}
