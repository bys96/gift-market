import Link from "next/link";

export default function ProductNotFound() {
  return (
    <div className="product-not-found">
      <h1 className="product-not-found-title">상품을 찾을 수 없습니다</h1>

      <p className="product-not-found-description">
        판매가 종료되었거나 존재하지 않는 상품입니다.
      </p>

      <Link href="/products" className="product-not-found-link">
        상품 목록으로 이동
      </Link>
    </div>
  );
}
