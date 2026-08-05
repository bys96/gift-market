"use client";

import Image from "next/image";
import { useRouter } from "next/navigation";
import {
  type ChangeEvent,
  type FormEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import { createProduct, getCategories } from "@/lib/product-api";
import { uploadImage } from "@/lib/storage-api";
import { useAuthStore } from "@/stores/auth-store";
import type { Category, ProductCreateRequest } from "@/types/product";

const MAX_IMAGE_FILE_SIZE = 5 * 1024 * 1024;
const MAX_DETAIL_IMAGE_COUNT = 10;

const ALLOWED_IMAGE_TYPES = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
  "image/gif",
]);

interface DetailImageFile {
  id: string;
  file: File;
  previewUrl: string;
}

interface ProductFormState {
  categoryId: string;
  name: string;
  brandName: string;
  summary: string;
  description: string;
  price: string;
  stockQuantity: string;
  freeShipping: boolean;
  shippingFee: string;
}

const INITIAL_FORM_STATE: ProductFormState = {
  categoryId: "",
  name: "",
  brandName: "",
  summary: "",
  description: "",
  price: "",
  stockQuantity: "0",
  freeShipping: true,
  shippingFee: "0",
};

function createDetailImageId(): string {
  if (
    typeof crypto !== "undefined" &&
    typeof crypto.randomUUID === "function"
  ) {
    return crypto.randomUUID();
  }

  return `${Date.now()}-${Math.random()}`;
}

function validateImageFile(file: File): string | null {
  if (!ALLOWED_IMAGE_TYPES.has(file.type)) {
    return "JPG, PNG, WEBP, GIF 이미지만 업로드할 수 있습니다.";
  }

  if (file.size <= 0) {
    return "비어 있는 파일은 업로드할 수 없습니다.";
  }

  if (file.size > MAX_IMAGE_FILE_SIZE) {
    return "이미지 파일은 최대 5MB까지 업로드할 수 있습니다.";
  }

  return null;
}

function parseRequiredNumber(value: string, fieldName: string): number {
  if (!value.trim()) {
    throw new Error(`${fieldName}을(를) 입력해주세요.`);
  }

  const parsedValue = Number(value);

  if (!Number.isSafeInteger(parsedValue)) {
    throw new Error(`${fieldName}을(를) 올바르게 입력해주세요.`);
  }

  return parsedValue;
}

function flattenCategories(
  categories: Category[],
  depth = 0,
): {
  id: number;
  label: string;
}[] {
  return categories.flatMap((category) => {
    const currentCategory = {
      id: category.id,
      label: `${"— ".repeat(depth)}${category.name}`,
    };

    return [
      currentCategory,
      ...flattenCategories(category.children ?? [], depth + 1),
    ];
  });
}

