import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";

import type { Product } from "@/types/product";

interface ProductDetailPageProps {
  params: Promise<{
    productId: string;
  }>;
}

interface ProductDetail extends Product {
  description: string;
  categoryName: string;
  stockQuantity: number;
}

const products: ProductDetail[] = [
  {
    id: 1,
    name: "마음을 담은 프리미엄 초콜릿 선물 세트",
    brandName: "스위트하우스",
    price: 32900,
    imageUrl: "/images/products/product-1.jpg",
    isFreeShipping: true,
    categoryName: "간식",
    stockQuantity: 30,
    description:
      "다양한 맛의 초콜릿을 고급 패키지에 담은 선물 세트입니다. 생일과 감사 선물로 추천합니다.",
  },
  {
    id: 2,
    name: "향기로운 핸드크림 & 바디케어 세트",
    brandName: "오브제뷰티",
    price: 27900,
    imageUrl: "/images/products/product-2.jpg",
    isFreeShipping: true,
    categoryName: "뷰티",
    stockQuantity: 18,
    description:
      "은은한 향과 촉촉한 보습감을 제공하는 핸드크림과 바디케어 구성입니다.",
  },
  {
    id: 3,
    name: "매일 사용하기 좋은 머그컵 세트",
    brandName: "데일리리빙",
    price: 19800,
    imageUrl: "/images/products/product-3.jpg",
    isFreeShipping: false,
    categoryName: "리빙",
    stockQuantity: 42,
    description:
      "따뜻한 음료를 즐기기 좋은 심플한 디자인의 머그컵 2종 세트입니다.",
  },
  {
    id: 4,
    name: "특별한 날을 위한 꽃다발 선물",
    brandName: "플라워데이",
    price: 45000,
    imageUrl: "/images/products/product-4.jpg",
    isFreeShipping: true,
    categoryName: "축하",
    stockQuantity: 12,
    description:
      "특별한 날 마음을 전할 수 있도록 화사한 꽃으로 구성한 꽃다발입니다.",
  },
  {
    id: 5,
    name: "프리미엄 티 컬렉션 선물 세트",
    brandName: "티가든",
    price: 38900,
    imageUrl: "/images/products/product-5.jpg",
    isFreeShipping: true,
    categoryName: "감사",
    stockQuantity: 25,
    description: "다양한 향과 맛을 경험할 수 있는 프리미엄 티 컬렉션입니다.",
  },
  {
    id: 6,
    name: "부드러운 수건 기프트 패키지",
    brandName: "코지홈",
    price: 24900,
    imageUrl: "/images/products/product-6.jpg",
    isFreeShipping: false,
    categoryName: "리빙",
    stockQuantity: 37,
    description:
      "부드러운 촉감과 높은 흡수력을 갖춘 수건을 선물 패키지로 구성했습니다.",
  },
  {
    id: 7,
    name: "데일리 향수 미니어처 컬렉션",
    brandName: "센트오브제",
    price: 59000,
    imageUrl: "/images/products/product-7.jpg",
    isFreeShipping: true,
    categoryName: "뷰티",
    stockQuantity: 9,
    description: "매일 다른 향을 즐길 수 있는 미니어처 향수 컬렉션입니다.",
  },
  {
    id: 8,
    name: "베이커리 쿠키 선물 박스",
    brandName: "브레드하우스",
    price: 21500,
    imageUrl: "/images/products/product-8.jpg",
    isFreeShipping: true,
    categoryName: "간식",
    stockQuantity: 20,
    description:
      "바삭하고 달콤한 쿠키를 다양하게 담은 베이커리 선물 박스입니다.",
  },
];

export default async function ProductDetailPage({
  params,
}: ProductDetailPageProps) {
  const { productId } = await params;
  const parsedProductId = Number(productId);

  if (!Number.isInteger(parsedProductId)) {
    notFound();
  }

  const product = products.find((item) => item.id === parsedProductId);

  if (!product) {
    notFound();
  }

  return (
    <div className="product-detail-page">
      <nav className="product-detail-breadcrumb" aria-label="현재 위치">
        <Link href="/">홈</Link>
        <span aria-hidden="true">/</span>
        <Link href="/products">상품</Link>
        <span aria-hidden="true">/</span>
        <span>{product.categoryName}</span>
      </nav>

      <section className="product-detail">
        <div className="product-detail-image-wrapper">
          <Image
            src={product.imageUrl}
            alt={product.name}
            fill
            priority
            sizes="(max-width: 768px) 100vw, 560px"
            className="product-detail-image"
          />
        </div>

        <div className="product-detail-info">
          <p className="product-detail-brand">{product.brandName}</p>

          <h1 className="product-detail-name">{product.name}</h1>

          <p className="product-detail-description">{product.description}</p>

          <strong className="product-detail-price">
            {product.price.toLocaleString("ko-KR")}원
          </strong>

          <dl className="product-detail-meta">
            <div className="product-detail-meta-row">
              <dt>배송비</dt>
              <dd>{product.isFreeShipping ? "무료배송" : "배송비 3,000원"}</dd>
            </div>

            <div className="product-detail-meta-row">
              <dt>재고</dt>
              <dd>{product.stockQuantity}개</dd>
            </div>

            <div className="product-detail-meta-row">
              <dt>카테고리</dt>
              <dd>{product.categoryName}</dd>
            </div>
          </dl>

          <div className="product-detail-actions">
            <button type="button" className="product-detail-wishlist-button">
              찜하기
            </button>

            <button type="button" className="product-detail-cart-button">
              장바구니
            </button>
          </div>

          <button type="button" className="product-detail-gift-button">
            선물하기
          </button>
        </div>
      </section>

      <section className="product-detail-content">
        <h2 className="product-detail-content-title">상품 정보</h2>

        <div className="product-detail-content-box">
          <p>{product.description}</p>

          <ul>
            <li>상품명: {product.name}</li>
            <li>브랜드: {product.brandName}</li>
            <li>카테고리: {product.categoryName}</li>
          </ul>
        </div>
      </section>
    </div>
  );
}
