export interface CartItem {
  cartItemId: number;
  productId: number;
  sellerId: number;
  storeName: string;
  productName: string;
  brandName: string | null;
  price: number;
  stockQuantity: number;
  quantity: number;
  freeShipping: boolean;
  shippingFee: number;
  representativeImageKey: string | null;
}

export interface Cart {
  items: CartItem[];
  totalProductPrice: number;
  totalShippingFee: number;
  totalPrice: number;
  itemCount: number;
}

export interface CartItemCreateRequest {
  productId: number;
  quantity: number;
}

export interface CartItemQuantityUpdateRequest {
  quantity: number;
}
