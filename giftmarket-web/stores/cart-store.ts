import { create } from "zustand";

import {
  addCartItem,
  clearCart as clearCartApi,
  deleteCartItem,
  getCart,
  updateCartItemQuantity,
} from "@/lib/cart-api";
import type { Cart, CartItem } from "@/types/cart";

interface AddCartItem {
  productId: number;
  quantity?: number;

  // 기존 ProductDetailActions 호출부 호환용
  name?: string;
  brandName?: string;
  price?: number;
  imageUrl?: string;
  stockQuantity?: number;
  isFreeShipping?: boolean;
}

interface CartState {
  items: CartItem[];

  totalProductPrice: number;
  totalShippingFee: number;
  totalPrice: number;
  itemCount: number;

  initialized: boolean;
  isLoading: boolean;
  errorMessage: string;

  loadCart: () => Promise<void>;
  addItem: (item: AddCartItem) => Promise<void>;
  removeItem: (productId: number) => Promise<void>;
  increaseQuantity: (productId: number) => Promise<void>;
  decreaseQuantity: (productId: number) => Promise<void>;
  clearCart: () => Promise<void>;
  resetCart: () => void;
}

const EMPTY_CART_STATE = {
  items: [],
  totalProductPrice: 0,
  totalShippingFee: 0,
  totalPrice: 0,
  itemCount: 0,
};

function createCartState(cart: Cart) {
  return {
    items: cart.items,
    totalProductPrice: cart.totalProductPrice,
    totalShippingFee: cart.totalShippingFee,
    totalPrice: cart.totalPrice,
    itemCount: cart.itemCount,
  };
}

export const useCartStore = create<CartState>((set, get) => ({
  ...EMPTY_CART_STATE,

  initialized: false,
  isLoading: false,
  errorMessage: "",

  loadCart: async () => {
    if (get().isLoading) {
      return;
    }

    try {
      set({
        isLoading: true,
        errorMessage: "",
      });

      const cart = await getCart();

      set({
        ...createCartState(cart),
        initialized: true,
      });
    } catch (error) {
      set({
        ...EMPTY_CART_STATE,
        initialized: true,
        errorMessage:
          error instanceof Error
            ? error.message
            : "장바구니를 불러오지 못했습니다.",
      });
    } finally {
      set({
        isLoading: false,
      });
    }
  },

  addItem: async (item) => {
    try {
      set({
        isLoading: true,
        errorMessage: "",
      });

      const cart = await addCartItem({
        productId: item.productId,
        quantity: item.quantity ?? 1,
      });

      set({
        ...createCartState(cart),
        initialized: true,
      });
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "장바구니에 상품을 담지 못했습니다.";

      set({
        errorMessage: message,
      });

      throw error;
    } finally {
      set({
        isLoading: false,
      });
    }
  },

  removeItem: async (productId) => {
    const cartItem = get().items.find((item) => item.productId === productId);

    if (!cartItem) {
      return;
    }

    try {
      set({
        isLoading: true,
        errorMessage: "",
      });

      const cart = await deleteCartItem(cartItem.cartItemId);

      set({
        ...createCartState(cart),
      });
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "장바구니 상품을 삭제하지 못했습니다.";

      set({
        errorMessage: message,
      });

      throw error;
    } finally {
      set({
        isLoading: false,
      });
    }
  },

  increaseQuantity: async (productId) => {
    const cartItem = get().items.find((item) => item.productId === productId);

    if (!cartItem) {
      return;
    }

    if (cartItem.quantity >= cartItem.stockQuantity) {
      return;
    }

    try {
      set({
        isLoading: true,
        errorMessage: "",
      });

      const cart = await updateCartItemQuantity(cartItem.cartItemId, {
        quantity: cartItem.quantity + 1,
      });

      set({
        ...createCartState(cart),
      });
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "장바구니 수량을 변경하지 못했습니다.";

      set({
        errorMessage: message,
      });

      throw error;
    } finally {
      set({
        isLoading: false,
      });
    }
  },

  decreaseQuantity: async (productId) => {
    const cartItem = get().items.find((item) => item.productId === productId);

    if (!cartItem) {
      return;
    }

    try {
      set({
        isLoading: true,
        errorMessage: "",
      });

      if (cartItem.quantity <= 1) {
        const cart = await deleteCartItem(cartItem.cartItemId);

        set({
          ...createCartState(cart),
        });

        return;
      }

      const cart = await updateCartItemQuantity(cartItem.cartItemId, {
        quantity: cartItem.quantity - 1,
      });

      set({
        ...createCartState(cart),
      });
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "장바구니 수량을 변경하지 못했습니다.";

      set({
        errorMessage: message,
      });

      throw error;
    } finally {
      set({
        isLoading: false,
      });
    }
  },

  clearCart: async () => {
    try {
      set({
        isLoading: true,
        errorMessage: "",
      });

      await clearCartApi();

      set({
        ...EMPTY_CART_STATE,
        initialized: true,
      });
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "장바구니를 비우지 못했습니다.";

      set({
        errorMessage: message,
      });

      throw error;
    } finally {
      set({
        isLoading: false,
      });
    }
  },

  resetCart: () => {
    set({
      ...EMPTY_CART_STATE,
      initialized: false,
      isLoading: false,
      errorMessage: "",
    });
  },
}));
