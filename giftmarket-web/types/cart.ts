export interface CartItemOption {
  optionGroupId: number;
  optionGroupName: string;
  optionValueId: number;
  optionValue: string;
}

export interface CartItem {
  cartItemId: number;
  productId: number;
  variantId: number | null;

  sellerId: number;
  storeName: string;

  productName: string;
  brandName: string | null;

  price: number;
  additionalPrice: number;

  stockQuantity: number;
  quantity: number;

  freeShipping: boolean;
  shippingFee: number;

  representativeImageKey: string | null;

  options: CartItemOption[];
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
  variantId: number | null;
  quantity: number;
}

export interface CartItemQuantityUpdateRequest {
  quantity: number;
}
