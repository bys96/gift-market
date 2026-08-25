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
import ProductOptionManager, {
  type ProductOptionEditorState,
} from "@/components/seller/ProductOptionManager";
import { getCategories, modifyProduct, registerProduct } from "@/lib/product-api";
import {
  createProductDraft,
  getProductDraftByProductId,
  updateProductDraft,
} from "@/lib/product-draft-api";
import { uploadImage } from "@/lib/storage-api";
import type {
  Category,
  ProductModificationRequest,
  ProductModificationVariantRequest,
  ProductOptionUpdateRequest,
  ProductRegistrationRequest,
  ProductRegistrationVariantRequest,
  SellerProduct,
} from "@/types/product";
import type { ProductDraft, ProductDraftData } from "@/types/product-draft";
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
  initialDraft?: ProductDraft | null;
}

const INITIAL_FORM_STATE: ProductFormState = {
  categoryId: "",
  name: "",
  brandName: "",
  summary: "",
  description: "",
  price: "",
  stockQuantity: "",
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

const INITIAL_OPTION_EDITOR_STATE: ProductOptionEditorState = {
  enabled: false,
  optionGroups: [],
  variants: [],
  initialized: true,
};

export default function ProductForm({
  mode,
  initialProduct,
  initialDraft = null,
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

  const [isSavingDraft, setIsSavingDraft] = useState(false);

  const [errorMessage, setErrorMessage] = useState("");

  const [draftId, setDraftId] = useState<number | null>(
    initialDraft?.id ?? null,
  );

  const [availableDraft, setAvailableDraft] = useState<ProductDraft | null>(
    initialDraft,
  );

  const [draftOptionState, setDraftOptionState] =
    useState<ProductOptionEditorState | null>(null);

  const [draftOptionRevision, setDraftOptionRevision] = useState(0);
  const [optionEditorState, setOptionEditorState] =
    useState<ProductOptionEditorState>(() => ({
      ...INITIAL_OPTION_EDITOR_STATE,
      initialized: mode === "create",
    }));

  const [selectedRootCategoryId, setSelectedRootCategoryId] = useState("");

  const [editorRevision, setEditorRevision] = useState(0);

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
    if (mode !== "edit" || !initialProduct) {
      return;
    }

    let cancelled = false;

    const loadExistingDraft = async () => {
      try {
        const draft = await getProductDraftByProductId(initialProduct.id);

        if (cancelled) {
          return;
        }

        setAvailableDraft(draft);
        setDraftId(draft.id);
      } catch {
        // 기존 수정 Draft가 없는 경우 정상
      }
    };

    void loadExistingDraft();

    return () => {
      cancelled = true;
    };
  }, [mode, initialProduct]);

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
        // 카테고리 API 결과와 controlled 상위 카테고리 선택값을 동기화한다.
        // eslint-disable-next-line react-hooks/set-state-in-effect
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

  const validateCommonRequest = (stockQuantityOverride?: number) => {
    const categoryId = parseRequiredNumber(form.categoryId, "카테고리");
    const price = parseRequiredNumber(form.price, "판매가");
    const stockQuantity =
      stockQuantityOverride ??
      parseRequiredNumber(form.stockQuantity, "재고 수량");
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

  const validateOptionConfiguration = () => {
    if (!optionEditorState.initialized) {
      throw new Error("상품 옵션 정보를 아직 불러오는 중입니다.");
    }

    const hasPersistedOptionData =
      optionEditorState.optionGroups.some((group) => group.id !== null) ||
      optionEditorState.variants.some((variant) => variant.id !== null);

    if (!optionEditorState.enabled) {
      if (hasPersistedOptionData) {
        throw new Error(
          "기존 옵션 상품은 옵션 없음 상품으로 변경할 수 없습니다. 기존 SKU를 유지한 상태에서 옵션을 수정해주세요.",
        );
      }

      return {
        totalStockQuantity: undefined as number | undefined,
        optionRequest: null as ProductOptionUpdateRequest | null,
      };
    }

    if (optionEditorState.optionGroups.length === 0) {
      throw new Error("옵션을 1개 이상 등록해주세요.");
    }

    const groupNames = new Set<string>();

    const optionGroups = optionEditorState.optionGroups.map(
      (group, groupIndex) => {
        const name = group.name.trim();

        if (!name) {
          throw new Error(`옵션 ${groupIndex + 1}의 이름을 입력해주세요.`);
        }

        const normalizedName = name.toLocaleLowerCase("ko-KR");

        if (groupNames.has(normalizedName)) {
          throw new Error("동일한 옵션명을 중복 등록할 수 없습니다.");
        }

        groupNames.add(normalizedName);

        if (group.values.length === 0) {
          throw new Error(`${name} 옵션 값을 1개 이상 등록해주세요.`);
        }

        const optionValues = new Set<string>();

        const values = group.values.map((optionValue, valueIndex) => {
          const value = optionValue.value.trim();

          if (!value) {
            throw new Error(
              `${name} 옵션의 ${valueIndex + 1}번째 값을 입력해주세요.`,
            );
          }

          const normalizedValue = value.toLocaleLowerCase("ko-KR");

          if (optionValues.has(normalizedValue)) {
            throw new Error(
              `${name} 옵션에 동일한 값을 중복 등록할 수 없습니다.`,
            );
          }

          optionValues.add(normalizedValue);

          return {
            id: optionValue.id,
            value,
            sortOrder: valueIndex,
          };
        });

        return {
          id: group.id,
          name,
          sortOrder: groupIndex,
          values,
        };
      },
    );

    if (optionEditorState.variants.length === 0) {
      throw new Error("옵션 조합별 SKU를 확인해주세요.");
    }

    const skuCodes = new Set<string>();
    let totalStockQuantity = 0;
    const productPrice = parseRequiredNumber(form.price, "판매가");

    optionEditorState.variants.forEach((variant, variantIndex) => {
      const skuCode = variant.skuCode.trim();

      if (!skuCode) {
        throw new Error(`${variantIndex + 1}번째 SKU 코드를 입력해주세요.`);
      }

      const normalizedSkuCode = skuCode.toUpperCase();

      if (skuCodes.has(normalizedSkuCode)) {
        throw new Error("SKU 코드는 중복될 수 없습니다.");
      }

      skuCodes.add(normalizedSkuCode);

      const additionalPrice = parseRequiredNumber(
        variant.additionalPrice,
        `${skuCode} 추가금`,
      );
      const stockQuantity = parseRequiredNumber(
        variant.stockQuantity,
        `${skuCode} 재고`,
      );

      const finalVariantPrice = productPrice + additionalPrice;

      if (!Number.isSafeInteger(finalVariantPrice)) {
        throw new Error(`${skuCode} 옵션 적용 후 최종 판매가격을 확인해주세요.`);
      }

      if (finalVariantPrice <= 0) {
        throw new Error(
          `${skuCode} 옵션 적용 후 최종 판매가격은 1원 이상이어야 합니다.`,
        );
      }

      if (stockQuantity < 0) {
        throw new Error("옵션 재고는 0개 이상이어야 합니다.");
      }

      if (variant.active) {
        totalStockQuantity += stockQuantity;
      }
    });

    return {
      totalStockQuantity,
      optionRequest: {
        optionGroups,
      } satisfies ProductOptionUpdateRequest,
    };
  };

  const createRegistrationVariants =
    (): ProductRegistrationVariantRequest[] => {
      if (!optionEditorState.enabled) {
        return [];
      }

      const optionPositionByClientId = new Map<
        string,
        {
          optionGroupSortOrder: number;
          optionValueSortOrder: number;
        }
      >();

      optionEditorState.optionGroups.forEach((group, groupIndex) => {
        group.values.forEach((value, valueIndex) => {
          optionPositionByClientId.set(value.clientId, {
            optionGroupSortOrder: groupIndex,
            optionValueSortOrder: valueIndex,
          });
        });
      });

      return optionEditorState.variants.map((variant) => {
        const skuCode = variant.skuCode.trim();

        const options = variant.optionValueClientIds.map((clientId) => {
          const optionPosition = optionPositionByClientId.get(clientId);

          if (!optionPosition) {
            throw new Error(`${skuCode} SKU의 옵션 정보를 확인할 수 없습니다.`);
          }

          return optionPosition;
        });

        return {
          skuCode,
          options,
          additionalPrice: parseRequiredNumber(
            variant.additionalPrice,
            `${skuCode} 추가금`,
          ),
          stockQuantity: parseRequiredNumber(
            variant.stockQuantity,
            `${skuCode} 재고`,
          ),
          active: variant.active,
        };
      });
    };

  const createModificationVariants =
    (): ProductModificationVariantRequest[] => {
      if (!optionEditorState.enabled) {
        return [];
      }

      const optionPositionByClientId = new Map<
        string,
        {
          optionGroupSortOrder: number;
          optionValueSortOrder: number;
        }
      >();

      optionEditorState.optionGroups.forEach((group, groupIndex) => {
        group.values.forEach((value, valueIndex) => {
          optionPositionByClientId.set(value.clientId, {
            optionGroupSortOrder: groupIndex,
            optionValueSortOrder: valueIndex,
          });
        });
      });

      return optionEditorState.variants.map((variant) => {
        const skuCode = variant.skuCode.trim();

        const options = variant.optionValueClientIds.map((clientId) => {
          const optionPosition = optionPositionByClientId.get(clientId);

          if (!optionPosition) {
            throw new Error(`${skuCode} SKU의 옵션 정보를 확인할 수 없습니다.`);
          }

          return optionPosition;
        });

        return {
          id: variant.id,
          skuCode,
          options,
          additionalPrice: parseRequiredNumber(
            variant.additionalPrice,
            `${skuCode} 추가금`,
          ),
          stockQuantity: parseRequiredNumber(
            variant.stockQuantity,
            `${skuCode} 재고`,
          ),
          active: variant.active,
        };
      });
    };

  const uploadDraftImages = async () => {
    let nextRepresentativeImageKey = representativeImageKey;

    if (representativeFile) {
      nextRepresentativeImageKey = await uploadImage(
        representativeFile,
        "PRODUCT_REPRESENTATIVE",
      );

      if (representativeBlobUrlRef.current) {
        URL.revokeObjectURL(representativeBlobUrlRef.current);

        representativeBlobUrlRef.current = null;
      }

      setRepresentativeFile(null);

      setRepresentativeImageKey(nextRepresentativeImageKey);

      setRepresentativePreviewUrl(resolveImageUrl(nextRepresentativeImageKey));
    }

    const nextGalleryImages: GalleryImageItem[] = [];
    const galleryImageKeys: string[] = [];

    for (const image of galleryImages) {
      if (image.kind === "existing") {
        galleryImageKeys.push(image.objectKey);

        nextGalleryImages.push(image);

        continue;
      }

      const objectKey = await uploadImage(image.file, "PRODUCT_GALLERY");

      galleryImageKeys.push(objectKey);

      URL.revokeObjectURL(image.previewUrl);

      const previewUrl = resolveImageUrl(objectKey);

      if (!previewUrl) {
        continue;
      }

      nextGalleryImages.push({
        id: createImageId(),
        kind: "existing",
        objectKey,
        previewUrl,
      });
    }

    setGalleryImages(nextGalleryImages);

    return {
      representativeImageKey: nextRepresentativeImageKey,

      galleryImageKeys,
    };
  };

  const createDraftData = (imageRequest: {
    representativeImageKey: string | null;

    galleryImageKeys: string[];
  }): ProductDraftData => {
    return {
      categoryId: form.categoryId,

      name: form.name,

      brandName: form.brandName,

      summary: form.summary,

      description: form.description,

      price: form.price,

      stockQuantity: form.stockQuantity,

      representativeImageKey: imageRequest.representativeImageKey,

      galleryImageKeys: imageRequest.galleryImageKeys,

      freeShipping: form.freeShipping,

      shippingFee: form.shippingFee,

      shippingPreparationDays: form.shippingPreparationDays,

      returnShippingFee: form.returnShippingFee,

      exchangeShippingFee: form.exchangeShippingFee,

      options: {
        enabled: optionEditorState.enabled,

        optionGroups: optionEditorState.optionGroups.map((group) => ({
          ...group,

          values: group.values.map((value) => ({
            ...value,
          })),
        })),

        variants: optionEditorState.variants.map((variant) => ({
          ...variant,

          optionValueClientIds: [...variant.optionValueClientIds],
        })),
      },
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

  const handleDraftSave = async () => {
    if (isSubmitting || isSavingDraft) {
      return;
    }

    if (!optionEditorState.initialized) {
      setErrorMessage("상품 옵션 정보를 아직 불러오는 중입니다.");

      return;
    }

    try {
      setIsSavingDraft(true);
      setErrorMessage("");

      const imageRequest = await uploadDraftImages();

      const draftData = createDraftData(imageRequest);

      let savedDraft: ProductDraft;

      if (draftId !== null) {
        savedDraft = await updateProductDraft(draftId, {
          draftData: JSON.stringify(draftData),
        });
      } else {
        savedDraft = await createProductDraft({
          productId: mode === "edit" ? (initialProduct?.id ?? null) : null,

          draftData: JSON.stringify(draftData),
        });
      }

      setDraftId(savedDraft.id);

      setAvailableDraft(savedDraft);

      alert("상품이 임시저장되었습니다.");
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "상품 임시저장에 실패했습니다.",
      );

      window.scrollTo({
        top: 0,
        behavior: "smooth",
      });
    } finally {
      setIsSavingDraft(false);
    }
  };

  const handleLoadDraft = () => {
    if (!availableDraft) {
      return;
    }

    const draft = JSON.parse(availableDraft.draftData) as ProductDraftData;

    setForm({
      categoryId: draft.categoryId,

      name: draft.name,

      brandName: draft.brandName,

      summary: draft.summary,

      description: draft.description,

      price: draft.price,

      stockQuantity: draft.stockQuantity,

      freeShipping: draft.freeShipping,

      shippingFee: draft.shippingFee,

      shippingPreparationDays: draft.shippingPreparationDays,

      returnShippingFee: draft.returnShippingFee,

      exchangeShippingFee: draft.exchangeShippingFee,
    });

    setEditorRevision((current) => current + 1);

    if (representativeBlobUrlRef.current) {
      URL.revokeObjectURL(representativeBlobUrlRef.current);

      representativeBlobUrlRef.current = null;
    }

    setRepresentativeFile(null);

    setRepresentativeImageKey(draft.representativeImageKey);

    setRepresentativePreviewUrl(resolveImageUrl(draft.representativeImageKey));

    galleryImages.forEach((image) => {
      if (image.kind === "new") {
        URL.revokeObjectURL(image.previewUrl);
      }
    });

    const restoredGalleryImages = draft.galleryImageKeys.flatMap(
      (objectKey) => {
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
      },
    );

    setGalleryImages(restoredGalleryImages);

    setDraftOptionState({
      enabled: draft.options.enabled,

      optionGroups: draft.options.optionGroups.map((group) => ({
        ...group,

        values: group.values.map((value) => ({
          ...value,
        })),
      })),

      variants: draft.options.variants.map((variant) => ({
        ...variant,

        optionValueClientIds: [...variant.optionValueClientIds],
      })),

      initialized: true,
    });

    setDraftOptionRevision((current) => current + 1);

    setErrorMessage("");
  };

  useEffect(() => {
    if (mode !== "create" || !initialDraft) {
      return;
    }

    const draft = JSON.parse(initialDraft.draftData) as ProductDraftData;

    // 서버에서 복원한 최초 임시저장을 controlled 상품 폼에 반영한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setForm({
      categoryId: draft.categoryId,

      name: draft.name,

      brandName: draft.brandName,

      summary: draft.summary,

      description: draft.description,

      price: draft.price,

      stockQuantity: draft.stockQuantity,

      freeShipping: draft.freeShipping,

      shippingFee: draft.shippingFee,

      shippingPreparationDays: draft.shippingPreparationDays,

      returnShippingFee: draft.returnShippingFee,

      exchangeShippingFee: draft.exchangeShippingFee,
    });

    setEditorRevision((current) => current + 1);

    if (representativeBlobUrlRef.current) {
      URL.revokeObjectURL(representativeBlobUrlRef.current);

      representativeBlobUrlRef.current = null;
    }

    setRepresentativeFile(null);

    setRepresentativeImageKey(draft.representativeImageKey);

    setRepresentativePreviewUrl(resolveImageUrl(draft.representativeImageKey));

    setGalleryImages(
      draft.galleryImageKeys.flatMap((objectKey) => {
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
      }),
    );

    setDraftOptionState({
      enabled: draft.options.enabled,

      optionGroups: draft.options.optionGroups.map((group) => ({
        ...group,

        values: group.values.map((value) => ({
          ...value,
        })),
      })),

      variants: draft.options.variants.map((variant) => ({
        ...variant,

        optionValueClientIds: [...variant.optionValueClientIds],
      })),

      initialized: true,
    });

    setDraftOptionRevision((current) => current + 1);
  }, [mode, initialDraft]);

  const handleSave = async (startSale: boolean) => {
    if (isSubmitting) {
      return;
    }

    try {
      setIsSubmitting(true);
      setErrorMessage("");

      const optionConfiguration = validateOptionConfiguration();
      const commonRequest = validateCommonRequest(
        optionConfiguration.totalStockQuantity,
      );

      if (startSale && !representativePreviewUrl) {
        throw new Error("판매를 시작하려면 대표 이미지를 등록해주세요.");
      }

      if (startSale && !form.description.trim()) {
        throw new Error("판매를 시작하려면 상품 상세 설명을 입력해주세요.");
      }

      const imageRequest = await uploadImages();

      if (mode === "create") {
        if (!startSale) {
          throw new Error("임시저장은 임시저장 버튼을 이용해주세요.");
        }

        const optionRequest: ProductOptionUpdateRequest =
          optionEditorState.enabled
            ? (optionConfiguration.optionRequest ?? {
                optionGroups: [],
              })
            : {
                optionGroups: [],
              };

        const registrationRequest: ProductRegistrationRequest = {
          product: {
            ...commonRequest,
            ...imageRequest,

            // 백엔드에서 다시 false로 강제하지만
            // 기존 ProductCreateRequest 타입을 맞추기 위해 전달
            startSale: false,
          },

          options: optionRequest,

          variants: createRegistrationVariants(),

          draftId,
        };

        await registerProduct(registrationRequest);

        router.replace("/seller/products");
        router.refresh();

        return;
      }

      if (!initialProduct) {
        throw new Error("수정할 상품 정보를 확인할 수 없습니다.");
      }

      const optionRequest: ProductOptionUpdateRequest =
        optionEditorState.enabled
          ? (optionConfiguration.optionRequest ?? {
              optionGroups: [],
            })
          : {
              optionGroups: [],
            };

      const modificationRequest: ProductModificationRequest = {
        product: {
          ...commonRequest,
          ...imageRequest,
        },

        options: optionRequest,

        variants: createModificationVariants(),

        draftId,
      };

      await modifyProduct(initialProduct.id, modificationRequest);

      router.replace(`/seller/products/${initialProduct.id}`);
      router.refresh();

      return;
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
                onClick={() => {
                  if (isEditMode && initialProduct) {
                    router.push(`/seller/products/${initialProduct.id}`);
                    return;
                  }

                  router.push("/seller/products");
                }}
                disabled={isSubmitting}
              >
                {isEditMode ? "← 상품 정보" : "← 상품 관리"}
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
                key={editorRevision}
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
                  <h2>상품 옵션</h2>
                </div>
                <p>색상, 사이즈 등 옵션과 SKU별 재고를 설정합니다.</p>
              </header>

              <ProductOptionManager
                productId={initialProduct?.id}
                disabled={isSubmitting || isSavingDraft}
                draftState={draftOptionState}
                draftRevision={draftOptionRevision}
                onChange={setOptionEditorState}
              />
            </section>

            <section className="seller-product-form-section">
              <header className="seller-product-form-section-header">
                <div>
                  <span className="seller-product-form-section-number">05</span>
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

                {!optionEditorState.enabled && (
                  <div className="seller-product-form-field">
                    <label htmlFor="stockQuantity">재고 수량 *</label>

                    <input
                      id="stockQuantity"
                      name="stockQuantity"
                      type="text"
                      inputMode="numeric"
                      value={form.stockQuantity}
                      onFocus={() => {
                        if (form.stockQuantity === "0") {
                          setForm((currentForm) => ({
                            ...currentForm,
                            stockQuantity: "",
                          }));
                        }
                      }}
                      onChange={(event) => {
                        const value = event.target.value.replace(/\D/g, "");

                        setForm((currentForm) => ({
                          ...currentForm,
                          stockQuantity: value,
                        }));
                      }}
                      onBlur={() => {
                        setForm((currentForm) => ({
                          ...currentForm,
                          stockQuantity:
                            currentForm.stockQuantity.trim() === ""
                              ? "0"
                              : String(Number(currentForm.stockQuantity)),
                        }));
                      }}
                      disabled={isSubmitting}
                    />
                  </div>
                )}

                {optionEditorState.enabled && (
                  <div className="seller-product-form-field">
                    <label>총 재고</label>
                    <div className="seller-product-form-readonly-value">
                      {optionEditorState.variants
                        .filter((variant) => variant.active)
                        .reduce((total, variant) => {
                          const stockQuantity = Number(variant.stockQuantity);

                          return Number.isSafeInteger(stockQuantity) &&
                            stockQuantity >= 0
                            ? total + stockQuantity
                            : total;
                        }, 0)
                        .toLocaleString("ko-KR")}
                      개
                    </div>
                    <span className="seller-product-form-help">
                      옵션 상품의 재고는 SKU별 재고 합계로 자동 관리됩니다.
                    </span>
                  </div>
                )}

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

                <div
                  className={[
                    "seller-product-form-field",
                    "seller-product-form-shipping-fee-field",
                    form.freeShipping
                      ? "seller-product-form-shipping-fee-field-hidden"
                      : "",
                  ]
                    .filter(Boolean)
                    .join(" ")}
                >
                  <label htmlFor="shippingFee">배송비 *</label>

                  <div className="seller-product-form-unit-input">
                    <input
                      id="shippingFee"
                      name="shippingFee"
                      type="text"
                      inputMode="numeric"
                      value={form.shippingFee}
                      disabled={isSubmitting || form.freeShipping}
                      onFocus={() => {
                        if (form.shippingFee === "0") {
                          setForm((currentForm) => ({
                            ...currentForm,
                            shippingFee: "",
                          }));
                        }
                      }}
                      onChange={(event) => {
                        const value = event.target.value.replace(/\D/g, "");

                        setForm((currentForm) => ({
                          ...currentForm,
                          shippingFee: value,
                        }));
                      }}
                      onBlur={() => {
                        setForm((currentForm) => ({
                          ...currentForm,
                          shippingFee:
                            currentForm.shippingFee.trim() === ""
                              ? "0"
                              : String(Number(currentForm.shippingFee)),
                        }));
                      }}
                    />

                    <span>원</span>
                  </div>
                </div>

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

                <div className="seller-product-form-field seller-product-form-exchange-shipping-fee-field">
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
                  onClick={() => void handleDraftSave()}
                  disabled={
                    isSubmitting ||
                    isSavingDraft ||
                    !optionEditorState.initialized
                  }
                >
                  {isSavingDraft ? "임시저장 중..." : "임시 저장"}
                </button>
                {mode === "edit" && availableDraft && (
                  <button
                    type="button"
                    className="seller-product-form-draft-button"
                    onClick={handleLoadDraft}
                    disabled={isSubmitting || isSavingDraft}
                  >
                    임시저장 불러오기
                  </button>
                )}
              </div>

              <div className="seller-product-form-actions-right">
                <button
                  type="button"
                  className="seller-product-form-cancel-button"
                  onClick={() => {
                    if (isEditMode && initialProduct) {
                      router.push(`/seller/products/${initialProduct.id}`);
                      return;
                    }

                    router.push("/seller/products");
                  }}
                  disabled={isSubmitting || isSavingDraft}
                >
                  취소
                </button>
                <button
                  type="button"
                  className="seller-product-form-submit-button"
                  onClick={() => void handleSave(true)}
                  disabled={
                    isSubmitting ||
                    isSavingDraft ||
                    !optionEditorState.initialized
                  }
                >
                  {isSubmitting
                    ? "저장 중..."
                    : isEditMode
                      ? "수정 완료"
                      : "판매 시작"}
                </button>
              </div>
            </div>
          </form>
        </div>
      </div>
    </main>
  );
}
