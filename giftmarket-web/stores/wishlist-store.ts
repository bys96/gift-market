"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Product } from "@/types/product";

interface WishlistState {
  items: Product[];
  hydrated: boolean;

  setHydrated: (hydrated: boolean) => void;
  addItem: (product: Product) => void;
  removeItem: (productId: number) => void;
  toggleItem: (product: Product) => void;
  clearWishlist: () => void;
}

export const useWishlistStore = create<WishlistState>()(
  persist(
    (set, get) => ({
      items: [],
      hydrated: false,

      setHydrated: (hydrated) => {
        set({ hydrated });
      },

      addItem: (product) => {
        const exists = get().items.some((item) => item.id === product.id);

        if (exists) {
          return;
        }

        set((state) => ({
          items: [...state.items, product],
        }));
      },

      removeItem: (productId) => {
        set((state) => ({
          items: state.items.filter((item) => item.id !== productId),
        }));
      },

      toggleItem: (product) => {
        const exists = get().items.some((item) => item.id === product.id);

        if (exists) {
          set((state) => ({
            items: state.items.filter((item) => item.id !== product.id),
          }));

          return;
        }

        set((state) => ({
          items: [...state.items, product],
        }));
      },

      clearWishlist: () => {
        set({ items: [] });
      },
    }),
    {
      name: "open-market-wishlist",

      onRehydrateStorage: () => (state) => {
        state?.setHydrated(true);
      },
    },
  ),
);
