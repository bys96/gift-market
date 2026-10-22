export type ProductStatus = "DRAFT" | "ON_SALE" | "SOLD_OUT" | "HIDDEN";
export type ProductSort = "LATEST" | "POPULAR" | "PRICE_ASC" | "PRICE_DESC";

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
  giftOccasions: GiftOccasion[];
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

export type HomePriceRangeCode =
  | "UNDER_10000"
  | "FROM_10001_TO_30000"
  | "FROM_30001_TO_50000"
  | "OVER_50000";

export interface HomeProductPriceRange {
  code: HomePriceRangeCode;
  products: ProductSummary[];
}

export interface HomeProductData {
  giftOccasions: GiftOccasion[];
  popularProducts: ProductSummary[];
  priceRanges: HomeProductPriceRange[];
  newestProducts: ProductSummary[];
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
  adminHidden: boolean;
  adminHiddenReason: string | null;
  adminHiddenAt: string | null;
  representativeImageKey: string | null;
  galleryImageKeys: string[];
  giftOccasions: GiftOccasion[];
  freeShipping: boolean;
  shippingFee: number;
  shippingPreparationDays: number;
  returnShippingFee: number;
  exchangeShippingFee: number;
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
  adminHidden: boolean;
  adminHiddenReason: string | null;
  adminHiddenAt: string | null;
  representativeImageKey: string | null;
  giftOccasions: GiftOccasion[];
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
  shippingPreparationDays: number;
  returnShippingFee: number;
  exchangeShippingFee: number;
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
  shippingPreparationDays: number;
  returnShippingFee: number;
  exchangeShippingFee: number;
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

export interface GiftOccasion {
  id: number;
  name: string;
}

/* ========================================
   Buyer Product Detail
======================================== */

export interface ProductDetailOptionValue {
  id: number;
  value: string;
  sortOrder: number;
}

export interface ProductDetailOptionGroup {
  id: number;
  name: string;
  sortOrder: number;
  values: ProductDetailOptionValue[];
}

export interface ProductDetailVariant {
  id: number;
  optionValueIds: number[];
  additionalPrice: number;
  price: number;
  stockQuantity: number;
  available: boolean;
}

export interface ProductDetail {
  id: number;
  sellerId: number;
  storeName: string;
  sellerIntroduction: string | null;

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
  shippingPreparationDays: number;
  returnShippingFee: number;
  exchangeShippingFee: number;
  giftOccasions: GiftOccasion[];

  hasOptions: boolean;
  optionGroups: ProductDetailOptionGroup[];
  variants: ProductDetailVariant[];
}

/* ========================================
   Seller Product Options
======================================== */

export interface ProductOptionValue {
  id: number;
  value: string;
  sortOrder: number;
}

export interface ProductOptionGroup {
  id: number;
  name: string;
  sortOrder: number;
  values: ProductOptionValue[];
}

export interface ProductOptionResponse {
  productId: number;
  optionGroups: ProductOptionGroup[];
}

export interface ProductOptionValueUpdateRequest {
  id: number | null;
  value: string;
  sortOrder: number;
}

export interface ProductOptionGroupUpdateRequest {
  id: number | null;
  name: string;
  sortOrder: number;
  values: ProductOptionValueUpdateRequest[];
}

export interface ProductOptionUpdateRequest {
  optionGroups: ProductOptionGroupUpdateRequest[];
}

/* ========================================
   Seller Product Variants
======================================== */

export interface ProductVariantOptionValue {
  optionGroupId: number;
  optionGroupName: string;
  optionGroupSortOrder: number;

  optionValueId: number;
  optionValue: string;
  optionValueSortOrder: number;
}

export interface ProductVariant {
  id: number;
  skuCode: string;
  combinationKey: string;

  additionalPrice: number;
  stockQuantity: number;
  active: boolean;

  optionValues: ProductVariantOptionValue[];
}

export interface ProductVariantListResponse {
  productId: number;
  variants: ProductVariant[];
}

export interface ProductVariantUpdateItem {
  id: number | null;
  skuCode: string;
  optionValueIds: number[];
  additionalPrice: number;
  stockQuantity: number;
  active: boolean;
}

export interface ProductVariantUpdateRequest {
  variants: ProductVariantUpdateItem[];
}

export interface ProductOptionReferenceRequest {
  optionGroupSortOrder: number;
  optionValueSortOrder: number;
}

export interface ProductRegistrationVariantRequest {
  skuCode: string;
  options: ProductOptionReferenceRequest[];
  additionalPrice: number;
  stockQuantity: number;
  active: boolean;
}

export interface ProductRegistrationRequest {
  product: ProductCreateRequest;
  options: ProductOptionUpdateRequest;
  variants: ProductRegistrationVariantRequest[];
  giftOccasionIds: number[];
  draftId: number | null;
}

export interface ProductRegistrationResponse {
  productId: number;
  product: SellerProduct;
}

export interface ProductModificationVariantRequest {
  id: number | null;
  skuCode: string;
  options: ProductOptionReferenceRequest[];
  additionalPrice: number;
  stockQuantity: number;
  active: boolean;
}

export interface ProductModificationRequest {
  product: ProductUpdateRequest;
  options: ProductOptionUpdateRequest;
  variants: ProductModificationVariantRequest[];
  giftOccasionIds: number[];
  draftId: number | null;
}
