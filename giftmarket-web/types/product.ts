export type ProductStatus = "DRAFT" | "ON_SALE" | "SOLD_OUT" | "HIDDEN";

export interface Product {
  id: number;
  name: string;
  brandName: string;
  price: number;
  imageUrl: string;
  isFreeShipping: boolean;
}

export interface ProductSummary {
  id: number;
  categoryId: number;
  categoryName: string;
  name: string;
  brandName: string | null;
  price: number;
  status: ProductStatus;
  representativeImageKey: string | null;
  freeShipping: boolean;
  shippingFee: number;
}

export interface ProductPage {
  products: ProductSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface SellerProduct {
  id: number;
  sellerId: number;
  categoryId: number;
  categoryName: string;
  name: string;
  brandName: string | null;
  summary: string | null;
  description: string | null;
  price: number;
  stockQuantity: number;
  status: ProductStatus;
  representativeImageKey: string | null;
  galleryImageKeys: string[];
  freeShipping: boolean;
  shippingFee: number;
  createdAt: string;
  updatedAt: string;
}

export interface SellerProductListItem {
  id: number;
  categoryId: number;
  categoryName: string;
  name: string;
  brandName: string | null;
  price: number;
  stockQuantity: number;
  status: ProductStatus;
  representativeImageKey: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SellerProductPage {
  products: SellerProductListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ProductCreateRequest {
  categoryId: number;
  name: string;
  brandName: string | null;
  summary: string | null;
  description: string | null;
  price: number;
  stockQuantity: number;
  representativeImageKey: string | null;
  galleryImageKeys: string[];
  freeShipping: boolean;
  shippingFee: number;
  startSale: boolean;
}

export interface ProductUpdateRequest {
  categoryId: number;
  name: string;
  brandName: string | null;
  summary: string | null;
  description: string | null;
  price: number;
  stockQuantity: number;
  representativeImageKey: string | null;
  galleryImageKeys: string[];
  freeShipping: boolean;
  shippingFee: number;
}

export interface ProductStatusUpdateRequest {
  status: Exclude<ProductStatus, "SOLD_OUT">;
}

export interface ProductStockUpdateRequest {
  stockQuantity: number;
}

export interface Category {
  id: number;
  name: string;
  children: Category[];
}

export interface ProductDetail {
  id: number;
  sellerId: number;
  storeName: string;
  categoryId: number;
  categoryName: string;
  name: string;
  brandName: string | null;
  summary: string | null;
  description: string | null;
  price: number;
  stockQuantity: number;
  status: ProductStatus;
  representativeImageKey: string | null;
  galleryImageKeys: string[];
  freeShipping: boolean;
  shippingFee: number;
}
