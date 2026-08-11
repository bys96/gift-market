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

  optionValueClientIds: string[];

  additionalPrice: string;
  stockQuantity: string;

  active: boolean;
}

export interface ProductOptionEditorState {
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
  return optionValueClientIds.join("|");
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
    clientId: createClientId(),
    id: group.id,
    name: group.name,
    values: group.values.map((value) => ({
      clientId: createClientId(),
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
    const optionValueClientIds = variant.optionValues
      .sort(
        (a, b) =>
          a.optionGroupSortOrder - b.optionGroupSortOrder ||
          a.optionValueSortOrder - b.optionValueSortOrder,
      )
      .map((optionValue) =>
        clientIdByOptionValueId.get(optionValue.optionValueId),
      );

    if (optionValueClientIds.some((clientId) => !clientId)) {
      return [];
    }

    return [
      {
        clientId: createVariantClientKey(optionValueClientIds as string[]),
        id: variant.id,
        skuCode: variant.skuCode,
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

  const [enabled, setEnabled] = useState(false);

  const [optionGroups, setOptionGroups] = useState<ProductOptionGroupDraft[]>(
    [],
  );

  const [variants, setVariants] = useState<ProductVariantDraft[]>([]);

  const [initialized, setInitialized] = useState(productId === undefined);

  const [isLoading, setIsLoading] = useState(productId !== undefined);

  const [errorMessage, setErrorMessage] = useState("");

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

        const loadedOptionGroups = createOptionDrafts(
          optionResponse.optionGroups,
        );

        const loadedVariants = createVariantDrafts(
          variantResponse.variants,
          loadedOptionGroups,
        );

        const hasOptions = loadedOptionGroups.length > 0;

        setEnabled(hasOptions);
        setOptionGroups(loadedOptionGroups);
        setVariants(loadedVariants);
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
  }, [draftRevision, draftState]);

  useEffect(() => {
    if (!initialized) {
      return;
    }

    onChangeRef.current({
      enabled,
      optionGroups,
      variants,
      initialized,
    });
  }, [enabled, optionGroups, variants, initialized]);

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

  const updateOptionGroups = (
    updater: (current: ProductOptionGroupDraft[]) => ProductOptionGroupDraft[],
  ) => {
    setOptionGroups((currentGroups) => {
      const nextGroups = updater(currentGroups);

      setVariants((currentVariants) =>
        synchronizeVariants(nextGroups, currentVariants),
      );

      return nextGroups;
    });
  };

  const handleEnabledChange = (nextEnabled: boolean) => {
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

    event.preventDefault();

    const targetInput = targetInputs[targetColumnIndex];

    targetInput.focus();
    targetInput.select();
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
            disabled={disabled}
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
                    value={group.name}
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
                          value={optionValue.value}
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

                <p>옵션 조합별 SKU, 추가금, 재고를 설정합니다.</p>
              </div>

              <span className="seller-product-variant-total-stock">
                활성 SKU 총 재고{" "}
                <strong>{totalStockQuantity.toLocaleString("ko-KR")}</strong>개
              </span>
            </div>

            {variants.length === 0 ? (
              <div className="seller-product-variant-empty">
                옵션명과 옵션 값을 모두 입력하면 SKU 조합이 자동으로 생성됩니다.
              </div>
            ) : (
              <div className="seller-product-variant-table-wrapper">
                <table className="seller-product-variant-table">
                  <thead>
                    <tr>
                      <th>옵션 조합</th>
                      <th>SKU</th>
                      <th>추가금</th>
                      <th>재고</th>
                      <th>판매</th>
                    </tr>
                  </thead>

                  <tbody>
                    {variants.map((variant) => (
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
                            maxLength={100}
                            placeholder="SKU 코드"
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

                          {variant.id !== null && (
                            <small className="seller-product-variant-existing">
                              기존 SKU
                            </small>
                          )}
                        </td>

                        <td>
                          <div className="seller-product-variant-number-input">
                            <input
                              type="text"
                              inputMode="numeric"
                              value={variant.additionalPrice}
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
                                const value = event.target.value.replace(
                                  /\D/g,
                                  "",
                                );

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
                                  variant.additionalPrice.trim() === ""
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

            <p className="seller-product-option-notice">
              기존 SKU의 옵션 조합은 변경하지 않습니다. 조합을 변경해야 할 경우
              기존 SKU를 판매 중지하고 새로운 조합을 등록합니다.
            </p>
          </div>
        </>
      )}
    </div>
  );
}
