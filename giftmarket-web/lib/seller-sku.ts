// Match Java String.trim() and Locale.ROOT uppercase, including length expansion.
export function normalizeSellerSkuCode(code: string): string {
  const normalized = code.replace(/^[\x00-\x20]+|[\x00-\x20]+$/g, "").toUpperCase();
  if (!normalized) throw new Error("옵션 관리 코드를 입력해주세요.");
  if (normalized.length > 100) {
    throw new Error("옵션 관리 코드는 정규화 후 100자 이하입니다.");
  }
  return normalized;
}
