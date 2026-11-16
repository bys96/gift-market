package com.giftmarket.product.service;

import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.entity.ProductVariantOptionValue;
import com.giftmarket.product.exception.ProductConflictException;
import com.giftmarket.product.repository.ProductOptionGroupRepository;
import com.giftmarket.product.repository.ProductVariantOptionValueRepository;
import com.giftmarket.product.repository.ProductVariantRepository;
import com.giftmarket.product.repository.ProductRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Guards existing inventory owners; never transfers stock between Product and Variant. */
@Service
@RequiredArgsConstructor
public class ProductInventoryPolicy {
    private final ProductVariantRepository variants;
    private final ProductVariantOptionValueRepository mappings;
    private final ProductOptionGroupRepository groups;
    private final OrderItemRepository orderItems;
    private final ProductRepository products;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public void requirePayable(List<OrderItem> items) {
        entityManager.flush();
        for (OrderItem item : items.stream().sorted(java.util.Comparator
                .comparing((OrderItem item) -> item.getProduct().getId())
                .thenComparing(item -> item.getVariant() == null ? 0L : item.getVariant().getId())).toList()) {
            Product product = products.findByIdForUpdate(item.getProduct().getId()).orElseThrow();
            entityManager.refresh(product, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            ProductVariant variant = item.getVariant() == null ? null
                    : variants.findWithLockByIdAndProductId(item.getVariant().getId(), product.getId()).orElseThrow();
            if (variant != null) entityManager.refresh(variant, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (product.isDeleted() || product.isAdminHidden()
                    || product.getSeller().getStatus() != com.giftmarket.seller.entity.SellerStatus.ACTIVE
                    || product.getStatus() == ProductStatus.DRAFT || product.getStatus() == ProductStatus.HIDDEN) {
                throw new ProductConflictException("현재 판매할 수 없는 상품입니다. 신규 결제를 진행할 수 없습니다.");
            }
            // Reserved stock may be zero/SOLD_OUT. Never reserve or deduct it a second time.
            requirePurchasable(product, variant);
        }
    }

    public boolean usesVariants(Long productId) {
        return variants.existsByProductId(productId);
    }

    public void requireUnmixedHistory(Long productId) {
        if (hasMixedHistory(productId)) {
            throw new ProductConflictException("일반 상품 주문과 옵션 재고가 혼재한 상품입니다. 재고를 자동 보정하지 않습니다. 운영 대사가 필요합니다.");
        }
    }

    public boolean hasMixedHistory(Long productId) {
        return usesVariants(productId) && orderItems.existsByProductIdAndVariantIsNull(productId);
    }

    public void requireEditable(Long productId) {
        requireUnmixedHistory(productId);
        List<ProductVariant> all = variants.findAllByProductIdOrderByIdAsc(productId);
        if (all.isEmpty()) return;
        var byVariant = mappings.findAllByVariantIdIn(all.stream().map(ProductVariant::getId).toList())
                .stream().collect(Collectors.groupingBy(mapping -> mapping.getVariant().getId()));
        all.forEach(variant -> ProductVariantMapping.requireValid(variant,
                byVariant.getOrDefault(variant.getId(), List.of())));
    }

    public void requireConfiguration(Product product, boolean hasRequestedGroups, boolean initialRegistration) {
        requireEditable(product.getId());
        if (initialRegistration) {
            if (product.getStatus() != ProductStatus.DRAFT || orderItems.existsByProductId(product.getId())) {
                throw new ProductConflictException("최초 상품 등록에서만 재고 관리 방식을 선택할 수 있습니다.");
            }
        } else if (!usesVariants(product.getId()) && hasRequestedGroups) {
            throw new ProductConflictException("기존 일반 상품은 옵션 재고 방식으로 변경할 수 없습니다. 새 상품으로 등록해주세요.");
        }
    }

    public void requireDirectStock(Product product) {
        requireUnmixedHistory(product.getId());
        if (usesVariants(product.getId())) {
            throw new ProductConflictException("옵션 상품은 보관 여부와 관계없이 Variant별 재고를 사용합니다. 일반 상품으로 전환하거나 합계 재고를 직접 수정할 수 없습니다.");
        }
    }

    public void requirePurchasable(Product product, ProductVariant variant) {
        requireUnmixedHistory(product.getId());
        boolean variantManaged = usesVariants(product.getId());
        if (variantManaged != (variant != null)) {
            throw new ProductConflictException("상품의 재고 관리 방식과 주문 옵션이 다릅니다. 보관된 옵션 상품은 일반 상품으로 구매할 수 없습니다.");
        }
        if (!variantManaged) {
            if (!groups.findAllByProductIdOrderBySortOrderAsc(product.getId()).isEmpty()) {
                throw new ProductConflictException("상품 옵션 등록 상태를 확인해야 합니다. 구매할 수 없습니다.");
            }
            return;
        }
        var selected = mappings.findAllByVariantId(variant.getId());
        ProductVariantMapping.requireValid(variant, selected);
        var activeGroups = groups.findAllByProductIdOrderBySortOrderAsc(product.getId());
        var selectedGroups = selected.stream().map(mapping -> mapping.getOptionValue().getOptionGroup().getId())
                .collect(Collectors.toSet());
        if (!variant.isActive() || activeGroups.isEmpty()
                || selected.stream().anyMatch(mapping -> mapping.getOptionValue().isArchived()
                        || mapping.getOptionValue().getOptionGroup().isArchived())
                || !selectedGroups.equals(activeGroups.stream().map(group -> group.getId()).collect(Collectors.toSet()))) {
            throw new ProductConflictException("보관되었거나 현재 판매 조합과 다른 옵션입니다. 구매 또는 신규 결제를 진행할 수 없습니다.");
        }
    }
}
