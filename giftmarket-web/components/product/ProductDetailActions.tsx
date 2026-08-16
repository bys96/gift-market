"use client";

import { useMemo, useState } from "react";
import { usePathname, useRouter } from "next/navigation";

import { useAuthStore } from "@/stores/auth-store";
import { useCartStore } from "@/stores/cart-store";
import { useWishlistStore } from "@/stores/wishlist-store";
import type {
  Product,
  ProductDetailOptionGroup,
  ProductDetailVariant,
} from "@/types/product";

interface ProductDetailActionsProps {
  product: {
    id: number;
    name: string;
    brandName: string;
    price: number;
    imageUrl: string;
    stockQuantity: number;
    isFreeShipping: boolean;

    hasOptions: boolean;
    optionGroups: ProductDetailOptionGroup[];
    variants: ProductDetailVariant[];
  };
}

export default function ProductDetailActions({
  product,
}: ProductDetailActionsProps) {
  const router = useRouter();
  const pathname = usePathname();

  const [quantity, setQuantity] = useState(1);

  const [selectedOptionValues, setSelectedOptionValues] = useState<
    Record<number, number>
  >({});

  const [isAddingCart, setIsAddingCart] = useState(false);
  const [isBuyingNow, setIsBuyingNow] = useState(false);

  const addCartItem = useCartStore((state) => state.addItem);

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const wishlistItems = useWishlistStore((state) => state.items);
  const wishlistHydrated = useWishlistStore((state) => state.hydrated);

  const toggleWishlistItem = useWishlistStore((state) => state.toggleItem);

  const selectedOptionValueIds = useMemo(
    () => Object.values(selectedOptionValues),
    [selectedOptionValues],
  );

  const selectedVariant = useMemo(() => {
    if (!product.hasOptions) {
      return null;
    }

    if (selectedOptionValueIds.length !== product.optionGroups.length) {
      return null;
    }

    return (
      product.variants.find((variant) => {
        if (variant.optionValueIds.length !== selectedOptionValueIds.length) {
          return false;
        }

        return selectedOptionValueIds.every((optionValueId) =>
          variant.optionValueIds.includes(optionValueId),
        );
      }) ?? null
    );
  }, [
    product.hasOptions,
    product.optionGroups.length,
    product.variants,
    selectedOptionValueIds,
  ]);

  const currentPrice = selectedVariant?.price ?? product.price;

  const currentStockQuantity = product.hasOptions
    ? (selectedVariant?.stockQuantity ?? 0)
    : product.stockQuantity;

  const isVariantSelectionComplete =
    !product.hasOptions ||
    selectedOptionValueIds.length === product.optionGroups.length;

  const isSelectedVariantUnavailable =
    product.hasOptions &&
    isVariantSelectionComplete &&
    (!selectedVariant || !selectedVariant.available);

  const isWishlisted =
    wishlistHydrated && wishlistItems.some((item) => item.id === product.id);

  const totalPrice = useMemo(
    () => currentPrice * quantity,
    [currentPrice, quantity],
  );

  const wishlistProduct: Product = {
    id: product.id,
    name: product.name,
    brandName: product.brandName,
    price: product.price,
    imageUrl: product.imageUrl,
    isFreeShipping: product.isFreeShipping,
  };

  const getVariantOptionLabel = (variant: ProductDetailVariant) =>
    product.optionGroups
      .map((optionGroup) =>
        optionGroup.values.find((optionValue) =>
          variant.optionValueIds.includes(optionValue.id),
        ),
      )
      .filter((optionValue) => optionValue !== undefined)
      .map((optionValue) => optionValue.value)
      .join(" / ");

  const getAdditionalPriceLabel = (additionalPrice: number) => {
    if (additionalPrice === 0) return "";

    const sign = additionalPrice > 0 ? "+" : "-";
    return ` (${sign}${Math.abs(additionalPrice).toLocaleString("ko-KR")}원)`;
  };

  const getVariantStockLabel = (variant: ProductDetailVariant) => {
    if (!variant.available || variant.stockQuantity <= 0) {
      return " (품절)";
    }
    if (variant.stockQuantity <= 5) {
      return ` · ${variant.stockQuantity}개 남음`;
    }
    return "";
  };

  const handleVariantSelect = (variantId: number | null) => {
    setQuantity(1);
    const variant = product.variants.find((item) => item.id === variantId);

    if (!variant) {
      setSelectedOptionValues({});
      return;
    }

    const nextSelectedOptionValues = product.optionGroups.reduce<
      Record<number, number>
    >((selectedValues, optionGroup) => {
      const selectedValue = optionGroup.values.find((optionValue) =>
        variant.optionValueIds.includes(optionValue.id),
      );

      if (selectedValue) {
        selectedValues[optionGroup.id] = selectedValue.id;
      }

      return selectedValues;
    }, {});

    setSelectedOptionValues(nextSelectedOptionValues);
  };

  const decreaseQuantity = () => {
    setQuantity((currentQuantity) => Math.max(1, currentQuantity - 1));
  };

  const increaseQuantity = () => {
    if (currentStockQuantity <= 0) {
      return;
    }

    setQuantity((currentQuantity) =>
      Math.min(currentStockQuantity, currentQuantity + 1),
    );
  };

  const handleToggleWishlist = () => {
    if (!wishlistHydrated) {
      return;
    }

    toggleWishlistItem(wishlistProduct);
  };

  const validateVariantSelection = () => {
    if (!product.hasOptions) {
      return true;
    }

    if (!isVariantSelectionComplete) {
      alert("상품 옵션을 모두 선택해주세요.");
      return false;
    }

    if (!selectedVariant) {
      alert("선택할 수 없는 옵션 조합입니다.");
      return false;
    }

    if (!selectedVariant.available) {
      alert("선택한 상품 옵션은 품절되었습니다.");
      return false;
    }

    return true;
  };

  const handleAddCart = async () => {
    if (!isAuthenticated) {
      router.push(`/login?redirect=${encodeURIComponent(pathname)}`);
      return;
    }

    if (isAddingCart || isBuyingNow) {
      return;
    }

    if (!validateVariantSelection()) {
      return;
    }

    try {
      setIsAddingCart(true);

      await addCartItem({
        productId: product.id,
        variantId: selectedVariant?.id ?? null,
        quantity,
      });

      alert("장바구니에 상품을 담았습니다.");
    } catch (error) {
      alert(
        error instanceof Error
          ? error.message
          : "장바구니에 상품을 담지 못했습니다.",
      );
    } finally {
      setIsAddingCart(false);
    }
  };

  const handleBuyNow = () => {
    if (!isAuthenticated) {
      router.push(`/login?redirect=${encodeURIComponent(pathname)}`);
      return;
    }

    if (isAddingCart || isBuyingNow) {
      return;
    }

    if (!validateVariantSelection()) {
      return;
    }

    try {
      setIsBuyingNow(true);

      const params = new URLSearchParams({
        productId: String(product.id),
        quantity: String(quantity),
      });

      if (selectedVariant) {
        params.set("variantId", String(selectedVariant.id));
      }

      router.push(`/order?${params.toString()}`);
    } finally {
      setIsBuyingNow(false);
    }
  };

  const isActionDisabled =
    isAddingCart ||
    isBuyingNow ||
    (!product.hasOptions && product.stockQuantity <= 0) ||
    isSelectedVariantUnavailable;

  return (
    <div className="product-detail-purchase">
      {product.hasOptions && (
        <div className="product-detail-option-section">
          <label
            className="product-detail-option-label"
            htmlFor="product-variant-select"
          >
            옵션 선택
          </label>

          <div className="product-detail-option-select-wrapper">
            <select
              id="product-variant-select"
              className="product-detail-option-select"
              value={selectedVariant?.id ?? ""}
              onChange={(event) =>
                handleVariantSelect(
                  event.target.value ? Number(event.target.value) : null,
                )
              }
            >
              <option value="">옵션을 선택해주세요</option>

              {product.variants.map((variant) => (
                <option
                  key={variant.id}
                  value={variant.id}
                  disabled={!variant.available}
                >
                  {getVariantOptionLabel(variant)}
                  {getAdditionalPriceLabel(variant.additionalPrice)}
                  {getVariantStockLabel(variant)}
                </option>
              ))}
            </select>
          </div>

          {isVariantSelectionComplete && selectedVariant && (
            <div className="product-detail-selected-variant">
              <span>선택 상품</span>

              <strong>{currentPrice.toLocaleString("ko-KR")}원</strong>
            </div>
          )}
        </div>
      )}

      <div className="product-detail-quantity-section">
        <div>
          <strong className="product-detail-quantity-title">구매 수량</strong>

          <p className="product-detail-quantity-stock">
            {product.hasOptions && !isVariantSelectionComplete
              ? "옵션을 선택하면 재고를 확인할 수 있습니다."
              : product.hasOptions
                ? "선택한 옵션의 구매 수량을 정해주세요."
                : currentStockQuantity > 0
                ? `현재 재고 ${currentStockQuantity.toLocaleString("ko-KR")}개`
                : "현재 품절된 상품입니다."}
          </p>
        </div>

        <div
          className="product-detail-quantity-control"
          aria-label="상품 수량 선택"
        >
          <button
            type="button"
            className="product-detail-quantity-button"
            aria-label="수량 줄이기"
            disabled={
              isActionDisabled || !isVariantSelectionComplete || quantity <= 1
            }
            onClick={decreaseQuantity}
          >
            −
          </button>

          <span className="product-detail-quantity-value" aria-live="polite">
            {quantity}
          </span>

          <button
            type="button"
            className="product-detail-quantity-button"
            aria-label="수량 늘리기"
            disabled={
              isActionDisabled ||
              !isVariantSelectionComplete ||
              currentStockQuantity <= 0 ||
              quantity >= currentStockQuantity
            }
            onClick={increaseQuantity}
          >
            +
          </button>
        </div>
      </div>

      <div className="product-detail-total">
        <span>총 상품 금액</span>

        <strong>{totalPrice.toLocaleString("ko-KR")}원</strong>
      </div>

      <div className="product-detail-actions">
        <button
          type="button"
          className={[
            "product-detail-wishlist-button",
            isWishlisted ? "product-detail-wishlist-button-active" : "",
          ]
            .filter(Boolean)
            .join(" ")}
          aria-label={isWishlisted ? "찜 목록에서 제거" : "찜 목록에 추가"}
          aria-pressed={isWishlisted}
          disabled={!wishlistHydrated || isAddingCart || isBuyingNow}
          onClick={handleToggleWishlist}
        >
          <span aria-hidden="true">{isWishlisted ? "♥" : "♡"}</span>

          <span>찜</span>
        </button>

        <button
          type="button"
          className="product-detail-cart-button"
          onClick={() => void handleAddCart()}
          disabled={isActionDisabled}
        >
          {isAddingCart ? "담는 중..." : "장바구니"}
        </button>

        <button
          type="button"
          className="product-detail-gift-button"
          onClick={handleBuyNow}
          disabled={isActionDisabled}
        >
          {isBuyingNow ? "이동 중..." : "바로 구매"}
        </button>
      </div>
    </div>
  );
}
