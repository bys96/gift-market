import Link from "next/link";

interface MyQuickStatsProps {
  orderCount: number | null;
  wishlistCount: number | null;
  addressCount: number | null;
}

export default function MyQuickStats({
  orderCount,
  wishlistCount,
  addressCount,
}: MyQuickStatsProps) {
  return (
    <section className="my-quick-stats">
      <Link className="my-quick-stat" href="/my/orders">
        <strong className="my-quick-stat-value">{orderCount ?? "-"}</strong>

        <span className="my-quick-stat-label">주문</span>
      </Link>

      <Link className="my-quick-stat" href="/my/wishlist">
        <strong className="my-quick-stat-value">{wishlistCount ?? "-"}</strong>

        <span className="my-quick-stat-label">찜</span>
      </Link>

      <Link className="my-quick-stat" href="/my/addresses">
        <strong className="my-quick-stat-value">{addressCount ?? "-"}</strong>

        <span className="my-quick-stat-label">배송지</span>
      </Link>
    </section>
  );
}
