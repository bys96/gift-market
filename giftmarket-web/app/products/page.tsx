import ProductCard from "@/components/product/ProductCard";
import type { Product } from "@/types/product";

interface ProductsPageProps {
  searchParams: Promise<{
    category?: string;
    sort?: string;
  }>;
}

const categories = [
  "전체",
  "생일",
  "감사",
  "축하",
  "응원",
  "간식",
  "뷰티",
  "패션",
  "리빙",
];

const products: Product[] = [
  {
    id: 1,
    name: "마음을 담은 프리미엄 초콜릿 선물 세트",
    brandName: "스위트하우스",
    price: 32900,
    imageUrl: "/images/products/product-1.jpg",
    isFreeShipping: true,
  },
  {
    id: 2,
    name: "향기로운 핸드크림 & 바디케어 세트",
    brandName: "오브제뷰티",
    price: 27900,
    imageUrl: "/images/products/product-2.jpg",
    isFreeShipping: true,
  },
  {
    id: 3,
    name: "매일 사용하기 좋은 머그컵 세트",
    brandName: "데일리리빙",
    price: 19800,
    imageUrl: "/images/products/product-3.jpg",
    isFreeShipping: false,
  },
  {
    id: 4,
    name: "특별한 날을 위한 꽃다발 선물",
    brandName: "플라워데이",
    price: 45000,
    imageUrl: "/images/products/product-4.jpg",
    isFreeShipping: true,
  },
  {
    id: 5,
    name: "프리미엄 티 컬렉션 선물 세트",
    brandName: "티가든",
    price: 38900,
    imageUrl: "/images/products/product-5.jpg",
    isFreeShipping: true,
  },
  {
    id: 6,
    name: "부드러운 수건 기프트 패키지",
    brandName: "코지홈",
    price: 24900,
    imageUrl: "/images/products/product-6.jpg",
    isFreeShipping: false,
  },
  {
    id: 7,
    name: "데일리 향수 미니어처 컬렉션",
    brandName: "센트오브제",
    price: 59000,
    imageUrl: "/images/products/product-7.jpg",
    isFreeShipping: true,
  },
  {
    id: 8,
    name: "베이커리 쿠키 선물 박스",
    brandName: "브레드하우스",
    price: 21500,
    imageUrl: "/images/products/product-8.jpg",
    isFreeShipping: true,
  },
];

export default async function ProductsPage({
  searchParams,
}: ProductsPageProps) {
  const { category = "전체", sort = "recommended" } = await searchParams;

  const sortedProducts = [...products].sort((a, b) => {
    if (sort === "price-asc") {
      return a.price - b.price;
    }

    if (sort === "price-desc") {
      return b.price - a.price;
    }

    return a.id - b.id;
  });

  return (
    <div className="product-page">
      <div className="product-page-header">
        <div>
          <p className="product-page-eyebrow">PRODUCTS</p>
          <h1 className="product-page-title">선물 전체보기</h1>
        </div>

        <p className="product-page-count">
          총 <strong>{sortedProducts.length}</strong>개의 상품
        </p>
      </div>

      <div className="product-page-category-list">
        {categories.map((categoryName) => {
          const isActive = categoryName === category;

          return (
            <a
              key={categoryName}
              href={
                categoryName === "전체"
                  ? `/products?sort=${sort}`
                  : `/products?category=${encodeURIComponent(
                      categoryName,
                    )}&sort=${sort}`
              }
              className={`product-page-category ${
                isActive ? "product-page-category-active" : ""
              }`}
            >
              {categoryName}
            </a>
          );
        })}
      </div>

      <div className="product-page-toolbar">
        <p className="product-page-selected-category">
          {category === "전체" ? "전체 상품" : `${category} 선물`}
        </p>

        <form className="product-page-sort-form">
          {category !== "전체" && (
            <input type="hidden" name="category" value={category} />
          )}

          <label htmlFor="product-sort" className="sr-only">
            상품 정렬
          </label>

          <select
            id="product-sort"
            name="sort"
            defaultValue={sort}
            className="product-page-sort"
          >
            <option value="recommended">추천순</option>
            <option value="price-asc">낮은 가격순</option>
            <option value="price-desc">높은 가격순</option>
          </select>

          <button type="submit" className="product-page-sort-button">
            적용
          </button>
        </form>
      </div>

      <div className="product-list">
        {sortedProducts.map((product) => (
          <ProductCard key={product.id} product={product} />
        ))}
      </div>
    </div>
  );
}
