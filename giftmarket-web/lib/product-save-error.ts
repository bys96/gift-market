export function getProductSaveErrorKind(status: number | null, message: string) {
  if (status !== 409) return "validation";
  if (/중복/.test(message)) return "validation";
  if (/변경되었습니다.*최신|이전 형식의 상품 요청/.test(message)) return "stale";
  return "conflict";
}
