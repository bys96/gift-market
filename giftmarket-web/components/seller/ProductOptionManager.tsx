"use client";

import { useEffect, useMemo, useRef, useState } from "react";

import { getProductOptions, getProductVariants } from "@/lib/product-api";
import type { ProductOptionGroup, ProductVariant } from "@/types/product";

export interface ProductOptionValueDraft {
  clientId: string;
  id: number | null;
  value: string;
}

export interface ProductOptionGroupDraft {
  clientId: string;
  id: number | null;
  name: string;
  values: ProductOptionValueDraft[];
}

export interface ProductVariantDraft {
  clientId: string;
  id: number | null;

  skuCode: string;
  systemSkuCode?: string;
  version?: number;
  originalStockQuantity?: number;

  optionValueClientIds: string[];

  additionalPrice: string;
  stockQuantity: string;

  active: boolean;
}

export interface ProductOptionEditorState {
  mappingInvalid?: boolean;
  enabled: boolean;

  optionGroups: ProductOptionGroupDraft[];

  variants: ProductVariantDraft[];

  initialized: boolean;
}

interface ProductOptionManagerProps {
  productId?: number;

  disabled?: boolean;

  draftState?: ProductOptionEditorState | null;

  draftRevision?: number;

  onChange: (state: ProductOptionEditorState) => void;
}

function createClientId(): string {
  if (
    typeof crypto !== "undefined" &&
    typeof crypto.randomUUID === "function"
  ) {
    return crypto.randomUUID();
  }

  return `${Date.now()}-${Math.random()}`;
}

function createEmptyOptionValue(): ProductOptionValueDraft {
  return {
    clientId: createClientId(),
    id: null,
    value: "",
  };
}

function createEmptyOptionGroup(): ProductOptionGroupDraft {
  return {
    clientId: createClientId(),
    id: null,
    name: "",
    values: [createEmptyOptionValue(), createEmptyOptionValue()],
  };
}

function createVariantClientKey(optionValueClientIds: string[]): string {
  return [...optionValueClientIds].sort().join("|");
}

function createOptionCombinations(
  optionGroups: ProductOptionGroupDraft[],
): string[][] {
  if (optionGroups.length === 0) {
    return [];
  }

  if (
    optionGroups.some(
      (group) =>
        group.values.length === 0 ||
        group.values.some((value) => !value.value.trim()),
    )
  ) {
    return [];
  }

  return optionGroups.reduce<string[][]>((combinations, group) => {
    if (combinations.length === 0) {
      return group.values.map((value) => [value.clientId]);
    }

    return combinations.flatMap((combination) =>
      group.values.map((value) => [...combination, value.clientId]),
    );
  }, []);
}

function createOptionDrafts(
  optionGroups: ProductOptionGroup[],
): ProductOptionGroupDraft[] {
  return optionGroups.map((group) => ({
    clientId: `group-${group.id}`,
    id: group.id,
    name: group.name,
    values: group.values.map((value) => ({
      clientId: `group-${group.id}-value-${value.id}`,
      id: value.id,
      value: value.value,
    })),
  }));
}

function createVariantDrafts(
  variants: ProductVariant[],
  optionGroups: ProductOptionGroupDraft[],
): ProductVariantDraft[] {
  const clientIdByOptionValueId = new Map<number, string>();

  optionGroups.forEach((group) => {
    group.values.forEach((value) => {
      if (value.id !== null) {
        clientIdByOptionValueId.set(value.id, value.clientId);
      }
    });
  });

  return variants.flatMap((variant) => {
    if (variant.mappingValid === false) return [];
    const optionValueClientIds = variant.optionValues
      .sort(
        (a, b) =>
          a.optionGroupSortOrder - b.optionGroupSortOrder ||
          a.optionValueSortOrder - b.optionValueSortOrder,
      )
      .map((optionValue) =>
        clientIdByOptionValueId.get(optionValue.optionValueId),
      );

    if (
      optionValueClientIds.length === 0 ||
      optionValueClientIds.some((clientId) => !clientId)
    ) {
      return [];
    }

    return [
      {
        clientId: createVariantClientKey(optionValueClientIds as string[]),
        id: variant.id,
        skuCode: variant.sellerSkuCode,
        systemSkuCode: variant.skuCode,
        version: variant.version,
        originalStockQuantity: variant.stockQuantity,
        optionValueClientIds: optionValueClientIds as string[],
        additionalPrice: String(variant.additionalPrice),
        stockQuantity: String(variant.stockQuantity),
        active: variant.active,
      },
    ];
  });
}

