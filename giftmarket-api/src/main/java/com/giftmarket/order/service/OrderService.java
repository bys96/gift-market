package com.giftmarket.order.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.cart.entity.CartItem;
import com.giftmarket.cart.repository.CartItemRepository;
import com.giftmarket.order.dto.request.OrderCreateRequest;
import com.giftmarket.order.dto.response.OrderCreateResponse;
import com.giftmarket.order.dto.response.OrderDetailResponse;
import com.giftmarket.order.dto.response.OrderSummaryResponse;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductOptionValue;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.entity.ProductVariantOptionValue;
import com.giftmarket.product.repository.ProductOptionGroupRepository;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.product.repository.ProductVariantOptionValueRepository;
import com.giftmarket.product.repository.ProductVariantRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter
            ORDER_NUMBER_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    private final CartItemRepository cartItemRepository;

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductOptionGroupRepository productOptionGroupRepository;
    private final ProductVariantOptionValueRepository
            productVariantOptionValueRepository;

    private final UserRepository userRepository;

    /**
     * 장바구니 선택 주문 생성.
     *
     * 하나의 트랜잭션 안에서:
     *
     * 1. CartItem 소유권 검증
     * 2. Product / Variant 재고 row lock
     * 3. 현재 판매 상태 / 옵션 상태 / 재고 재검증
     * 4. 서버 가격 기준 주문 Snapshot 생성
     * 5. 재고 차감
     * 6. 옵션상품 Product 총재고 동기화
     * 7. Order / OrderItem 저장
     * 8. 주문 성공한 CartItem 삭제
     *
     * 중간에 하나라도 실패하면 전체 rollback 됩니다.
     */
    @Transactional
    public OrderCreateResponse createOrder(
            Long userId,
            OrderCreateRequest request
    ) {
        User user = getAuthenticatedUser(userId);

        List<Long> cartItemIds =
                normalizeCartItemIds(
                        request.cartItemIds()
                );

        List<CartItem> cartItems =
                getOrderCartItems(
                        userId,
                        cartItemIds
                );

        /*
         * 여러 주문이 동시에 여러 Product row를 잠글 때
         * 서로 다른 순서로 lock을 획득하면 deadlock 위험이 커집니다.
         *
         * Product ID -> Variant ID 순으로 항상 고정합니다.
         */
        List<CartItem> sortedCartItems =
                cartItems.stream()
                        .sorted(
                                Comparator
                                        .comparing(
                                                (CartItem cartItem) ->
                                                        cartItem
                                                                .getProduct()
                                                                .getId()
                                        )
                                        .thenComparing(
                                                cartItem -> {
                                                    ProductVariant variant =
                                                            cartItem.getVariant();

                                                    return variant == null
                                                            ? Long.MIN_VALUE
                                                            : variant.getId();
                                                }
                                        )
                        )
                        .toList();

        Map<Long, Product> lockedProducts =
                new HashMap<>();

        Map<Long, ProductVariant> lockedVariants =
                new HashMap<>();

        List<PreparedOrderItem> preparedItems =
                new ArrayList<>();

        /*
         * 같은 상품의 Variant 여러 개를 주문할 수 있으므로
         * Product별 총 Variant 재고 동기화를 마지막에 한 번만 수행합니다.
         */
        Set<Long> variantProductIds =
                new LinkedHashSet<>();

        for (CartItem cartItem : sortedCartItems) {
            PreparedOrderItem preparedItem =
                    prepareOrderItem(
                            cartItem,
                            lockedProducts,
                            lockedVariants,
                            variantProductIds
                    );

            preparedItems.add(preparedItem);
        }

        long totalProductAmount =
                preparedItems.stream()
                        .mapToLong(
                                PreparedOrderItem::totalPrice
                        )
                        .sum();

        /*
         * 현재 Cart 정책과 동일하게 CartItem별 배송비를 계산합니다.
         *
         * 향후 판매자 묶음배송 / 배송비 템플릿을 도입하면
         * 이 계산 책임을 Shipping 정책 객체로 분리하면 됩니다.
         */
        long totalShippingFee =
                preparedItems.stream()
                        .mapToLong(
                                PreparedOrderItem::shippingFee
                        )
                        .sum();

        Order order =
                Order.create(
                        generateOrderNumber(),
                        user,
                        totalProductAmount,
                        totalShippingFee,
                        request.recipientName().trim(),
                        request.recipientPhone().trim(),
                        request.postalCode().trim(),
                        request.address().trim(),
                        normalizeNullableText(
                                request.addressDetail()
                        )
                );

        orderRepository.save(order);

        List<OrderItem> orderItems =
                preparedItems.stream()
                        .map(prepared ->
                                createOrderItem(
                                        order,
                                        prepared
                                )
                        )
                        .toList();

        orderItemRepository.saveAll(orderItems);

        /*
         * Variant 주문으로 변경된 Product들의
         * 활성 Variant 재고 합계를 Product.stockQuantity에 반영합니다.
         *
         * Product.changeStockQuantity()가
         * ON_SALE <-> SOLD_OUT 상태 동기화까지 담당합니다.
         */
        for (Long productId : variantProductIds) {
            Product product =
                    lockedProducts.get(productId);

            synchronizeVariantProductStock(
                    product,
                    productId
            );
        }

        /*
         * 주문 생성이 여기까지 성공했을 때만
         * 주문한 장바구니 항목을 제거합니다.
         *
         * 이후 과정에서 예외가 발생하면 Transaction rollback으로
         * 재고 / 주문 / CartItem 삭제가 전부 되돌아갑니다.
         */
        cartItemRepository.deleteAllByIdInAndUserId(
                cartItemIds,
                userId
        );

        return OrderCreateResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getMyOrders(
            Long userId
    ) {
        getAuthenticatedUser(userId);

        return orderRepository
                .findAllByUserIdOrderByOrderedAtDesc(
                        userId
                )
                .stream()
                .map(order -> {
                    List<OrderItem> orderItems =
                            orderItemRepository
                                    .findAllByOrderIdOrderByIdAsc(
                                            order.getId()
                                    );

                    return OrderSummaryResponse.from(
                            order,
                            orderItems
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getMyOrder(
            Long userId,
            Long orderId
    ) {
        getAuthenticatedUser(userId);

        Order order =
                orderRepository
                        .findByIdAndUserId(
                                orderId,
                                userId
                        )
                        .orElseThrow(() ->
                                new OrderException(
                                        "주문 정보를 찾을 수 없습니다."
                                )
                        );

        List<OrderItem> orderItems =
                orderItemRepository
                        .findAllByOrderIdOrderByIdAsc(
                                order.getId()
                        );

        return OrderDetailResponse.from(
                order,
                orderItems
        );
    }

    private PreparedOrderItem prepareOrderItem(
            CartItem cartItem,
            Map<Long, Product> lockedProducts,
            Map<Long, ProductVariant> lockedVariants,
            Set<Long> variantProductIds
    ) {
        Long productId =
                cartItem.getProduct().getId();

        Product product =
                lockedProducts.computeIfAbsent(
                        productId,
                        this::getLockedProduct
                );

        validateProductPurchasable(product);

        ProductVariant cartVariant =
                cartItem.getVariant();

        boolean hasOptions =
                hasProductOptions(productId);

        if (hasOptions && cartVariant == null) {
            throw new OrderException(
                    "상품 옵션 정보가 변경되었습니다. 장바구니를 다시 확인해주세요."
            );
        }

        if (!hasOptions && cartVariant != null) {
            throw new OrderException(
                    "상품 옵션 정보가 변경되었습니다. 장바구니를 다시 확인해주세요."
            );
        }

        ProductVariant variant = null;

        if (cartVariant != null) {
            Long variantId =
                    cartVariant.getId();

            variant =
                    lockedVariants.computeIfAbsent(
                            variantId,
                            id ->
                                    getLockedVariant(
                                            id,
                                            productId
                                    )
                    );

            validateVariantPurchasable(
                    variant,
                    cartItem.getQuantity()
            );

            variant.decreaseStock(
                    cartItem.getQuantity()
            );

            variantProductIds.add(productId);
        } else {
            validateProductStock(
                    product,
                    cartItem.getQuantity()
            );

            product.decreaseStock(
                    cartItem.getQuantity()
            );
        }

        long additionalPrice =
                variant == null
                        ? 0L
                        : variant.getAdditionalPrice();

        String optionSnapshot =
                variant == null
                        ? null
                        : createOptionSnapshot(
                        variant.getId()
                );

        Seller seller =
                product.getSeller();

        long unitPrice =
                product.getPrice()
                        + additionalPrice;

        long totalPrice =
                unitPrice
                        * cartItem.getQuantity();

        long shippingFee =
                product.isFreeShipping()
                        ? 0L
                        : product.getShippingFee();

        return new PreparedOrderItem(
                product,
                variant,
                seller,
                product.getName(),
                product.getBrandName(),
                seller.getStoreName(),
                product.getRepresentativeImageKey(),
                optionSnapshot,
                product.getPrice(),
                additionalPrice,
                unitPrice,
                cartItem.getQuantity(),
                totalPrice,
                product.isFreeShipping(),
                shippingFee
        );
    }

    private OrderItem createOrderItem(
            Order order,
            PreparedOrderItem prepared
    ) {
        return OrderItem.create(
                order,
                prepared.product(),
                prepared.variant(),
                prepared.seller(),
                prepared.productName(),
                prepared.brandName(),
                prepared.storeName(),
                prepared.representativeImageKey(),
                prepared.optionSnapshot(),
                prepared.productPrice(),
                prepared.additionalPrice(),
                prepared.quantity(),
                prepared.freeShipping(),
                prepared.shippingFee()
        );
    }

    private Product getLockedProduct(
            Long productId
    ) {
        return productRepository
                .findWithLockByIdAndDeletedAtIsNull(
                        productId
                )
                .orElseThrow(() ->
                        new OrderException(
                                "현재 구매할 수 없는 상품이 포함되어 있습니다."
                        )
                );
    }

    private ProductVariant getLockedVariant(
            Long variantId,
            Long productId
    ) {
        return productVariantRepository
                .findWithLockByIdAndProductId(
                        variantId,
                        productId
                )
                .orElseThrow(() ->
                        new OrderException(
                                "선택한 상품 옵션을 찾을 수 없습니다."
                        )
                );
    }

    private void validateProductPurchasable(
            Product product
    ) {
        if (product.isDeleted()) {
            throw new OrderException(
                    "삭제된 상품은 주문할 수 없습니다."
            );
        }

        if (product.getStatus()
                == ProductStatus.SOLD_OUT) {
            throw new OrderException(
                    "품절된 상품이 포함되어 있습니다."
            );
        }

        if (product.getStatus()
                != ProductStatus.ON_SALE) {
            throw new OrderException(
                    "현재 판매하지 않는 상품이 포함되어 있습니다."
            );
        }
    }

    private void validateProductStock(
            Product product,
            Integer quantity
    ) {
        validateOrderQuantity(quantity);

        if (product.getStockQuantity() < quantity) {
            throw new OrderException(
                    "상품 재고가 부족합니다. 장바구니를 다시 확인해주세요."
            );
        }
    }

    private void validateVariantPurchasable(
            ProductVariant variant,
            Integer quantity
    ) {
        validateOrderQuantity(quantity);

        if (!variant.isActive()) {
            throw new OrderException(
                    "현재 판매하지 않는 상품 옵션이 포함되어 있습니다."
            );
        }

        if (variant.getStockQuantity() < quantity) {
            throw new OrderException(
                    "선택한 상품 옵션의 재고가 부족합니다. 장바구니를 다시 확인해주세요."
            );
        }
    }

    private void validateOrderQuantity(
            Integer quantity
    ) {
        if (quantity == null
                || quantity <= 0) {
            throw new OrderException(
                    "올바르지 않은 주문 수량입니다."
            );
        }
    }

    private boolean hasProductOptions(
            Long productId
    ) {
        return !productOptionGroupRepository
                .findAllByProductIdOrderBySortOrderAsc(
                        productId
                )
                .isEmpty();
    }

    private String createOptionSnapshot(
            Long variantId
    ) {
        List<ProductVariantOptionValue>
                variantOptionValues =
                productVariantOptionValueRepository
                        .findAllByVariantId(
                                variantId
                        );

        if (variantOptionValues.isEmpty()) {
            throw new OrderException(
                    "상품 옵션 정보를 확인할 수 없습니다."
            );
        }

        return variantOptionValues.stream()
                .map(
                        ProductVariantOptionValue::getOptionValue
                )
                .sorted(
                        Comparator
                                .comparing(
                                        (ProductOptionValue optionValue) ->
                                                optionValue
                                                        .getOptionGroup()
                                                        .getSortOrder()
                                )
                                .thenComparing(
                                        ProductOptionValue::getSortOrder
                                )
                )
                .map(optionValue ->
                        optionValue
                                .getOptionGroup()
                                .getName()
                                + ": "
                                + optionValue.getValue()
                )
                .collect(
                        Collectors.joining(" / ")
                );
    }

    private void synchronizeVariantProductStock(
            Product product,
            Long productId
    ) {
        int totalStockQuantity =
                productVariantRepository
                        .findAllByProductIdAndActiveTrueOrderByIdAsc(
                                productId
                        )
                        .stream()
                        .mapToInt(
                                ProductVariant::getStockQuantity
                        )
                        .sum();

        product.changeStockQuantity(
                totalStockQuantity
        );
    }

    private List<Long> normalizeCartItemIds(
            List<Long> cartItemIds
    ) {
        if (cartItemIds == null
                || cartItemIds.isEmpty()) {
            throw new OrderException(
                    "주문할 상품을 선택해주세요."
            );
        }

        List<Long> normalized =
                cartItemIds.stream()
                        .distinct()
                        .toList();

        /*
         * 중복 ID를 조용히 제거하면 프론트 버그를 숨길 수 있으므로
         * 잘못된 요청으로 처리합니다.
         */
        if (normalized.size()
                != cartItemIds.size()) {
            throw new OrderException(
                    "중복된 장바구니 상품이 포함되어 있습니다."
            );
        }

        return normalized;
    }

    private List<CartItem> getOrderCartItems(
            Long userId,
            List<Long> cartItemIds
    ) {
        List<CartItem> cartItems =
                cartItemRepository
                        .findAllByIdInAndUserId(
                                cartItemIds,
                                userId
                        );

        if (cartItems.size()
                != cartItemIds.size()) {
            throw new OrderException(
                    "주문 상품 정보를 확인할 수 없습니다. 장바구니를 다시 확인해주세요."
            );
        }

        return cartItems;
    }

    private User getAuthenticatedUser(
            Long userId
    ) {
        if (userId == null) {
            throw new AuthenticationException(
                    "인증이 필요합니다."
            );
        }

        return userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new AuthenticationException(
                                "사용자 정보를 찾을 수 없습니다."
                        )
                );
    }

    /**
     * DB PK를 주문번호로 노출하지 않습니다.
     *
     * 예:
     * GM-20260813-A83F92C13D04
     */
    private String generateOrderNumber() {
        String date =
                LocalDate.now()
                        .format(
                                ORDER_NUMBER_DATE_FORMATTER
                        );

        String random =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 12)
                        .toUpperCase();

        return "GM-"
                + date
                + "-"
                + random;
    }

    private String normalizeNullableText(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private record PreparedOrderItem(

            Product product,

            ProductVariant variant,

            Seller seller,

            String productName,

            String brandName,

            String storeName,

            String representativeImageKey,

            String optionSnapshot,

            Long productPrice,

            Long additionalPrice,

            Long unitPrice,

            Integer quantity,

            Long totalPrice,

            boolean freeShipping,

            Long shippingFee

    ) {
    }
}