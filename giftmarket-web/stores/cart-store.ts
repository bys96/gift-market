import { create } from "zustand";

import {
  addCartItem,
  clearCart as clearCartApi,
  deleteCartItem,
  deleteCartItems,
  getCart,
  updateCartItemQuantity,
} from "@/lib/cart-api";
import type { Cart, CartItem, CartItemCreateRequest } from "@/types/cart";

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

  addItem: (item: CartItemCreateRequest) => Promise<void>;

  removeItem: (cartItemId: number) => Promise<void>;

  removeSelectedItems: (cartItemIds: number[]) => Promise<void>;

  increaseQuantity: (cartItemId: number) => Promise<void>;

  decreaseQuantity: (cartItemId: number) => Promise<void>;

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

      const cart = await addCartItem(item);

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

  removeItem: async (cartItemId) => {
    const cartItem = get().items.find((item) => item.cartItemId === cartItemId);

    if (!cartItem) {
      return;
    }

    try {
      set({
        isLoading: true,
        errorMessage: "",
      });

      const cart = await deleteCartItem(cartItemId);

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

  removeSelectedItems: async (cartItemIds) => {
    if (cartItemIds.length === 0) {
      return;
    }

    try {
      set({
        isLoading: true,
        errorMessage: "",
      });

      const cart = await deleteCartItems({
        cartItemIds,
      });

      set({
        ...createCartState(cart),
      });
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "선택한 장바구니 상품을 삭제하지 못했습니다.";

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

  increaseQuantity: async (cartItemId) => {
    const cartItem = get().items.find((item) => item.cartItemId === cartItemId);

    if (!cartItem) {
      return;
    }

    if (!cartItem.purchasable) {
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

      const cart = await updateCartItemQuantity(cartItemId, {
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

  decreaseQuantity: async (cartItemId) => {
    const cartItem = get().items.find((item) => item.cartItemId === cartItemId);

    if (!cartItem) {
      return;
    }

    if (
      !cartItem.purchasable &&
      cartItem.availability !== "INSUFFICIENT_STOCK"
    ) {
      return;
    }

    try {
      set({
        isLoading: true,
        errorMessage: "",
      });

      if (cartItem.quantity <= 1) {
        const cart = await deleteCartItem(cartItemId);

        set({
          ...createCartState(cart),
        });

        return;
      }

      const nextQuantity =
        cartItem.availability === "INSUFFICIENT_STOCK"
          ? cartItem.stockQuantity
          : cartItem.quantity - 1;

      if (nextQuantity <= 0) {
        return;
      }

      const cart = await updateCartItemQuantity(cartItemId, {
        quantity: nextQuantity,
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
