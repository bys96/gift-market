package com.giftmarket.order.service;

import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.ReturnInspectionResult;
import com.giftmarket.order.entity.ReturnRequestItem;
import com.giftmarket.order.entity.ExchangeRequestItem;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class OrderInventoryService {

    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    /**
     * OrderItem snapshot을 기준으로 예약/주문 재고를 복원합니다.
     * 호출 측 transaction 안에서 Order 상태 검증과 함께 실행해야 합니다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<OrderItem> restore(Long orderId) {
        List<OrderItem> orderItems = orderItemRepository
                .findAllByOrderIdOrderByIdAsc(orderId);

        if (orderItems.isEmpty()) {
            throw new OrderException("주문 상품 정보를 확인할 수 없습니다.");
        }

        restoreQuantities(orderItems.stream()
                .map(item -> new InventoryRestoreItem(item, item.getQuantity()))
                .toList());

        return orderItems;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreCancellationItems(List<OrderCancellationItem> cancellationItems) {
        if (cancellationItems == null || cancellationItems.isEmpty()) {
            throw new OrderException("Cancellation items are required for stock restoration.");
        }
        restoreQuantities(cancellationItems.stream()
                .map(item -> new InventoryRestoreItem(item.getOrderItem(), item.getQuantity()))
                .toList());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreReturnItems(List<ReturnRequestItem> returnItems) {
        if (returnItems == null || returnItems.isEmpty()) {
            throw new OrderException("Return items are required for stock restoration.");
        }
        List<ReturnRequestItem> restockable = returnItems.stream()
                .filter(item -> item.getInspectionResult() == ReturnInspectionResult.RESTOCKABLE)
                .toList();
        if (restockable.isEmpty()) return;
        restoreQuantities(restockable.stream()
                .map(item -> new InventoryRestoreItem(item.getOrderItem(), item.getQuantity()))
                .toList());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveExchangeTargets(List<ExchangeRequestItem> exchangeItems) {
        if (exchangeItems == null || exchangeItems.isEmpty()) {
            throw new OrderException("교환 요청 상품 정보가 필요합니다.");
        }

        List<ExchangeRequestItem> sortedItems = exchangeItems.stream()
                .sorted(Comparator
                        .comparing((ExchangeRequestItem item) -> item.getTargetProduct().getId())
                        .thenComparing(item -> item.getTargetVariant() == null
                                ? Long.MIN_VALUE : item.getTargetVariant().getId())
                        .thenComparing(item -> item.getOrderItem().getId()))
                .toList();

        Map<TargetStockKey, Integer> requiredByTarget = new LinkedHashMap<>();
        for (ExchangeRequestItem item : sortedItems) {
            if (item.getQuantity() <= 0 || item.getReservedQuantity() != 0
                    || item.getReleasedQuantity() != 0 || item.getConsumedQuantity() != 0) {
                throw new OrderException("교환 target 재고 예약 상태를 확인해주세요.");
            }
            TargetStockKey key = new TargetStockKey(
                    item.getTargetProduct().getId(),
                    item.getTargetVariant() == null ? null : item.getTargetVariant().getId()
            );
            requiredByTarget.merge(key, item.getQuantity(), (left, right) -> {
                try {
                    return Math.addExact(left, right);
                } catch (ArithmeticException exception) {
                    throw new OrderException("교환 target 예약 수량을 확인해주세요.");
                }
            });
        }

        Map<Long, Product> lockedProducts = new LinkedHashMap<>();
        Map<Long, ProductVariant> lockedVariants = new LinkedHashMap<>();
        for (TargetStockKey key : requiredByTarget.keySet()) {
            Product product = lockedProducts.computeIfAbsent(key.productId(), this::getLockedProduct);
            if (key.variantId() != null) {
                lockedVariants.computeIfAbsent(key.variantId(),
                        id -> getLockedVariant(id, product.getId()));
            }
        }

        for (Map.Entry<TargetStockKey, Integer> entry : requiredByTarget.entrySet()) {
            TargetStockKey key = entry.getKey();
            int required = entry.getValue();
            Product product = lockedProducts.get(key.productId());
            ProductVariant variant = key.variantId() == null ? null : lockedVariants.get(key.variantId());
            validateExchangeTarget(exchangeItems, key, product, variant);
            int currentStock = key.variantId() == null
                    ? product.getStockQuantity() : variant.getStockQuantity();
            if (currentStock < required) {
                throw new OrderException("교환 대상 재고가 부족합니다. 재고 확보 후 다시 승인해주세요.");
            }
        }

        Set<Long> variantProductIds = new LinkedHashSet<>();
        for (Map.Entry<TargetStockKey, Integer> entry : requiredByTarget.entrySet()) {
            TargetStockKey key = entry.getKey();
            int quantity = entry.getValue();
            if (key.variantId() == null) {
                lockedProducts.get(key.productId()).decreaseStock(quantity);
            } else {
                lockedVariants.get(key.variantId()).decreaseStock(quantity);
                variantProductIds.add(key.productId());
            }
        }

        for (Long productId : variantProductIds) {
            int totalStock = productVariantRepository.findAllByProductIdAndActiveTrueOrderByIdAsc(productId)
                    .stream().mapToInt(ProductVariant::getStockQuantity).sum();
            lockedProducts.get(productId).changeStockQuantity(totalStock);
        }

        for (ExchangeRequestItem item : sortedItems) {
            try {
                item.reserveTargetStock(item.getQuantity());
            } catch (IllegalArgumentException | IllegalStateException exception) {
                throw new OrderException(exception.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseExchangeTargets(List<ExchangeRequestItem> exchangeItems) {
        if (exchangeItems == null || exchangeItems.isEmpty()) {
            throw new OrderException("교환 요청 상품 정보가 필요합니다.");
        }
        List<ExchangeRequestItem> sorted = exchangeItems.stream()
                .sorted(Comparator.comparing((ExchangeRequestItem item) -> item.getOrderItem().getId()))
                .toList();
        Map<TargetStockKey, Integer> releaseByTarget = new LinkedHashMap<>();
        for (ExchangeRequestItem item : sorted) {
            int effective = item.getEffectiveReservedQuantity();
            if (effective == 0) continue;
            TargetStockKey key = new TargetStockKey(item.getTargetProduct().getId(),
                    item.getTargetVariant() == null ? null : item.getTargetVariant().getId());
            releaseByTarget.merge(key, effective, Math::addExact);
        }
        if (releaseByTarget.isEmpty()) return;

        Map<Long, Product> products = new LinkedHashMap<>();
        Map<Long, ProductVariant> variants = new LinkedHashMap<>();
        for (TargetStockKey key : releaseByTarget.keySet().stream().sorted().toList()) {
            Product product = products.computeIfAbsent(key.productId(), this::getLockedProduct);
            if (key.variantId() != null) variants.computeIfAbsent(key.variantId(), id -> getLockedVariant(id, product.getId()));
        }
        Set<Long> variantProductIds = new LinkedHashSet<>();
        for (Map.Entry<TargetStockKey, Integer> entry : releaseByTarget.entrySet()) {
            TargetStockKey key = entry.getKey();
            if (key.variantId() == null) products.get(key.productId()).increaseStock(entry.getValue());
            else {
                variants.get(key.variantId()).increaseStock(entry.getValue());
                variantProductIds.add(key.productId());
            }
        }
        for (Long productId : variantProductIds) {
            int total = productVariantRepository.findAllByProductIdAndActiveTrueOrderByIdAsc(productId)
                    .stream().mapToInt(ProductVariant::getStockQuantity).sum();
            products.get(productId).changeStockQuantity(total);
        }
        for (ExchangeRequestItem item : sorted) {
            int effective = item.getEffectiveReservedQuantity();
            if (effective > 0) item.releaseTargetStockReservation(effective);
        }
    }

    private void validateExchangeTarget(
            List<ExchangeRequestItem> items,
            TargetStockKey key,
            Product product,
            ProductVariant variant
    ) {
        if (product.isDeleted() || product.getStatus() != ProductStatus.ON_SALE) {
            throw new OrderException("현재 판매가 중지된 상품은 교환 승인할 수 없습니다.");
        }
        if (variant != null && (!variant.isActive()
                || !variant.getProduct().getId().equals(product.getId()))) {
            throw new OrderException("현재 사용할 수 없는 교환 대상 옵션입니다.");
        }
        for (ExchangeRequestItem item : items) {
            Long itemVariantId = item.getTargetVariant() == null ? null : item.getTargetVariant().getId();
            if (!item.getTargetProduct().getId().equals(key.productId())
                    || !java.util.Objects.equals(itemVariantId, key.variantId())) continue;
            if (!item.getOrderItem().getProduct().getId().equals(product.getId())
                    || (item.getOrderItem().getVariant() == null) != (variant == null)) {
                throw new OrderException("원 주문 상품과 같은 상품 구조로만 교환할 수 있습니다.");
            }
            long currentPrice;
            try {
                currentPrice = Math.addExact(product.getPrice(), variant == null ? 0L : variant.getAdditionalPrice());
            } catch (ArithmeticException exception) {
                throw new OrderException("교환 대상 옵션 가격을 확인할 수 없습니다.");
            }
            if (currentPrice != item.getOrderItem().getUnitPrice()) {
                throw new OrderException("가격이 변경된 교환 대상은 승인할 수 없습니다. 반품 후 다시 구매해 주세요.");
            }
        }
    }

    private void restoreQuantities(List<InventoryRestoreItem> restoreItems) {
        List<InventoryRestoreItem> sortedRestoreItems = restoreItems.stream()
                .sorted(
                        Comparator
                                .comparing((InventoryRestoreItem item) -> item.orderItem().getProduct().getId())
                                .thenComparing(item -> item.orderItem().getVariant() == null
                                        ? Long.MIN_VALUE
                                        : item.orderItem().getVariant().getId())
                )
                .toList();

        Map<Long, Product> lockedProducts = new HashMap<>();
        Map<Long, ProductVariant> lockedVariants = new HashMap<>();
        Set<Long> variantProductIds = new LinkedHashSet<>();

        for (InventoryRestoreItem restoreItem : sortedRestoreItems) {
            OrderItem orderItem = restoreItem.orderItem();
            int restoreQuantity = restoreItem.quantity();
            if (restoreQuantity <= 0 || restoreQuantity > orderItem.getQuantity()) {
                throw new OrderException("Invalid stock restoration quantity.");
            }
            Long productId = orderItem.getProduct().getId();
            Product product = lockedProducts.computeIfAbsent(
                    productId,
                    this::getLockedProduct
            );

            ProductVariant orderVariant = orderItem.getVariant();
            if (orderVariant == null) {
                product.increaseStock(restoreQuantity);
                continue;
            }

            Long variantId = orderVariant.getId();
            ProductVariant variant = lockedVariants.computeIfAbsent(
                    variantId,
                    id -> getLockedVariant(id, productId)
            );
            variant.increaseStock(restoreQuantity);
            variantProductIds.add(productId);
        }

        for (Long productId : variantProductIds) {
            Product product = lockedProducts.get(productId);
            int totalStockQuantity = productVariantRepository
                    .findAllByProductIdAndActiveTrueOrderByIdAsc(productId)
                    .stream()
                    .mapToInt(ProductVariant::getStockQuantity)
                    .sum();
            product.changeStockQuantity(totalStockQuantity);
        }

    }

    private record InventoryRestoreItem(OrderItem orderItem, int quantity) {
    }

    private record TargetStockKey(Long productId, Long variantId) implements Comparable<TargetStockKey> {
        @Override
        public int compareTo(TargetStockKey other) {
            int product = productId.compareTo(other.productId);
            if (product != 0) return product;
            if (variantId == null) return other.variantId == null ? 0 : -1;
            if (other.variantId == null) return 1;
            return variantId.compareTo(other.variantId);
        }
    }

    private Product getLockedProduct(Long productId) {
        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new OrderException(
                        "주문 상품 정보를 확인할 수 없습니다."
                ));
    }

    private ProductVariant getLockedVariant(Long variantId, Long productId) {
        return productVariantRepository
                .findWithLockByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new OrderException(
                        "주문 상품 옵션 정보를 확인할 수 없습니다."
                ));
    }
}
