import { create } from "zustand";
import { persist } from "zustand/middleware";

export interface CartItem {
  productId: number;
  name: string;
  brandName: string;
  price: number;
  imageUrl: string;
  quantity: number;
  stockQuantity: number;
  isFreeShipping: boolean;
}

interface AddCartItem {
  productId: number;
  name: string;
  brandName: string;
  price: number;
  imageUrl: string;
  quantity?: number;
  stockQuantity: number;
  isFreeShipping: boolean;
}

interface CartState {
  items: CartItem[];

  addItem: (item: AddCartItem) => void;
  removeItem: (productId: number) => void;
  increaseQuantity: (productId: number) => void;
  decreaseQuantity: (productId: number) => void;
  clearCart: () => void;
}

export const useCartStore = create<CartState>()(
  persist(
    (set) => ({
      items: [],

      addItem: (newItem) =>
        set((state) => {
          const requestedQuantity = Math.max(
            1,
            Math.min(newItem.quantity ?? 1, newItem.stockQuantity),
          );

          const existingItem = state.items.find(
            (item) => item.productId === newItem.productId,
          );

          if (!existingItem) {
            return {
              items: [
                ...state.items,
                {
                  ...newItem,
                  quantity: requestedQuantity,
                },
              ],
            };
          }

          return {
            items: state.items.map((item) =>
              item.productId === newItem.productId
                ? {
                    ...item,
                    name: newItem.name,
                    brandName: newItem.brandName,
                    price: newItem.price,
                    imageUrl: newItem.imageUrl,
                    stockQuantity: newItem.stockQuantity,
                    isFreeShipping: newItem.isFreeShipping,
                    quantity: Math.min(
                      item.quantity + requestedQuantity,
                      newItem.stockQuantity,
                    ),
                  }
                : item,
            ),
          };
        }),

      removeItem: (productId) =>
        set((state) => ({
          items: state.items.filter((item) => item.productId !== productId),
        })),

      increaseQuantity: (productId) =>
        set((state) => ({
          items: state.items.map((item) =>
            item.productId === productId
              ? {
                  ...item,
                  quantity: Math.min(item.quantity + 1, item.stockQuantity),
                }
              : item,
          ),
        })),

      decreaseQuantity: (productId) =>
        set((state) => ({
          items: state.items
            .map((item) =>
              item.productId === productId
                ? {
                    ...item,
                    quantity: item.quantity - 1,
                  }
                : item,
            )
            .filter((item) => item.quantity > 0),
        })),

      clearCart: () => set({ items: [] }),
    }),
    {
      name: "open-market-cart",
    },
  ),
);
