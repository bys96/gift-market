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

import ProductEditor from "@/components/seller/ProductEditor";
import {
  createProduct,
  getCategories,
  updateProduct,
  updateProductStatus,
} from "@/lib/product-api";
import { uploadImage } from "@/lib/storage-api";
import type {
  Category,
  ProductCreateRequest,
  ProductUpdateRequest,
  SellerProduct,
} from "@/types/product";
import { resolveImageUrl } from "@/utils/image-url";

const MAX_IMAGE_FILE_SIZE = 5 * 1024 * 1024;
const MAX_GALLERY_IMAGE_COUNT = 10;

const DEFAULT_SHIPPING_PREPARATION_DAYS = "3";
const DEFAULT_RETURN_SHIPPING_FEE = "3000";
const DEFAULT_EXCHANGE_SHIPPING_FEE = "6000";

const ALLOWED_IMAGE_TYPES = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
  "image/gif",
]);

type ProductFormMode = "create" | "edit";

type GalleryImageItem =
  | {
      id: string;
      kind: "existing";
      objectKey: string;
      previewUrl: string;
    }
  | {
      id: string;
      kind: "new";
      file: File;
      previewUrl: string;
    };

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
  shippingPreparationDays: string;
  returnShippingFee: string;
  exchangeShippingFee: string;
}

interface ProductFormProps {
  mode: ProductFormMode;
  initialProduct?: SellerProduct;
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
  shippingPreparationDays: DEFAULT_SHIPPING_PREPARATION_DAYS,
  returnShippingFee: DEFAULT_RETURN_SHIPPING_FEE,
  exchangeShippingFee: DEFAULT_EXCHANGE_SHIPPING_FEE,
};

