package com.giftmarket.order.service;

import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductVariant;
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
