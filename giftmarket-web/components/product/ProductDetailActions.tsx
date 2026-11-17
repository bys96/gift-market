"use client";

import { useMemo, useState } from "react";
import { usePathname, useRouter } from "next/navigation";

import { addVariantSelection, changeSelectionQuantity, selectionTotal, type PurchaseSelection } from "@/lib/product-selection";
import Modal from "@/components/common/modal/Modal";
import WishlistHeartIcon from "@/components/product/WishlistHeartIcon";
import { useAuthStore } from "@/stores/auth-store";
import { useCartStore } from "@/stores/cart-store";
import { useWishlistStore } from "@/stores/wishlist-store";
import type {
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

  const [selections, setSelections] = useState<PurchaseSelection[]>([]);

  const [isAddingCart, setIsAddingCart] = useState(false);
  const [isBuyingNow, setIsBuyingNow] = useState(false);
  const [isCartPromptOpen, setIsCartPromptOpen] = useState(false);

  const addCartItems = useCartStore((state) => state.addItems);

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const wishlistItems = useWishlistStore((state) => state.items);
  const wishlistInitialized = useWishlistStore((state) => state.initialized);
  const wishlistMutatingProductIds = useWishlistStore((state) => state.mutatingProductIds);

  const toggleWishlistItem = useWishlistStore((state) => state.toggleItem);

  const currentStockQuantity = product.stockQuantity;
  const isWishlisted =
    isAuthenticated && wishlistItems.some((item) => item.id === product.id);

  const totalPrice = useMemo(
    () => product.hasOptions ? selectionTotal(selections, product.variants) : product.price * quantity,
    [product.hasOptions, product.variants, product.price, selections, quantity],
  );

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
    const variant = product.variants.find(item => item.id === variantId);
    if (variant) setSelections(current => addVariantSelection(current, variant));
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

  const handleToggleWishlist = async () => {
    if (!isAuthenticated) {
      router.push(`/login?redirect=${encodeURIComponent(pathname)}`);
      return;
    }

    try {
      await toggleWishlistItem(product.id);
    } catch (error) {
      alert(error instanceof Error ? error.message : "찜 상태를 변경하지 못했습니다.");
    }
  };

  const validateVariantSelection = () => {
    if (!product.hasOptions) {
      if (quantity > 0 && quantity <= product.stockQuantity) return true;
      alert("상품 재고가 변경되었습니다. 구매 수량을 다시 확인해주세요.");
      return false;
    }
    if (!selections.length) { alert("상품 옵션을 선택해주세요."); return false; }
    if (selections.some(item => {
      const variant = product.variants.find(v => v.id === item.variantId);
      return !variant?.available || item.quantity > variant.stockQuantity;
    })) { alert("옵션 상태 또는 재고가 변경되었습니다. 다시 확인해주세요."); return false; }
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

      await addCartItems((product.hasOptions ? selections : [{ variantId: null, quantity }])
        .map(item => ({ productId: product.id, ...item,
          expectedUnitPrice: product.variants.find(v => v.id === item.variantId)?.price ?? product.price,
        })));

      setIsCartPromptOpen(true);
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

      const params = new URLSearchParams({ productId: String(product.id) });
      if (product.hasOptions) params.set("items", JSON.stringify(selections));
      else params.set("quantity", String(quantity));

      router.push(`/order?${params.toString()}`);
    } finally {
      setIsBuyingNow(false);
    }
  };

  const isActionDisabled =
    isAddingCart ||
    isBuyingNow ||
    (!product.hasOptions && product.stockQuantity <= 0) ||
    (product.hasOptions && selections.length === 0);

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
              value=""
              disabled={isAddingCart || isBuyingNow}
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
                  disabled={!variant.available || variant.stockQuantity <= 0}
                >
                  {getVariantOptionLabel(variant)}
                  {getAdditionalPriceLabel(variant.additionalPrice)}
                  {getVariantStockLabel(variant)}
                </option>
              ))}
            </select>
          </div>

          <div className="product-detail-selection-list" aria-live="polite">
            {selections.map(item => {
              const variant = product.variants.find(v => v.id === item.variantId)!;
              const label = getVariantOptionLabel(variant);
              const busy = isAddingCart || isBuyingNow;
              return (
                <div className="product-detail-selection-card" key={variant.id}>
                  <div className="product-detail-selection-heading">
                    <div><strong>{label}</strong><p>개당 {variant.price.toLocaleString("ko-KR")}원</p></div>
                    <button type="button" className="product-detail-selection-remove" aria-label={label + " 삭제"}
                      disabled={busy} onClick={() => setSelections(current => current.filter(v => v.variantId !== variant.id))}>×</button>
                  </div>
                  <div className="product-detail-selection-bottom">
                    <div className="product-detail-quantity-control" aria-label={label + " 수량"}>
                      <button type="button" className="product-detail-quantity-button" aria-label={label + " 수량 줄이기"}
                        disabled={busy || item.quantity <= 1} onClick={() => setSelections(current => changeSelectionQuantity(current, variant.id, -1, variant.stockQuantity))}>−</button>
                      <span className="product-detail-quantity-value">{item.quantity}</span>
                      <button type="button" className="product-detail-quantity-button" aria-label={label + " 수량 늘리기"}
                        disabled={busy || item.quantity >= variant.stockQuantity} onClick={() => setSelections(current => changeSelectionQuantity(current, variant.id, 1, variant.stockQuantity))}>+</button>
                    </div>
                    <strong>{(variant.price * item.quantity).toLocaleString("ko-KR")}원</strong>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {!product.hasOptions && (
      <div className="product-detail-quantity-section">
        <div>
          <strong className="product-detail-quantity-title">구매 수량</strong>

          <p className="product-detail-quantity-stock">
            {currentStockQuantity > 0
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
              isActionDisabled || quantity <= 1
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
              currentStockQuantity <= 0 ||
              quantity >= currentStockQuantity
            }
            onClick={increaseQuantity}
          >
            +
          </button>
        </div>
      </div>

      )}

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
          disabled={
            (isAuthenticated && !wishlistInitialized) ||
            wishlistMutatingProductIds.includes(product.id) ||
            isAddingCart ||
            isBuyingNow
          }
          onClick={() => void handleToggleWishlist()}
        >
          <WishlistHeartIcon filled={isWishlisted} />

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
      {isCartPromptOpen && (
        <Modal
          onClose={() => setIsCartPromptOpen(false)}
          overlayClassName="product-cart-confirm-backdrop"
          contentClassName="product-cart-confirm-modal"
          ariaLabelledBy="product-cart-confirm-title"
          ariaDescribedBy="product-cart-confirm-description"
        >
          <h2 id="product-cart-confirm-title">장바구니에 상품을 담았습니다.</h2>
          <p id="product-cart-confirm-description">장바구니로 이동하시겠습니까?</p>
          <div className="product-cart-confirm-actions">
            <button type="button" onClick={() => setIsCartPromptOpen(false)}>취소</button>
            <button type="button" onClick={() => { setIsCartPromptOpen(false); router.push("/cart"); }}>장바구니로 이동</button>
          </div>
        </Modal>
      )}
    </div>
  );
}