function synchronizeVariants(
  optionGroups: ProductOptionGroupDraft[],
  currentVariants: ProductVariantDraft[],
): ProductVariantDraft[] {
  const combinations = createOptionCombinations(optionGroups);

  if (combinations.length === 0) {
    return [];
  }

  const existingVariantMap = new Map(
    currentVariants.map((variant) => [
      createVariantClientKey(variant.optionValueClientIds),
      variant,
    ]),
  );

  return combinations.map((combination) => {
    const key = createVariantClientKey(combination);

    const existingVariant = existingVariantMap.get(key);

    if (existingVariant) {
      return existingVariant;
    }

    return {
      clientId: key,
      id: null,
      skuCode: "",
      optionValueClientIds: combination,
      additionalPrice: "0",
      stockQuantity: "0",
      active: true,
    };
  });
}

export default function ProductOptionManager({
  productId,
  disabled = false,
  draftState = null,
  draftRevision = 0,
  onChange,
}: ProductOptionManagerProps) {
  const onChangeRef = useRef(onChange);

  const fixedModeRef = useRef<boolean | null>(null);
  const variantHistoryRef = useRef<ProductVariantDraft[]>([]);
  const [lockedValueIds, setLockedValueIds] = useState<string[]>([]);
  const [unmappedVariants, setUnmappedVariants] = useState<ProductVariant[]>([]);
  const [archivedGroups, setArchivedGroups] = useState<ProductOptionGroupDraft[]>([]);
  const [enabled, setEnabled] = useState(false);

  const [optionGroups, setOptionGroups] = useState<ProductOptionGroupDraft[]>(
    [],
  );

  const [variants, setVariants] = useState<ProductVariantDraft[]>([]);

  const [initialized, setInitialized] = useState(productId === undefined);

  const [isLoading, setIsLoading] = useState(productId !== undefined);

  const [errorMessage, setErrorMessage] = useState("");

  const [showInactiveVariants, setShowInactiveVariants] = useState(false);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    if (!productId) {
      return;
    }

    let cancelled = false;

    const loadOptions = async () => {
      try {
        setIsLoading(true);
        setErrorMessage("");

        const [optionResponse, variantResponse] = await Promise.all([
          getProductOptions(productId),
          getProductVariants(productId),
        ]);

        if (cancelled) {
          return;
        }

        const activeValueIds = new Set(optionResponse.optionGroups.flatMap(group => group.values.map(value => value.id)));
        const activeGroupIds = new Set(optionResponse.optionGroups.map(group => group.id));
        const allGroups = optionResponse.optionGroups.map(group => ({...group, values: [...group.values]}));
        for (const group of optionResponse.archivedOptionGroups ?? []) {
          const existing = allGroups.find(item => item.id === group.id);
          if (existing) existing.values.push(...group.values);
          else allGroups.push(group);
        }
        const allDrafts = createOptionDrafts(allGroups);
        const loadedOptionGroups = allDrafts.filter(group => activeGroupIds.has(group.id!)).map(group => ({
          ...group, values: group.values.filter(value => activeValueIds.has(value.id!)),
        }));
        setArchivedGroups(allDrafts.map(group => ({...group,
          values: group.values.filter(value => !activeValueIds.has(value.id!)),
        })).filter(group => group.values.length > 0));

        const loadedVariants = createVariantDrafts(
          variantResponse.variants,
          allDrafts,
        );
        variantHistoryRef.current = loadedVariants;
        setLockedValueIds(loadedVariants.filter(variant => variant.id !== null).flatMap(variant => variant.optionValueClientIds));
        setUnmappedVariants(variantResponse.variants.filter(variant =>
          !loadedVariants.some(draft => draft.id === variant.id)));

        const hasOptions = variantResponse.variants.length > 0 || loadedOptionGroups.length > 0;
        fixedModeRef.current = hasOptions;

        setEnabled(hasOptions);
        setOptionGroups(loadedOptionGroups);
        setVariants(synchronizeVariants(loadedOptionGroups, loadedVariants));
      } catch (error) {
        if (cancelled) {
          return;
        }

        setErrorMessage(
          error instanceof Error
            ? error.message
            : "상품 옵션 정보를 불러오지 못했습니다.",
        );
      } finally {
        if (!cancelled) {
          setInitialized(true);
          setIsLoading(false);
        }
      }
    };

    void loadOptions();

    return () => {
      cancelled = true;
    };
  }, [productId]);

  useEffect(() => {
    if (!draftState || draftRevision <= 0) {
      return;
    }

    if (productId !== undefined && fixedModeRef.current !== null && draftState.enabled !== fixedModeRef.current) {
      setErrorMessage("임시저장 데이터의 재고 관리 방식이 현재 상품과 다릅니다. 최신 상품을 다시 불러와주세요.");
      return;
    }
    // Draft 복원은 외부 저장 snapshot을 로컬 편집 상태로 한 번 동기화한다.
    setEnabled(draftState.enabled);

    setOptionGroups(
      draftState.optionGroups.map((group) => ({
        ...group,
        values: group.values.map((value) => ({
          ...value,
        })),
      })),
    );

    setVariants(
      draftState.variants.map((variant) => ({
        ...variant,
        optionValueClientIds: [...variant.optionValueClientIds],
      })),
    );

    setInitialized(true);
    setIsLoading(false);
    setErrorMessage("");
  }, [draftRevision, draftState, productId]);

  useEffect(() => {
    if (!initialized) {
      return;
    }

    onChangeRef.current({
      enabled,
      optionGroups,
      variants,
      initialized,
      mappingInvalid: unmappedVariants.length > 0,
    });
  }, [enabled, optionGroups, variants, initialized, unmappedVariants]);

  const optionValueMap = useMemo(() => {
    const result = new Map<
      string,
      {
        groupName: string;
        valueName: string;
      }
    >();

    optionGroups.forEach((group) => {
      group.values.forEach((value) => {
        result.set(value.clientId, {
          groupName: group.name.trim() || "옵션",
          valueName: value.value.trim() || "값 미입력",
        });
      });
    });

    return result;
  }, [optionGroups]);

  const totalStockQuantity = useMemo(
    () =>
      variants
        .filter((variant) => variant.active)
        .reduce((total, variant) => {
          const stockQuantity = Number(variant.stockQuantity);

          if (!Number.isSafeInteger(stockQuantity) || stockQuantity < 0) {
            return total;
          }

          return total + stockQuantity;
        }, 0),
    [variants],
  );

  const activeVariants = variants.filter((variant) => variant.active);
  const inactiveVariants = variants.filter((variant) => !variant.active);

  const updateOptionGroups = (
    updater: (current: ProductOptionGroupDraft[]) => ProductOptionGroupDraft[],
  ) => {
    setOptionGroups((currentGroups) => {
      const nextGroups = updater(currentGroups);

      setVariants((currentVariants) => {
        const combined = [...variantHistoryRef.current.filter(old => !currentVariants.some(item => item.id === old.id && item.id !== null)), ...currentVariants];
        variantHistoryRef.current = combined;
        return synchronizeVariants(nextGroups, combined);
      });

      return nextGroups;
    });
  };

  const restoreArchivedValue = (group: ProductOptionGroupDraft, value: ProductOptionValueDraft) => {
    setEnabled(true);
    updateOptionGroups(current => {
      const existing = current.find(item => item.id === group.id);
      return existing ? current.map(item => item.id === group.id
        ? {...item, values: [...item.values, value]} : item)
        : [...current, {...group, values: [value]}];
    });
    setArchivedGroups(current => current.map(item => ({...item,
      values: item.values.filter(candidate => candidate.id !== value.id),
    })).filter(item => item.values.length > 0));
  };

  const handleEnabledChange = (nextEnabled: boolean) => {
    if (productId !== undefined && fixedModeRef.current !== null && nextEnabled !== fixedModeRef.current) {
      setErrorMessage("기존 상품의 재고 관리 방식은 변경할 수 없습니다. 옵션 전체 보관은 판매 중지로 처리됩니다.");
      return;
    }
    setEnabled(nextEnabled);

    if (nextEnabled && optionGroups.length === 0) {
      const firstGroup = createEmptyOptionGroup();

      setOptionGroups([firstGroup]);
      setVariants([]);
    }
  };

  const handleAddGroup = () => {
    updateOptionGroups((currentGroups) => [
      ...currentGroups,
      createEmptyOptionGroup(),
    ]);
  };

  const handleRemoveGroup = (groupClientId: string) => {
    const removed = optionGroups.find(group => group.clientId === groupClientId);
    if (removed?.id !== null && removed) {
      setArchivedGroups(current => {
        const previous = current.find(group => group.id === removed.id);
        const values = [...(previous?.values ?? []), ...removed.values];
        return [...current.filter(group => group.id !== removed.id), {...removed,
          values: values.filter((value, index) => values.findIndex(item => item.id === value.id) === index),
        }];
      });
    }
    updateOptionGroups((currentGroups) =>
      currentGroups.filter((group) => group.clientId !== groupClientId),
    );
  };

  const handleGroupNameChange = (groupClientId: string, name: string) => {
    updateOptionGroups((currentGroups) =>
      currentGroups.map((group) =>
        group.clientId === groupClientId
          ? {
              ...group,
              name,
            }
          : group,
      ),
    );
  };

  const handleAddOptionValue = (groupClientId: string) => {
    updateOptionGroups((currentGroups) =>
      currentGroups.map((group) =>
        group.clientId === groupClientId
          ? {
              ...group,
              values: [...group.values, createEmptyOptionValue()],
            }
          : group,
      ),
    );
  };

  const handleRemoveOptionValue = (
    groupClientId: string,
    valueClientId: string,
  ) => {
    const removedGroup = optionGroups.find(group => group.clientId === groupClientId);
    const removedValue = removedGroup?.values.find(value => value.clientId === valueClientId);
    if (removedGroup && removedValue && removedValue.id !== null) {
      setArchivedGroups(current => {
        const existing = current.find(group => group.id === removedGroup.id);
        return existing ? current.map(group => group.id === existing.id
          ? {...group, values: [...group.values, removedValue]} : group)
          : [...current, {...removedGroup, values: [removedValue]}];
      });
    }
    updateOptionGroups((currentGroups) =>
      currentGroups.map((group) => {
        if (group.clientId !== groupClientId) {
          return group;
        }

        return {
          ...group,
          values: group.values.filter(
            (value) => value.clientId !== valueClientId,
          ),
        };
      }),
    );
  };

  const handleOptionValueChange = (
    groupClientId: string,
    valueClientId: string,
    value: string,
  ) => {
    updateOptionGroups((currentGroups) =>
      currentGroups.map((group) => {
        if (group.clientId !== groupClientId) {
          return group;
        }

        return {
          ...group,
          values: group.values.map((optionValue) =>
            optionValue.clientId === valueClientId
              ? {
                  ...optionValue,
                  value,
                }
              : optionValue,
          ),
        };
      }),
    );
  };

  const handleVariantChange = (
    variantClientId: string,
    field: "skuCode" | "additionalPrice" | "stockQuantity",
    value: string,
  ) => {
    setVariants((currentVariants) =>
      currentVariants.map((variant) =>
        variant.clientId === variantClientId
          ? {
              ...variant,
              [field]: value,
            }
          : variant,
      ),
    );
  };

  const handleVariantInputKeyDown = (
    event: React.KeyboardEvent<HTMLInputElement>,
  ) => {
    const currentInput = event.currentTarget;

    if (event.nativeEvent.isComposing) {
      return;
    }

    if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
      const { selectionStart, selectionEnd, value } = currentInput;

      if (
        selectionStart === null ||
        selectionEnd === null ||
        selectionStart !== selectionEnd ||
        (event.key === "ArrowLeft" && selectionStart !== 0) ||
        (event.key === "ArrowRight" && selectionEnd !== value.length)
      ) {
        return;
      }
    }

    const row = currentInput.closest("tr");

    if (!row) {
      return;
    }

    const table = row.closest("table");

    if (!table) {
      return;
    }

    const rows = Array.from(
      table.querySelectorAll<HTMLTableRowElement>("tbody tr"),
    );

    const currentRowIndex = rows.indexOf(row);

    const inputs = Array.from(
      row.querySelectorAll<HTMLInputElement>(
        'input[data-variant-field="true"]',
      ),
    );

    const currentColumnIndex = inputs.indexOf(currentInput);

    let targetRowIndex = currentRowIndex;
    let targetColumnIndex = currentColumnIndex;

    switch (event.key) {
      case "ArrowUp":
        targetRowIndex -= 1;
        break;

      case "ArrowDown":
      case "Enter":
        targetRowIndex += 1;
        break;

      case "ArrowLeft":
        targetColumnIndex -= 1;
        break;

      case "ArrowRight":
        targetColumnIndex += 1;
        break;

      default:
        return;
    }

    if (targetRowIndex < 0 || targetRowIndex >= rows.length) {
      return;
    }

    const targetInputs = Array.from(
      rows[targetRowIndex].querySelectorAll<HTMLInputElement>(
        'input[data-variant-field="true"]',
      ),
    );

    if (targetColumnIndex < 0 || targetColumnIndex >= targetInputs.length) {
      return;
    }

    const targetInput = targetInputs[targetColumnIndex];

    event.preventDefault();
    targetInput.focus();

    if (event.key === "ArrowLeft") {
      targetInput.setSelectionRange(targetInput.value.length, targetInput.value.length);
    } else if (event.key === "ArrowRight") {
      targetInput.setSelectionRange(0, 0);
    } else {
      targetInput.select();
    }
  };

  const handleVariantActiveChange = (
    variantClientId: string,
    active: boolean,
  ) => {
    setVariants((currentVariants) =>
      currentVariants.map((variant) =>
        variant.clientId === variantClientId
          ? {
              ...variant,
              active,
            }
          : variant,
      ),
    );
  };

  if (isLoading) {
    return (
      <div className="seller-product-option-loading">
        상품 옵션을 불러오고 있습니다.
      </div>
    );
  }

  return (
    <div className="seller-product-option-manager">
      {errorMessage && (
        <div className="seller-product-option-error" role="alert">
          {errorMessage}
        </div>
      )}

      {unmappedVariants.length > 0 && (
        <div className="seller-product-option-error" role="alert">
          <strong>옵션 매핑 확인이 필요한 기존 SKU</strong>
          <p>옵션 매핑이 조합 이력과 일치하지 않아 자동 복원할 수 없습니다. 저장이 차단됩니다. 기존 주문과 재고는 보존됩니다. 아래 관리 코드는 계속 사용 중이며, 복원은 원본 이력 확인이 필요합니다.</p>
          {unmappedVariants.map(variant => (
            <p key={variant.id}>관리 코드: {variant.sellerSkuCode} / 시스템 SKU: {variant.skuCode} / 보존 재고: {variant.stockQuantity}개</p>
          ))}
        </div>
      )}
      {archivedGroups.length > 0 && (
        <details className="seller-product-option-group-card seller-product-option-archive">
          <summary>보관 옵션 복원</summary>
          <p>기존 옵션과 SKU·재고를 그대로 복원합니다. 복원 후 비활성 SKU를 활성화해주세요.</p>
          {archivedGroups.flatMap(group => group.values.map(value => (
            <button type="button" className="seller-product-option-add-button" disabled={disabled}
              key={value.clientId} onClick={() => restoreArchivedValue(group, value)}>
              {group.name} / {value.value} 복원
            </button>
          )))}
        </details>
      )}
      <p className="seller-product-option-notice">SKU에 연결된 옵션 이름은 변경할 수 없습니다. 전체 옵션 보관 시 구매가 차단되며 기존 Variant 재고는 보존됩니다. 재고 수정은 최신 버전을 확인합니다.</p>
      <div className="seller-product-option-use">
        <div>
          <strong>상품 옵션 사용</strong>

          <p>
            색상, 사이즈 등 선택 옵션이 있는 상품은 Variant 단위로 재고를
            관리합니다.
          </p>
        </div>

        <div className="seller-product-option-use-buttons">
          <button
            type="button"
            className={
              !enabled
                ? "seller-product-option-use-button seller-product-option-use-button-active"
                : "seller-product-option-use-button"
            }
            disabled={disabled || productId !== undefined}
            onClick={() => handleEnabledChange(false)}
          >
            옵션 없음
          </button>

          <button
            type="button"
            className={
              enabled
                ? "seller-product-option-use-button seller-product-option-use-button-active"
                : "seller-product-option-use-button"
            }
            disabled={disabled}
            onClick={() => handleEnabledChange(true)}
          >
            옵션 사용
          </button>
        </div>
      </div>

      {enabled && (
        <>
          <div className="seller-product-option-groups">
            <div className="seller-product-option-subheader">
              <div>
                <strong>옵션 구성</strong>

                <p>예: 색상 → 블랙, 화이트 / 사이즈 → M, L</p>
              </div>

              <button
                type="button"
                className="seller-product-option-add-button"
                disabled={disabled || optionGroups.length >= 10}
                onClick={handleAddGroup}
              >
                + 옵션 추가
              </button>
            </div>

            {optionGroups.map((group, groupIndex) => (
              <article
                key={group.clientId}
                className="seller-product-option-group-card"
              >
                <div className="seller-product-option-group-header">
                  <span>옵션 {groupIndex + 1}</span>

                  <button
                    type="button"
                    disabled={disabled}
                    onClick={() => handleRemoveGroup(group.clientId)}
                  >
                    삭제
                  </button>
                </div>

                <div className="seller-product-option-group-name">
                  <label>옵션명</label>

                  <input
                    readOnly={group.values.some(value => lockedValueIds.includes(value.clientId))}
                    value={group.name}
                    autoComplete="off"
                    maxLength={50}
                    placeholder="예: 색상"
                    disabled={disabled}
                    onChange={(event) =>
                      handleGroupNameChange(group.clientId, event.target.value)
                    }
                  />
                </div>

                <div className="seller-product-option-values">
                  <div className="seller-product-option-values-header">
                    <label>옵션 값</label>

                    <button
                      type="button"
                      disabled={disabled || group.values.length >= 50}
                      onClick={() => handleAddOptionValue(group.clientId)}
                    >
                      + 값 추가
                    </button>
                  </div>

                  <div className="seller-product-option-value-list">
                    {group.values.map((optionValue, valueIndex) => (
                      <div
                        key={optionValue.clientId}
                        className="seller-product-option-value-row"
                      >
                        <span>{valueIndex + 1}</span>

                        <input
                          readOnly={lockedValueIds.includes(optionValue.clientId)}
                          value={optionValue.value}
                          autoComplete="off"
                          maxLength={100}
                          placeholder={
                            valueIndex === 0
                              ? "예: 블랙"
                              : valueIndex === 1
                                ? "예: 화이트"
                                : "옵션 값 입력"
                          }
                          disabled={disabled}
                          onChange={(event) =>
                            handleOptionValueChange(
                              group.clientId,
                              optionValue.clientId,
                              event.target.value,
                            )
                          }
                        />

                        <button
                          type="button"
                          disabled={disabled || group.values.length <= 1}
                          onClick={() =>
                            handleRemoveOptionValue(
                              group.clientId,
                              optionValue.clientId,
                            )
                          }
                        >
                          삭제
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              </article>
            ))}
          </div>

          <div className="seller-product-variant-section">
            <div className="seller-product-option-subheader">
              <div>
                <strong>SKU / 재고 설정</strong>

                <p>상품 내 옵션을 구분하는 코드입니다. 다른 상품에서는 같은 코드를 사용할 수 있습니다. 비활성 옵션을 포함하여 대소문자는 구분하지 않습니다.</p>
              </div>

              <span className="seller-product-variant-total-stock">
                활성 SKU 총 재고{" "}
                <strong>{totalStockQuantity.toLocaleString("ko-KR")}</strong>개
              </span>
            </div>

            {activeVariants.length === 0 ? (
              <div className="seller-product-variant-empty">
                {inactiveVariants.length > 0
                  ? "현재 판매 중인 SKU가 없습니다. 판매 중지 옵션에서 다시 활성화할 수 있습니다."
                  : "옵션명과 옵션 값을 모두 입력하면 SKU 조합이 자동으로 생성됩니다."}
              </div>
            ) : (
              <div className="seller-product-variant-table-wrapper">
                <table className="seller-product-variant-table">
                  <thead>
                    <tr>
                      <th>옵션 조합</th>
                      <th>옵션 관리 코드</th>
                      <th>추가금</th>
                      <th>재고</th>
                      <th>판매</th>
                    </tr>
                  </thead>

                  <tbody>
                    {activeVariants.map((variant) => (
                      <tr key={variant.clientId}>
                        <td>
                          <div className="seller-product-variant-combination">
                            {variant.optionValueClientIds.map(
                              (optionValueClientId) => {
                                const option =
                                  optionValueMap.get(optionValueClientId);

                                if (!option) {
                                  return null;
                                }

                                return (
                                  <span key={optionValueClientId}>
                                    <small>{option.groupName}</small>

                                    {option.valueName}
                                  </span>
                                );
                              },
                            )}
                          </div>
                        </td>

                        <td>
                          <input
                            value={variant.skuCode}
                            autoComplete="off"
                            maxLength={100}
                            placeholder="예: F1, RED, BLUE"
                            aria-label="옵션 관리 코드"
                            disabled={disabled}
                            data-variant-field="true"
                            onChange={(event) =>
                              handleVariantChange(
                                variant.clientId,
                                "skuCode",
                                event.target.value,
                              )
                            }
                            onKeyDown={handleVariantInputKeyDown}
                          />

                          {variant.id !== null && variant.systemSkuCode && (
                            <small className="seller-product-variant-existing">
                              시스템 SKU: {variant.systemSkuCode}
                            </small>
                          )}
                        </td>

                        <td>
                          <div className="seller-product-variant-number-input">
                            <input
                              type="text"
                              autoComplete="off"
                              inputMode="text"
                              value={variant.additionalPrice}
                              title="추가금은 -1,000만원부터 1,000만원까지 입력할 수 있으며 최종 판매가격은 1원 이상이어야 합니다."
                              disabled={disabled}
                              data-variant-field="true"
                              onFocus={() => {
                                if (variant.additionalPrice === "0") {
                                  handleVariantChange(
                                    variant.clientId,
                                    "additionalPrice",
                                    "",
                                  );
                                }
                              }}
                              onChange={(event) => {
                                const inputValue = event.target.value;
                                if (!/^-?\d*$/.test(inputValue)) return;
                                const value = inputValue;

                                handleVariantChange(
                                  variant.clientId,
                                  "additionalPrice",
                                  value,
                                );
                              }}
                              onBlur={() => {
                                handleVariantChange(
                                  variant.clientId,
                                  "additionalPrice",
                                  variant.additionalPrice.trim() === "" ||
                                    variant.additionalPrice.trim() === "-"
                                    ? "0"
                                    : String(Number(variant.additionalPrice)),
                                );
                              }}
                              onKeyDown={handleVariantInputKeyDown}
                            />

                            <span>원</span>
                          </div>
                        </td>

                        <td>
                          <div className="seller-product-variant-number-input">
                            <input
                              type="text"
                              autoComplete="off"
                              inputMode="numeric"
                              value={variant.stockQuantity}
                              disabled={disabled}
                              data-variant-field="true"
                              onFocus={() => {
                                if (variant.stockQuantity === "0") {
                                  handleVariantChange(
                                    variant.clientId,
                                    "stockQuantity",
                                    "",
                                  );
                                }
                              }}
                              onChange={(event) => {
                                const value = event.target.value.replace(
                                  /\D/g,
                                  "",
                                );

                                handleVariantChange(
                                  variant.clientId,
                                  "stockQuantity",
                                  value,
                                );
                              }}
                              onBlur={() => {
                                handleVariantChange(
                                  variant.clientId,
                                  "stockQuantity",
                                  variant.stockQuantity.trim() === ""
                                    ? "0"
                                    : String(Number(variant.stockQuantity)),
                                );
                              }}
                              onKeyDown={handleVariantInputKeyDown}
                            />

                            <span>개</span>
                          </div>
                        </td>

                        <td>
                          <label className="seller-product-variant-active">
                            <input
                              type="checkbox"
                              checked={variant.active}
                              disabled={disabled}
                              onChange={(event) =>
                                handleVariantActiveChange(
                                  variant.clientId,
                                  event.target.checked,
                                )
                              }
                            />

                            <span>{variant.active ? "판매" : "중지"}</span>
                          </label>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {inactiveVariants.length > 0 && (
              <div className="seller-product-inactive-variants">
                <button
                  type="button"
                  className="seller-product-inactive-toggle"
                  aria-expanded={showInactiveVariants}
                  onClick={() => setShowInactiveVariants((current) => !current)}
                >
                  판매 중지 옵션 보기 <span>{inactiveVariants.length}개</span>
                </button>

                {showInactiveVariants && (
                  <div className="seller-product-inactive-list">
                    {inactiveVariants.map((variant) => (
                      <div key={variant.clientId}>
                        <div className="seller-product-variant-combination">
                          {variant.optionValueClientIds.map((clientId) => {
                            const option = optionValueMap.get(clientId);
                            return option ? <span key={clientId}><small>{option.groupName}</small>{option.valueName}</span> : null;
                          })}
                        </div>
                        <span>{variant.skuCode || "SKU 미입력"}</span>
                        <button type="button" disabled={disabled} onClick={() => handleVariantActiveChange(variant.clientId, true)}>판매 재개</button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            <p className="seller-product-option-notice">
              옵션값을 제거하면 연결된 기존 SKU는 삭제되지 않고 판매 중지됩니다.
              판매 중지 옵션은 위 목록에서 확인하거나 다시 활성화할 수 있습니다.
            </p>
          </div>
        </>
      )}
    </div>
  );
}