function createImageId(): string {
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

function findRootCategoryId(
  categories: Category[],
  categoryId: number,
): number | null {
  for (const category of categories) {
    if (category.id === categoryId) {
      return category.id;
    }

    if ((category.children ?? []).some((child) => child.id === categoryId)) {
      return category.id;
    }
  }

  return null;
}

function createInitialForm(product?: SellerProduct): ProductFormState {
  if (!product) {
    return INITIAL_FORM_STATE;
  }

  return {
    categoryId: String(product.categoryId),
    name: product.name,
    brandName: product.brandName ?? "",
    summary: product.summary ?? "",
    description: product.description ?? "",
    price: String(product.price),
    stockQuantity: String(product.stockQuantity),
    freeShipping: product.freeShipping,
    shippingFee: String(product.shippingFee),
    shippingPreparationDays: String(
      product.shippingPreparationDays ?? DEFAULT_SHIPPING_PREPARATION_DAYS,
    ),
    returnShippingFee: String(
      product.returnShippingFee ?? DEFAULT_RETURN_SHIPPING_FEE,
    ),
    exchangeShippingFee: String(
      product.exchangeShippingFee ?? DEFAULT_EXCHANGE_SHIPPING_FEE,
    ),
  };
}

function createExistingGalleryImages(
  product?: SellerProduct,
): GalleryImageItem[] {
  return (product?.galleryImageKeys ?? []).flatMap((objectKey) => {
    const previewUrl = resolveImageUrl(objectKey);

    if (!previewUrl) {
      return [];
    }

    return [
      {
        id: createImageId(),
        kind: "existing" as const,
        objectKey,
        previewUrl,
      },
    ];
  });
}

export default function ProductForm({
  mode,
  initialProduct,
}: ProductFormProps) {
  const router = useRouter();

  const representativeInputRef = useRef<HTMLInputElement>(null);
  const galleryInputRef = useRef<HTMLInputElement>(null);
  const representativeBlobUrlRef = useRef<string | null>(null);
  const galleryImagesRef = useRef<GalleryImageItem[]>([]);

  const [form, setForm] = useState<ProductFormState>(() =>
    createInitialForm(initialProduct),
  );
  const [categories, setCategories] = useState<Category[]>([]);
  const [representativeFile, setRepresentativeFile] = useState<File | null>(
    null,
  );
  const [representativeImageKey, setRepresentativeImageKey] = useState<
    string | null
  >(initialProduct?.representativeImageKey ?? null);
  const [representativePreviewUrl, setRepresentativePreviewUrl] = useState<
    string | null
  >(() => resolveImageUrl(initialProduct?.representativeImageKey));
  const [galleryImages, setGalleryImages] = useState<GalleryImageItem[]>(() =>
    createExistingGalleryImages(initialProduct),
  );
  const [isLoadingCategories, setIsLoadingCategories] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const [selectedRootCategoryId, setSelectedRootCategoryId] = useState("");

  const selectedRootCategory = useMemo(
    () =>
      categories.find(
        (category) => String(category.id) === selectedRootCategoryId,
      ) ?? null,
    [categories, selectedRootCategoryId],
  );

  const childCategories = selectedRootCategory?.children ?? [];

  useEffect(() => {
    const loadCategories = async () => {
      try {
        setIsLoadingCategories(true);
        setErrorMessage("");
        setCategories(await getCategories());
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

    void loadCategories();
  }, []);

  useEffect(() => {
    if (categories.length === 0) {
      return;
    }

    if (form.categoryId) {
      const rootCategoryId = findRootCategoryId(
        categories,
        Number(form.categoryId),
      );

      if (rootCategoryId) {
        setSelectedRootCategoryId(String(rootCategoryId));
        return;
      }
    }

    setSelectedRootCategoryId("");
  }, [categories, form.categoryId]);

  useEffect(() => {
    galleryImagesRef.current = galleryImages;
  }, [galleryImages]);

  useEffect(() => {
    return () => {
      if (representativeBlobUrlRef.current) {
        URL.revokeObjectURL(representativeBlobUrlRef.current);
      }

      galleryImagesRef.current.forEach((image) => {
        if (image.kind === "new") {
          URL.revokeObjectURL(image.previewUrl);
        }
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

  const handleRootCategoryChange = (event: ChangeEvent<HTMLSelectElement>) => {
    const rootCategoryId = event.target.value;
    const rootCategory = categories.find(
      (category) => String(category.id) === rootCategoryId,
    );

    setSelectedRootCategoryId(rootCategoryId);
    setForm((currentForm) => ({
      ...currentForm,
      categoryId:
        rootCategory && (rootCategory.children ?? []).length === 0
          ? rootCategoryId
          : "",
    }));
  };

  const handleChildCategoryChange = (event: ChangeEvent<HTMLSelectElement>) => {
    setForm((currentForm) => ({
      ...currentForm,
      categoryId: event.target.value,
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

    if (representativeBlobUrlRef.current) {
      URL.revokeObjectURL(representativeBlobUrlRef.current);
    }

    const previewUrl = URL.createObjectURL(file);
    representativeBlobUrlRef.current = previewUrl;

    setRepresentativeFile(file);
    setRepresentativeImageKey(null);
    setRepresentativePreviewUrl(previewUrl);
    setErrorMessage("");
  };

  const handleRemoveRepresentativeImage = () => {
    if (representativeBlobUrlRef.current) {
      URL.revokeObjectURL(representativeBlobUrlRef.current);
      representativeBlobUrlRef.current = null;
    }

    setRepresentativeFile(null);
    setRepresentativeImageKey(null);
    setRepresentativePreviewUrl(null);
  };

  const handleGalleryImagesChange = (event: ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = Array.from(event.target.files ?? []);
    event.target.value = "";

    if (selectedFiles.length === 0) {
      return;
    }

    const availableCount = MAX_GALLERY_IMAGE_COUNT - galleryImages.length;

    if (availableCount <= 0) {
      setErrorMessage(
        `갤러리 이미지는 최대 ${MAX_GALLERY_IMAGE_COUNT}장까지 등록할 수 있습니다.`,
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

    const newImages: GalleryImageItem[] = filesToAdd.map((file) => ({
      id: createImageId(),
      kind: "new",
      file,
      previewUrl: URL.createObjectURL(file),
    }));

    setGalleryImages((currentImages) => [...currentImages, ...newImages]);

    setErrorMessage(
      selectedFiles.length > availableCount
        ? `갤러리 이미지는 최대 ${MAX_GALLERY_IMAGE_COUNT}장까지만 등록됩니다.`
        : "",
    );
  };

  const handleRemoveGalleryImage = (imageId: string) => {
    setGalleryImages((currentImages) => {
      const targetImage = currentImages.find((image) => image.id === imageId);

      if (targetImage?.kind === "new") {
        URL.revokeObjectURL(targetImage.previewUrl);
      }

      return currentImages.filter((image) => image.id !== imageId);
    });
  };

  const handleMoveGalleryImage = (imageIndex: number, direction: -1 | 1) => {
    const targetIndex = imageIndex + direction;

    if (targetIndex < 0 || targetIndex >= galleryImages.length) {
      return;
    }

    setGalleryImages((currentImages) => {
      const nextImages = [...currentImages];
      [nextImages[imageIndex], nextImages[targetIndex]] = [
        nextImages[targetIndex],
        nextImages[imageIndex],
      ];
      return nextImages;
    });
  };

  const validateCommonRequest = () => {
    const categoryId = parseRequiredNumber(form.categoryId, "카테고리");
    const price = parseRequiredNumber(form.price, "판매가");
    const stockQuantity = parseRequiredNumber(form.stockQuantity, "재고 수량");
    const shippingFee = form.freeShipping
      ? 0
      : parseRequiredNumber(form.shippingFee, "배송비");

    const shippingPreparationDays = parseRequiredNumber(
      form.shippingPreparationDays,
      "출고 소요일",
    );
    const returnShippingFee = parseRequiredNumber(
      form.returnShippingFee,
      "반품 배송비",
    );
    const exchangeShippingFee = parseRequiredNumber(
      form.exchangeShippingFee,
      "교환 배송비",
    );

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

    if (shippingPreparationDays < 1 || shippingPreparationDays > 30) {
      throw new Error("출고 소요일은 1일 이상 30일 이하로 입력해주세요.");
    }

    if (returnShippingFee < 0) {
      throw new Error("반품 배송비는 0원 이상이어야 합니다.");
    }

    if (exchangeShippingFee < 0) {
      throw new Error("교환 배송비는 0원 이상이어야 합니다.");
    }

    return {
      categoryId,
      name: form.name.trim(),
      brandName: form.brandName.trim() || null,
      summary: form.summary.trim() || null,
      description: form.description.trim() || null,
      price,
      stockQuantity,
      freeShipping: form.freeShipping,
      shippingFee,
      shippingPreparationDays,
      returnShippingFee,
      exchangeShippingFee,
    };
  };

  const uploadImages = async () => {
    const nextRepresentativeImageKey = representativeFile
      ? await uploadImage(representativeFile, "PRODUCT_REPRESENTATIVE")
      : representativeImageKey;

    const galleryImageKeys: string[] = [];

    for (const image of galleryImages) {
      if (image.kind === "existing") {
        galleryImageKeys.push(image.objectKey);
        continue;
      }

      galleryImageKeys.push(await uploadImage(image.file, "PRODUCT_GALLERY"));
    }

    return {
      representativeImageKey: nextRepresentativeImageKey,
      galleryImageKeys,
    };
  };

  const handleSave = async (startSale: boolean) => {
    if (isSubmitting) {
      return;
    }

    try {
      setIsSubmitting(true);
      setErrorMessage("");

      const commonRequest = validateCommonRequest();

      if (startSale && !representativePreviewUrl) {
        throw new Error("판매를 시작하려면 대표 이미지를 등록해주세요.");
      }

      if (startSale && !form.description.trim()) {
        throw new Error("판매를 시작하려면 상품 상세 설명을 입력해주세요.");
      }

      const imageRequest = await uploadImages();

      if (mode === "create") {
        const request: ProductCreateRequest = {
          ...commonRequest,
          ...imageRequest,
          startSale,
        };

        await createProduct(request);
      } else {
        if (!initialProduct) {
          throw new Error("수정할 상품 정보를 확인할 수 없습니다.");
        }

        const request: ProductUpdateRequest = {
          ...commonRequest,
          ...imageRequest,
        };

        await updateProduct(initialProduct.id, request);
        await updateProductStatus(initialProduct.id, {
          status: startSale ? "ON_SALE" : "DRAFT",
        });
      }

      router.replace("/seller/products");
      router.refresh();
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : mode === "create"
            ? "상품 등록에 실패했습니다."
            : "상품 수정에 실패했습니다.",
      );

      window.scrollTo({ top: 0, behavior: "smooth" });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
  };

  const isEditMode = mode === "edit";

  return (
    <main className="seller-product-form-page">
      <div className="common-inner">
        <div className="seller-product-form-container">
          <header className="seller-product-form-header">
            <div>
              <button
                type="button"
                className="seller-product-form-back-button"
                onClick={() => router.push("/seller/products")}
                disabled={isSubmitting}
              >
                ← 상품 관리
              </button>

              <p className="seller-product-form-header-label">
                {isEditMode ? "PRODUCT EDIT" : "PRODUCT REGISTRATION"}
              </p>

              <h1 className="seller-product-form-title">
                {isEditMode ? "상품 수정" : "상품 등록"}
              </h1>

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
                  <label htmlFor="rootCategoryId">카테고리 *</label>
                  <div className="seller-product-category-selects">
                    <select
                      id="rootCategoryId"
                      value={selectedRootCategoryId}
                      onChange={handleRootCategoryChange}
                      disabled={isLoadingCategories || isSubmitting}
                    >
                      <option value="">
                        {isLoadingCategories
                          ? "카테고리를 불러오는 중입니다."
                          : "대분류를 선택해주세요."}
                      </option>
                      {categories.map((category) => (
                        <option key={category.id} value={category.id}>
                          {category.name}
                        </option>
                      ))}
                    </select>

                    <select
                      id="categoryId"
                      name="categoryId"
                      value={form.categoryId}
                      onChange={handleChildCategoryChange}
                      disabled={
                        isSubmitting ||
                        !selectedRootCategory ||
                        childCategories.length === 0
                      }
                    >
                      <option value="">
                        {!selectedRootCategory
                          ? "대분류를 먼저 선택해주세요."
                          : childCategories.length === 0
                            ? "세부 카테고리가 없습니다."
                            : "세부 카테고리를 선택해주세요."}
                      </option>
                      {childCategories.map((category) => (
                        <option key={category.id} value={category.id}>
                          {category.name}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="seller-product-form-field">
                  <label htmlFor="name">상품명 *</label>
                  <input
                    id="name"
                    name="name"
                    value={form.name}
                    onChange={handleTextChange}
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
                    value={form.brandName}
                    onChange={handleTextChange}
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
                <p>JPG, PNG, WEBP, GIF 형식의 5MB 이하 이미지</p>
              </header>

              <div className="seller-product-form-fields">
                <div className="seller-product-form-field">
                  <label>대표 이미지 *</label>
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
                        <strong>
                          {representativeFile?.name ?? "기존 대표 이미지"}
                        </strong>
                        <span>대표 이미지로 사용됩니다.</span>

                        <div className="seller-product-image-actions">
                          <button
                            type="button"
                            onClick={() =>
                              representativeInputRef.current?.click()
                            }
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
                      onClick={() => representativeInputRef.current?.click()}
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
                    <label>상품 갤러리</label>
                    <span>
                      {galleryImages.length} / {MAX_GALLERY_IMAGE_COUNT}
                    </span>
                  </div>

                  <input
                    ref={galleryInputRef}
                    type="file"
                    accept="image/jpeg,image/png,image/webp,image/gif"
                    multiple
                    className="seller-product-form-file-input"
                    onChange={handleGalleryImagesChange}
                    disabled={
                      isSubmitting ||
                      galleryImages.length >= MAX_GALLERY_IMAGE_COUNT
                    }
                  />

                  <div className="seller-product-gallery-images">
                    {galleryImages.map((image, index) => (
                      <article
                        key={image.id}
                        className="seller-product-gallery-image-card"
                      >
                        <div className="seller-product-gallery-image-preview">
                          <Image
                            src={image.previewUrl}
                            alt={`갤러리 이미지 ${index + 1}`}
                            fill
                            sizes="180px"
                            unoptimized
                          />
                          <span>{index + 1}</span>
                        </div>

                        <div className="seller-product-gallery-image-card-actions">
                          <button
                            type="button"
                            onClick={() => handleMoveGalleryImage(index, -1)}
                            disabled={isSubmitting || index === 0}
                          >
                            ←
                          </button>

                          <button
                            type="button"
                            onClick={() => handleMoveGalleryImage(index, 1)}
                            disabled={
                              isSubmitting || index === galleryImages.length - 1
                            }
                          >
                            →
                          </button>

                          <button
                            type="button"
                            onClick={() => handleRemoveGalleryImage(image.id)}
                            disabled={isSubmitting}
                          >
                            삭제
                          </button>
                        </div>
                      </article>
                    ))}

                    {galleryImages.length < MAX_GALLERY_IMAGE_COUNT && (
                      <button
                        type="button"
                        className="seller-product-gallery-image-add"
                        onClick={() => galleryInputRef.current?.click()}
                        disabled={isSubmitting}
                      >
                        <span>+</span>
                        <strong>이미지 추가</strong>
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </section>

            <section className="seller-product-form-section">
              <header className="seller-product-form-section-header">
                <div>
                  <span className="seller-product-form-section-number">03</span>
                  <h2>상품 상세 설명</h2>
                </div>
                <p>텍스트와 이미지를 자유롭게 배치할 수 있습니다.</p>
              </header>

              <ProductEditor
                value={form.description}
                onChange={(description) =>
                  setForm((currentForm) => ({
                    ...currentForm,
                    description,
                  }))
                }
                disabled={isSubmitting}
              />
            </section>

            <section className="seller-product-form-section">
              <header className="seller-product-form-section-header">
                <div>
                  <span className="seller-product-form-section-number">04</span>
                  <h2>판매 및 배송</h2>
                </div>
                <p>가격, 재고 및 배송 정책을 설정합니다.</p>
              </header>

              <div className="seller-product-form-fields seller-product-form-grid">
                <div className="seller-product-form-field">
                  <label htmlFor="price">판매가 *</label>
                  <div className="seller-product-form-input-unit">
                    <input
                      id="price"
                      name="price"
                      type="number"
                      min="1"
                      value={form.price}
                      onChange={handleTextChange}
                      disabled={isSubmitting}
                    />
                    <span>원</span>
                  </div>
                </div>

                <div className="seller-product-form-field">
                  <label htmlFor="stockQuantity">재고 수량 *</label>
                  <input
                    id="stockQuantity"
                    name="stockQuantity"
                    type="number"
                    min="0"
                    value={form.stockQuantity}
                    onChange={handleTextChange}
                    disabled={isSubmitting}
                  />
                </div>

                <div className="seller-product-form-field seller-product-form-field-full">
                  <label>배송 방식 *</label>

                  <div className="seller-product-form-shipping-options">
                    <label
                      className={[
                        "seller-product-shipping-option",
                        form.freeShipping
                          ? "seller-product-shipping-option-active"
                          : "",
                      ]
                        .filter(Boolean)
                        .join(" ")}
                    >
                      <input
                        type="radio"
                        name="shippingType"
                        checked={form.freeShipping}
                        onChange={() => handleFreeShippingChange(true)}
                        disabled={isSubmitting}
                      />
                      <strong>무료배송</strong>
                      <span>구매자에게 별도의 배송비를 받지 않습니다.</span>
                    </label>

                    <label
                      className={[
                        "seller-product-shipping-option",
                        !form.freeShipping
                          ? "seller-product-shipping-option-active"
                          : "",
                      ]
                        .filter(Boolean)
                        .join(" ")}
                    >
                      <input
                        type="radio"
                        name="shippingType"
                        checked={!form.freeShipping}
                        onChange={() => handleFreeShippingChange(false)}
                        disabled={isSubmitting}
                      />
                      <strong>유료배송</strong>
                      <span>설정한 배송비를 주문 금액에 추가합니다.</span>
                    </label>
                  </div>
                </div>

                {!form.freeShipping && (
                  <div className="seller-product-form-field">
                    <label htmlFor="shippingFee">배송비 *</label>
                    <div className="seller-product-form-input-unit">
                      <input
                        id="shippingFee"
                        name="shippingFee"
                        type="number"
                        min="0"
                        value={form.shippingFee}
                        onChange={handleTextChange}
                        disabled={isSubmitting}
                      />
                      <span>원</span>
                    </div>
                  </div>
                )}

                <div className="seller-product-form-field">
                  <label htmlFor="shippingPreparationDays">출고 소요일 *</label>

                  <div className="seller-product-form-input-unit">
                    <input
                      id="shippingPreparationDays"
                      name="shippingPreparationDays"
                      type="number"
                      min="1"
                      max="30"
                      value={form.shippingPreparationDays}
                      onChange={handleTextChange}
                      disabled={isSubmitting}
                    />
                    <span>일</span>
                  </div>

                  <span className="seller-product-form-help">
                    결제 완료 후 상품 출고까지 예상되는 기간입니다.
                  </span>
                </div>

                <div className="seller-product-form-field">
                  <label htmlFor="returnShippingFee">반품 배송비 *</label>

                  <div className="seller-product-form-input-unit">
                    <input
                      id="returnShippingFee"
                      name="returnShippingFee"
                      type="number"
                      min="0"
                      value={form.returnShippingFee}
                      onChange={handleTextChange}
                      disabled={isSubmitting}
                    />
                    <span>원</span>
                  </div>

                  <span className="seller-product-form-help">
                    구매자 귀책 사유로 반품할 때 부과되는 배송비입니다.
                  </span>
                </div>

                <div className="seller-product-form-field">
                  <label htmlFor="exchangeShippingFee">교환 배송비 *</label>

                  <div className="seller-product-form-input-unit">
                    <input
                      id="exchangeShippingFee"
                      name="exchangeShippingFee"
                      type="number"
                      min="0"
                      value={form.exchangeShippingFee}
                      onChange={handleTextChange}
                      disabled={isSubmitting}
                    />
                    <span>원</span>
                  </div>

                  <span className="seller-product-form-help">
                    구매자 귀책 사유로 교환할 때 발생하는 왕복 배송비입니다.
                  </span>
                </div>
              </div>
            </section>

            <div className="seller-product-form-actions">
              <div className="seller-product-form-actions-left">
                <button
                  type="button"
                  className="seller-product-form-draft-button"
                  onClick={() => void handleSave(false)}
                  disabled={isSubmitting}
                >
                  {isSubmitting ? "저장 중..." : "임시 저장"}
                </button>
              </div>

              <div className="seller-product-form-actions-right">
                <button
                  type="button"
                  className="seller-product-form-cancel-button"
                  onClick={() => router.push("/seller/products")}
                  disabled={isSubmitting}
                >
                  취소
                </button>

                <button
                  type="button"
                  className="seller-product-form-submit-button"
                  onClick={() => void handleSave(true)}
                  disabled={isSubmitting}
                >
                  {isSubmitting ? "저장 중..." : "판매 시작"}
                </button>
              </div>
            </div>
          </form>
        </div>
      </div>
    </main>
  );
}
