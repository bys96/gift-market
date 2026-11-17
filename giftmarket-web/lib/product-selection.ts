export interface PurchaseSelection { variantId: number | null; quantity: number }
interface SelectableVariant { id: number; available: boolean; stockQuantity: number; price: number }

export function addVariantSelection(items: PurchaseSelection[], variant: SelectableVariant): PurchaseSelection[] {
  if (!variant.available || variant.stockQuantity <= 0) return items;
  const existing = items.find(item => item.variantId === variant.id);
  return existing
    ? items.map(item => item.variantId === variant.id
      ? { ...item, quantity: Math.min(variant.stockQuantity, item.quantity + 1) } : item)
    : [...items, { variantId: variant.id, quantity: 1 }];
}

export function changeSelectionQuantity(items: PurchaseSelection[], variantId: number, delta: number, stock: number) {
  return items.map(item => item.variantId === variantId
    ? { ...item, quantity: Math.max(1, Math.min(stock, item.quantity + delta)) } : item);
}

export function selectionTotal(items: PurchaseSelection[], variants: SelectableVariant[]) {
  return items.reduce((total, item) => total + (variants.find(v => v.id === item.variantId)?.price ?? 0) * item.quantity, 0);
}

export function isDirectPurchaseUnavailable(
  product: { status: string; hasOptions: boolean; stockQuantity: number; variants: SelectableVariant[] } | null,
  items: PurchaseSelection[],
  hasVerifiedReservation = false,
) {
  if (!product || !items.length || (product.status !== "ON_SALE" &&
    !(hasVerifiedReservation && product.status === "SOLD_OUT"))) return true;
  return items.some(item => {
    const variant = product.variants.find(v => v.id === item.variantId);
    return product.hasOptions
      ? !variant || (!hasVerifiedReservation && (!variant.available || item.quantity > variant.stockQuantity))
      : item.variantId !== null || (!hasVerifiedReservation && item.quantity > product.stockQuantity);
  });
}

// Malformed or duplicate URL entries block the entire checkout, never a partial order.
export function parsePurchaseSelections(value: string | null): PurchaseSelection[] {
  if (!value) return [];
  try {
    const items: unknown = JSON.parse(value);
    if (!Array.isArray(items) || !items.length || items.length > 100) return [];
    const ids = new Set<number | null>();
    for (const item of items) {
      if (!item || (item.variantId !== null && (!Number.isSafeInteger(item.variantId) || item.variantId <= 0))
        || !Number.isSafeInteger(item.quantity) || item.quantity <= 0 || ids.has(item.variantId)) return [];
      ids.add(item.variantId);
    }
    return items.map(item => ({ variantId: item.variantId, quantity: item.quantity }));
  } catch { return []; }
}
