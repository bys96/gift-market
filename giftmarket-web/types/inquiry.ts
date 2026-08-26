export type ProductInquiryStatus = "WAITING" | "ANSWERED";

export interface ProductInquiry {
  id: number;
  productId: number;
  productName: string;
  title: string;
  content: string | null;
  isPrivate: boolean;
  masked: boolean;
  status: ProductInquiryStatus;
  writerName: string;
  mine: boolean;
  editable: boolean;
  answerContent: string | null;
  answeredAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProductInquiryPage {
  inquiries: ProductInquiry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ProductInquiryRequest { title: string; content: string; isPrivate: boolean; }
