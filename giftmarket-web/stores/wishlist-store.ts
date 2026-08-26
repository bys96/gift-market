"use client";

import { create } from "zustand";
import { addWishlist, getWishlist, removeWishlist } from "@/lib/wishlist-api";
import type { ProductSummary } from "@/types/product";

interface WishlistState {
  items: ProductSummary[];
  initialized: boolean;
  isLoading: boolean;
  errorMessage: string;
  mutatingProductIds: number[];
  loadWishlist: (force?: boolean) => Promise<void>;
  toggleItem: (productId: number) => Promise<void>;
  resetWishlist: () => void;
}

export const useWishlistStore = create<WishlistState>((set, get) => ({
  items: [],
  initialized: false,
  isLoading: false,
  errorMessage: "",
  mutatingProductIds: [],

  loadWishlist: async (force = false) => {
    if (get().isLoading || (get().initialized && !force)) return;
    try {
      set({ isLoading: true, errorMessage: "" });
      const items = await getWishlist();
      set({ items, initialized: true });
    } catch (error) {
      set({
        items: [],
        initialized: true,
        errorMessage: error instanceof Error ? error.message : "찜 목록을 불러오지 못했습니다.",
      });
      throw error;
    } finally {
      set({ isLoading: false });
    }
  },

  toggleItem: async (productId) => {
    if (get().mutatingProductIds.includes(productId)) return;
    const isWishlisted = get().items.some((item) => item.id === productId);
    set((state) => ({ errorMessage: "", mutatingProductIds: [...state.mutatingProductIds, productId] }));
    try {
      if (isWishlisted) {
        await removeWishlist(productId);
        set((state) => ({ items: state.items.filter((item) => item.id !== productId) }));
      } else {
        const item = await addWishlist(productId);
        set((state) => ({ items: [item, ...state.items.filter((value) => value.id !== item.id)], initialized: true }));
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "찜 상태를 변경하지 못했습니다.";
      set({ errorMessage: message });
      throw error;
    } finally {
      set((state) => ({ mutatingProductIds: state.mutatingProductIds.filter((id) => id !== productId) }));
    }
  },

  resetWishlist: () => set({ items: [], initialized: false, isLoading: false, errorMessage: "", mutatingProductIds: [] }),
}));