export default function SellerProductCreatePage() {
  const router = useRouter();

  const representativeInputRef = useRef<HTMLInputElement>(null);

  const detailInputRef = useRef<HTMLInputElement>(null);

  const representativePreviewUrlRef = useRef<string | null>(null);

  const detailImagesRef = useRef<DetailImageFile[]>([]);

  const initialized = useAuthStore((state) => state.initialized);

  const user = useAuthStore((state) => state.user);

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [form, setForm] = useState<ProductFormState>(INITIAL_FORM_STATE);

  const [categories, setCategories] = useState<Category[]>([]);

  const [representativeFile, setRepresentativeFile] = useState<File | null>(
    null,
  );

  const [representativePreviewUrl, setRepresentativePreviewUrl] = useState<
    string | null
  >(null);

  const [detailImages, setDetailImages] = useState<DetailImageFile[]>([]);

  const [isLoadingCategories, setIsLoadingCategories] = useState(true);

  const [isSubmitting, setIsSubmitting] = useState(false);

  const [errorMessage, setErrorMessage] = useState("");

  const categoryOptions = useMemo(
    () => flattenCategories(categories),
    [categories],
  );

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }

    if (user.role !== "SELLER") {
      router.replace("/seller");
      return;
    }

    const loadCategories = async () => {
      try {
        setIsLoadingCategories(true);
        setErrorMessage("");

        const response = await getCategories();

        setCategories(response);
      } catch (error) {
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "카테고리를 불러오지 못했습니다.",
        );
      } finally {
        setIsLoadingCategories(false);
      }
    };

    loadCategories();
  }, [initialized, isAuthenticated, user, router]);

  useEffect(() => {
    representativePreviewUrlRef.current = representativePreviewUrl;
  }, [representativePreviewUrl]);

  useEffect(() => {
    detailImagesRef.current = detailImages;
  }, [detailImages]);

  useEffect(() => {
    return () => {
      const currentRepresentativePreviewUrl =
        representativePreviewUrlRef.current;

      if (currentRepresentativePreviewUrl) {
        URL.revokeObjectURL(currentRepresentativePreviewUrl);
      }

      detailImagesRef.current.forEach((image) => {
        URL.revokeObjectURL(image.previewUrl);
      });
    };
  }, []);

  const handleTextChange = (
    event: ChangeEvent<
      HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
    >,
  ) => {
    const { name, value } = event.target;

    setForm((currentForm) => ({
      ...currentForm,
      [name]: value,
    }));
  };

  const handleFreeShippingChange = (freeShipping: boolean) => {
    setForm((currentForm) => ({
      ...currentForm,
      freeShipping,
      shippingFee: freeShipping ? "0" : currentForm.shippingFee,
    }));
  };

  const handleRepresentativeImageChange = (
    event: ChangeEvent<HTMLInputElement>,
  ) => {
    const file = event.target.files?.[0];

    event.target.value = "";

    if (!file) {
      return;
    }

    const validationMessage = validateImageFile(file);

    if (validationMessage) {
      setErrorMessage(validationMessage);
      return;
    }

    if (representativePreviewUrl) {
      URL.revokeObjectURL(representativePreviewUrl);
    }

    setRepresentativeFile(file);
    setRepresentativePreviewUrl(URL.createObjectURL(file));

    setErrorMessage("");
  };

  const handleRemoveRepresentativeImage = () => {
    if (representativePreviewUrl) {
      URL.revokeObjectURL(representativePreviewUrl);
    }

    setRepresentativeFile(null);
    setRepresentativePreviewUrl(null);
  };

  const handleDetailImagesChange = (event: ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = Array.from(event.target.files ?? []);

    event.target.value = "";

    if (selectedFiles.length === 0) {
      return;
    }

    const availableCount = MAX_DETAIL_IMAGE_COUNT - detailImages.length;

    if (availableCount <= 0) {
      setErrorMessage(
        `상세 이미지는 최대 ${MAX_DETAIL_IMAGE_COUNT}장까지 등록할 수 있습니다.`,
      );
      return;
    }

    const filesToAdd = selectedFiles.slice(0, availableCount);

    for (const file of filesToAdd) {
      const validationMessage = validateImageFile(file);

      if (validationMessage) {
        setErrorMessage(`${file.name}: ${validationMessage}`);
        return;
      }
    }

    const newImages = filesToAdd.map((file) => ({
      id: createDetailImageId(),
      file,
      previewUrl: URL.createObjectURL(file),
    }));

    setDetailImages((currentImages) => [...currentImages, ...newImages]);

    if (selectedFiles.length > availableCount) {
      setErrorMessage(
        `상세 이미지는 최대 ${MAX_DETAIL_IMAGE_COUNT}장까지만 등록됩니다.`,
      );
      return;
    }

    setErrorMessage("");
  };

  const handleRemoveDetailImage = (imageId: string) => {
    setDetailImages((currentImages) => {
      const targetImage = currentImages.find((image) => image.id === imageId);

      if (targetImage) {
        URL.revokeObjectURL(targetImage.previewUrl);
      }

      return currentImages.filter((image) => image.id !== imageId);
    });
  };

  const handleMoveDetailImage = (imageIndex: number, direction: -1 | 1) => {
    const targetIndex = imageIndex + direction;

    if (targetIndex < 0 || targetIndex >= detailImages.length) {
      return;
    }

    setDetailImages((currentImages) => {
      const nextImages = [...currentImages];

      [nextImages[imageIndex], nextImages[targetIndex]] = [
        nextImages[targetIndex],
        nextImages[imageIndex],
      ];

      return nextImages;
    });
  };

  const validateForm = (startSale: boolean): ProductCreateRequest => {
    const categoryId = parseRequiredNumber(form.categoryId, "카테고리");

    const price = parseRequiredNumber(form.price, "판매가");

    const stockQuantity = parseRequiredNumber(form.stockQuantity, "재고 수량");

    const shippingFee = form.freeShipping
      ? 0
      : parseRequiredNumber(form.shippingFee, "배송비");

    if (!form.name.trim()) {
      throw new Error("상품명을 입력해주세요.");
    }

    if (form.name.trim().length > 200) {
      throw new Error("상품명은 200자 이하로 입력해주세요.");
    }

    if (form.brandName.trim().length > 100) {
      throw new Error("브랜드명은 100자 이하로 입력해주세요.");
    }

    if (form.summary.trim().length > 500) {
      throw new Error("상품 요약은 500자 이하로 입력해주세요.");
    }

    if (form.description.trim().length > 50000) {
      throw new Error("상품 상세 설명은 50000자 이하로 입력해주세요.");
    }

    if (price <= 0) {
      throw new Error("판매가는 0원보다 커야 합니다.");
    }

    if (stockQuantity < 0) {
      throw new Error("재고 수량은 0개 이상이어야 합니다.");
    }

    if (shippingFee < 0) {
      throw new Error("배송비는 0원 이상이어야 합니다.");
    }

    if (startSale && !representativeFile) {
      throw new Error("판매를 시작하려면 대표 이미지를 등록해주세요.");
    }

    if (startSale && !form.description.trim()) {
      throw new Error("판매를 시작하려면 상품 상세 설명을 입력해주세요.");
    }

    return {
      categoryId,
      name: form.name.trim(),
      brandName: form.brandName.trim() || null,
      summary: form.summary.trim() || null,
      description: form.description.trim() || null,
      price,
      stockQuantity,
      representativeImageKey: null,
      detailImageKeys: [],
      freeShipping: form.freeShipping,
      shippingFee,
      startSale,
    };
  };

  const handleCreateProduct = async (startSale: boolean) => {
    if (isSubmitting) {
      return;
    }

    try {
      setIsSubmitting(true);
      setErrorMessage("");

      const request = validateForm(startSale);

      const representativeImageKey = representativeFile
        ? await uploadImage(representativeFile, "PRODUCT_REPRESENTATIVE")
        : null;

      const detailImageKeys: string[] = [];

      for (const image of detailImages) {
        const detailImageKey = await uploadImage(
          image.file,
          "PRODUCT_DETAIL",
        );

        detailImageKeys.push(detailImageKey);
      }

      await createProduct({
        ...request,
        representativeImageKey,
        detailImageKeys,
      });

      router.replace("/seller/products");
      router.refresh();
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "상품 등록에 실패했습니다.",
      );

      window.scrollTo({
        top: 0,
        behavior: "smooth",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
  };

  if (!initialized || !isAuthenticated || !user || user.role !== "SELLER") {
    return (
      <main className="seller-product-form-page">
        <div className="common-inner">
          <div className="seller-application-loading">
            <span className="seller-application-loading-spinner" />
            <p>판매자 정보를 확인하고 있습니다.</p>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="seller-product-form-page">
      <div className="common-inner">
        <div className="seller-product-form-container">
          <header className="seller-product-form-header">
            <div>
              <button
                type="button"
                className="seller-product-form-back-button"
                onClick={() => {
                  router.push("/seller/products");
                }}
                disabled={isSubmitting}
              >
                ← 상품 관리
              </button>

              <p className="seller-product-form-header-label">
                PRODUCT REGISTRATION
              </p>

              <h1 className="seller-product-form-title">상품 등록</h1>

              <p className="seller-product-form-description">
                판매할 상품의 기본 정보와 이미지를 입력해주세요.
              </p>
            </div>
          </header>

          {errorMessage && (
            <div className="seller-product-form-error" role="alert">
              {errorMessage}
            </div>
          )}

          <form className="seller-product-form" onSubmit={handleSubmit}>
            <section className="seller-product-form-section">
              <header className="seller-product-form-section-header">
                <div>
                  <span className="seller-product-form-section-number">01</span>

                  <h2>기본 정보</h2>
                </div>

                <p>구매자에게 표시될 상품 정보를 입력합니다.</p>
              </header>

              <div className="seller-product-form-fields">
                <div className="seller-product-form-field">
                  <label htmlFor="categoryId">
                    카테고리
                    <span aria-hidden="true">*</span>
                  </label>

                  <select
                    id="categoryId"
                    name="categoryId"
                    value={form.categoryId}
                    onChange={handleTextChange}
                    disabled={isLoadingCategories || isSubmitting}
                  >
                    <option value="">
                      {isLoadingCategories
                        ? "카테고리를 불러오는 중입니다."
                        : "카테고리를 선택해주세요."}
                    </option>

                    {categoryOptions.map((category) => (
                      <option key={category.id} value={category.id}>
                        {category.label}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="seller-product-form-field">
                  <label htmlFor="name">
                    상품명
                    <span aria-hidden="true">*</span>
                  </label>

                  <input
                    id="name"
                    name="name"
                    type="text"
                    value={form.name}
                    onChange={handleTextChange}
                    placeholder="상품명을 입력해주세요."
                    maxLength={200}
                    disabled={isSubmitting}
                  />

                  <span className="seller-product-form-counter">
                    {form.name.length} / 200
                  </span>
                </div>

                <div className="seller-product-form-field">
                  <label htmlFor="brandName">브랜드명</label>

                  <input
                    id="brandName"
                    name="brandName"
                    type="text"
                    value={form.brandName}
                    onChange={handleTextChange}
                    placeholder="브랜드가 있다면 입력해주세요."
                    maxLength={100}
                    disabled={isSubmitting}
                  />
                </div>

                <div className="seller-product-form-field">
                  <label htmlFor="summary">상품 요약</label>

                  <textarea
                    id="summary"
                    name="summary"
                    value={form.summary}
                    onChange={handleTextChange}
                    placeholder="목록과 상품 상단에 표시할 간단한 설명을 입력해주세요."
                    maxLength={500}
                    rows={3}
                    disabled={isSubmitting}
                  />

                  <span className="seller-product-form-counter">
                    {form.summary.length} / 500
                  </span>
                </div>
              </div>
            </section>

            <section className="seller-product-form-section">
              <header className="seller-product-form-section-header">
                <div>
                  <span className="seller-product-form-section-number">02</span>

                  <h2>상품 이미지</h2>
                </div>

                <p>
                  JPG, PNG, WEBP, GIF 형식의 5MB 이하 이미지를 등록할 수
                  있습니다.
                </p>
              </header>

              <div className="seller-product-form-fields">
                <div className="seller-product-form-field">
                  <label>
                    대표 이미지
                    <span aria-hidden="true">*</span>
                  </label>

                  <input
                    ref={representativeInputRef}
                    type="file"
                    accept="image/jpeg,image/png,image/webp,image/gif"
                    className="seller-product-form-file-input"
                    onChange={handleRepresentativeImageChange}
                    disabled={isSubmitting}
                  />

                  {representativePreviewUrl ? (
                    <div className="seller-product-representative-preview">
                      <div className="seller-product-representative-image">
                        <Image
                          src={representativePreviewUrl}
                          alt="대표 이미지 미리보기"
                          fill
                          sizes="320px"
                          unoptimized
                        />
                      </div>

                      <div className="seller-product-image-information">
                        <strong>{representativeFile?.name}</strong>

                        <span>대표 이미지로 사용됩니다.</span>

                        <div className="seller-product-image-actions">
                          <button
                            type="button"
                            onClick={() => {
                              representativeInputRef.current?.click();
                            }}
                            disabled={isSubmitting}
                          >
                            이미지 변경
                          </button>

                          <button
                            type="button"
                            onClick={handleRemoveRepresentativeImage}
                            disabled={isSubmitting}
                          >
                            삭제
                          </button>
                        </div>
                      </div>
                    </div>
                  ) : (
                    <button
                      type="button"
                      className="seller-product-image-upload-box"
                      onClick={() => {
                        representativeInputRef.current?.click();
                      }}
                      disabled={isSubmitting}
                    >
                      <span className="seller-product-image-upload-icon">
                        +
                      </span>

                      <strong>대표 이미지 선택</strong>

                      <span>상품 목록과 상세 상단에 표시됩니다.</span>
                    </button>
                  )}
                </div>

                <div className="seller-product-form-field">
                  <div className="seller-product-form-field-label-row">
                    <label>상세 이미지</label>

                    <span>
                      {detailImages.length} / {MAX_DETAIL_IMAGE_COUNT}
                    </span>
                  </div>

                  <input
                    ref={detailInputRef}
                    type="file"
                    accept="image/jpeg,image/png,image/webp,image/gif"
                    multiple
                    className="seller-product-form-file-input"
                    onChange={handleDetailImagesChange}
                    disabled={
                      isSubmitting ||
                      detailImages.length >= MAX_DETAIL_IMAGE_COUNT
                    }
                  />

                  <div className="seller-product-detail-images">
                    {detailImages.map((image, index) => (
                      <article
                        key={image.id}
                        className="seller-product-detail-image-card"
                      >
                        <div className="seller-product-detail-image-preview">
                          <Image
                            src={image.previewUrl}
                            alt={`상세 이미지 ${index + 1}`}
                            fill
                            sizes="180px"
                            unoptimized
                          />

                          <span>{index + 1}</span>
                        </div>

                        <div className="seller-product-detail-image-card-actions">
                          <button
                            type="button"
                            aria-label={`상세 이미지 ${index + 1} 앞으로 이동`}
                            onClick={() => {
                              handleMoveDetailImage(index, -1);
                            }}
                            disabled={isSubmitting || index === 0}
                          >
                            ←
                          </button>

                          <button
                            type="button"
                            aria-label={`상세 이미지 ${index + 1} 뒤로 이동`}
                            onClick={() => {
                              handleMoveDetailImage(index, 1);
                            }}
                            disabled={
                              isSubmitting || index === detailImages.length - 1
                            }
                          >
                            →
                          </button>

                          <button
                            type="button"
                            onClick={() => {
                              handleRemoveDetailImage(image.id);
                            }}
                            disabled={isSubmitting}
                          >
                            삭제
                          </button>
                        </div>
                      </article>
                    ))}

                    {detailImages.length < MAX_DETAIL_IMAGE_COUNT && (
                      <button
                        type="button"
                        className="seller-product-detail-image-add"
                        onClick={() => {
                          detailInputRef.current?.click();
                        }}
                        disabled={isSubmitting}
                      >
                        <span>+</span>
                        <strong>이미지 추가</strong>
                      </button>
                    )}
                  </div>

                  <p className="seller-product-form-help">
                    등록 순서대로 상품 상세 화면에 표시됩니다.
                  </p>
                </div>
              </div>
            </section>

            <section className="seller-product-form-section">
              <header className="seller-product-form-section-header">
                <div>
                  <span className="seller-product-form-section-number">03</span>

                  <h2>가격 및 재고</h2>
                </div>

                <p>현재는 옵션 없이 상품 단위 재고를 관리합니다.</p>
              </header>

              <div className="seller-product-form-grid">
                <div className="seller-product-form-field">
                  <label htmlFor="price">
                    판매가
                    <span aria-hidden="true">*</span>
                  </label>

                  <div className="seller-product-form-input-unit">
                    <input
                      id="price"
                      name="price"
                      type="number"
                      min="1"
                      step="1"
                      value={form.price}
                      onChange={handleTextChange}
                      placeholder="0"
                      disabled={isSubmitting}
                    />

                    <span>원</span>
                  </div>
                </div>

                <div className="seller-product-form-field">
                  <label htmlFor="stockQuantity">
                    재고 수량
                    <span aria-hidden="true">*</span>
                  </label>

                  <div className="seller-product-form-input-unit">
                    <input
                      id="stockQuantity"
                      name="stockQuantity"
                      type="number"
                      min="0"
                      step="1"
                      value={form.stockQuantity}
                      onChange={handleTextChange}
                      disabled={isSubmitting}
                    />

                    <span>개</span>
                  </div>
                </div>
              </div>
            </section>

            <section className="seller-product-form-section">
              <header className="seller-product-form-section-header">
                <div>
                  <span className="seller-product-form-section-number">04</span>

                  <h2>배송 정보</h2>
                </div>

                <p>상품에 적용할 기본 배송비를 설정합니다.</p>
              </header>

              <div className="seller-product-form-fields">
                <div className="seller-product-form-shipping-options">
                  <label
                    className={`seller-product-shipping-option ${
                      form.freeShipping
                        ? "seller-product-shipping-option-active"
                        : ""
                    }`}
                  >
                    <input
                      type="radio"
                      name="shippingType"
                      checked={form.freeShipping}
                      onChange={() => {
                        handleFreeShippingChange(true);
                      }}
                      disabled={isSubmitting}
                    />

                    <strong>무료배송</strong>
                    <span>구매자에게 배송비가 부과되지 않습니다.</span>
                  </label>

                  <label
                    className={`seller-product-shipping-option ${
                      !form.freeShipping
                        ? "seller-product-shipping-option-active"
                        : ""
                    }`}
                  >
                    <input
                      type="radio"
                      name="shippingType"
                      checked={!form.freeShipping}
                      onChange={() => {
                        handleFreeShippingChange(false);
                      }}
                      disabled={isSubmitting}
                    />

                    <strong>유료배송</strong>
                    <span>설정한 배송비를 주문에 적용합니다.</span>
                  </label>
                </div>

                {!form.freeShipping && (
                  <div className="seller-product-form-field">
                    <label htmlFor="shippingFee">
                      배송비
                      <span aria-hidden="true">*</span>
                    </label>

                    <div className="seller-product-form-input-unit">
                      <input
                        id="shippingFee"
                        name="shippingFee"
                        type="number"
                        min="0"
                        step="1"
                        value={form.shippingFee}
                        onChange={handleTextChange}
                        disabled={isSubmitting}
                      />

                      <span>원</span>
                    </div>
                  </div>
                )}
              </div>
            </section>

            <section className="seller-product-form-section">
              <header className="seller-product-form-section-header">
                <div>
                  <span className="seller-product-form-section-number">05</span>

                  <h2>상세 설명</h2>
                </div>

                <p>판매 시작 시 상세 설명은 필수입니다.</p>
              </header>

              <div className="seller-product-form-field">
                <label htmlFor="description">상품 상세 설명</label>

                <textarea
                  id="description"
                  name="description"
                  value={form.description}
                  onChange={handleTextChange}
                  placeholder="상품의 특징, 구성, 사용 방법, 주의사항 등을 입력해주세요."
                  maxLength={50000}
                  rows={14}
                  disabled={isSubmitting}
                />

                <span className="seller-product-form-counter">
                  {form.description.length} / 50000
                </span>
              </div>
            </section>

            <div className="seller-product-form-actions">
              <button
                type="button"
                className="seller-product-form-cancel-button"
                onClick={() => {
                  router.push("/seller/products");
                }}
                disabled={isSubmitting}
              >
                취소
              </button>

              <div>
                <button
                  type="button"
                  className="seller-product-form-draft-button"
                  onClick={() => {
                    handleCreateProduct(false);
                  }}
                  disabled={isSubmitting}
                >
                  {isSubmitting ? "저장 중..." : "임시 저장"}
                </button>

                <button
                  type="button"
                  className="seller-product-form-submit-button"
                  onClick={() => {
                    handleCreateProduct(true);
                  }}
                  disabled={isSubmitting}
                >
                  {isSubmitting ? "등록 중..." : "판매 시작"}
                </button>
              </div>
            </div>
          </form>
        </div>
      </div>
    </main>
  );
}
