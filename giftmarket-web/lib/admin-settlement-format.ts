export const settlementWon = (value: number): string => `${value.toLocaleString("ko-KR")}원`;

export function settlementSignedWon(value: number): string {
  return `${value > 0 ? "+" : value < 0 ? "−" : ""}${settlementWon(Math.abs(value))}`;
}

export function settlementDate(value: string | null, withTime = false): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric", month: "2-digit", day: "2-digit",
    ...(withTime ? { hour: "2-digit", minute: "2-digit" } : {}),
  }).format(new Date(value));
}
