export interface Review {
  reviewId: number; rating: number; content: string; writerName: string;
  productName: string; optionSnapshot: string | null; unitPriceSnapshot: number;
  images: string[]; createdAt: string; updatedAt: string; mine: boolean;
}
export interface ReviewPage { reviews: Review[]; page: number; size: number; totalElements: number; totalPages: number; averageRating: number; reviewCount: number; }
export interface ReviewEligibility { orderItemId: number; reviewId: number | null; eligible: boolean; productName: string; optionSnapshot: string | null; }
export interface ReviewRequest { orderItemId: number; rating: number; content: string; imageObjectKeys: string[]; }
export interface ReviewEdit { review: Review; imageObjectKeys: string[]; }
