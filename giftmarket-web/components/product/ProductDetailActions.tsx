"use client";

import { useRouter } from "next/navigation";
import { useCartStore } from "@/stores/cart-store";

interface ProductDetailActionsProps {
  product: {
    id: number;
    name: string;
    brandName: string;
    price: number;
    imageUrl: string;
    stockQuantity: number;
    isFreeShipping: boolean;
  };
}

export default function ProductDetailActions({
  product,
}: ProductDetailActionsProps) {
  const router = useRouter();
  const addItem = useCartStore((state) => state.addItem);

  const handleAddCart = () => {
    addItem({
      productId: product.id,
      name: product.name,
      brandName: product.brandName,
      price: product.price,
      imageUrl: product.imageUrl,
      stockQuantity: product.stockQuantity,
      isFreeShipping: product.isFreeShipping,
    });

    alert("장바구니에 상품을 담았습니다.");
  };

  const handleGift = () => {
    addItem({
      productId: product.id,
      name: product.name,
      brandName: product.brandName,
      price: product.price,
      imageUrl: product.imageUrl,
      stockQuantity: product.stockQuantity,
      isFreeShipping: product.isFreeShipping,
    });

    router.push("/cart");
  };

  return (
    <div className="product-detail-actions">
      <button
        type="button"
        className="product-detail-wishlist-button"
        onClick={() => alert("찜하기 기능은 추후 구현 예정입니다.")}
      >
        찜하기
      </button>

      <button
        type="button"
        className="product-detail-cart-button"
        onClick={handleAddCart}
      >
        장바구니
      </button>

      <button
        type="button"
        className="product-detail-gift-button"
        onClick={handleGift}
      >
        선물하기
      </button>
    </div>
  );
}
