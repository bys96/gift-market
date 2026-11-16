package com.giftmarket.product.service;

import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.entity.ProductVariantOptionValue;
import com.giftmarket.product.exception.ProductConflictException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Option identity is a set of group/value IDs; display order is not identity. */
public final class ProductVariantMapping {
    private ProductVariantMapping() { }

    public static Set<String> parse(String key) {
        Set<String> result = new HashSet<>();
        Set<Long> groups = new HashSet<>();
        if (key == null || key.isBlank()) throw new IllegalArgumentException("empty combination");
        for (String entry : key.split("\\|", -1)) {
            String[] pair = entry.split(":", -1);
            if (pair.length != 2) throw new IllegalArgumentException("invalid combination");
            long group = Long.parseLong(pair[0]);
            long value = Long.parseLong(pair[1]);
            if (group <= 0 || value <= 0 || !groups.add(group)
                    || !result.add(group + ":" + value)) {
                throw new IllegalArgumentException("duplicate combination");
            }
        }
        return result;
    }

    public static boolean isValid(ProductVariant variant, List<ProductVariantOptionValue> mappings) {
        try {
            Set<String> expected = parse(variant.getCombinationKey());
            Set<String> actual = new HashSet<>();
            Set<Long> groups = new HashSet<>();
            for (ProductVariantOptionValue mapping : mappings) {
                var value = mapping.getOptionValue();
                var group = value.getOptionGroup();
                if (!variant.getProduct().getId().equals(group.getProduct().getId())
                        || !variant.getId().equals(mapping.getVariant().getId())
                        || !groups.add(group.getId())
                        || !actual.add(group.getId() + ":" + value.getId())) return false;
            }
            return expected.equals(actual) && mappings.size() == expected.size();
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }

    public static void requireValid(ProductVariant variant, List<ProductVariantOptionValue> mappings) {
        if (!isValid(variant, mappings)) {
            throw new ProductConflictException("SKU #" + variant.getId()
                    + "의 옵션 매핑이 조합 이력과 다릅니다. 저장을 중단하고 원본 이력을 대사해주세요. 자동 복원하지 않습니다.");
        }
    }
}
