package com.giftmarket.order.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.cart.entity.CartItem;
import com.giftmarket.cart.repository.CartItemRepository;
import com.giftmarket.order.dto.request.OrderCreateRequest;
import com.giftmarket.order.dto.request.DirectOrderCreateRequest;
import com.giftmarket.order.dto.response.OrderCreateResponse;
import com.giftmarket.order.dto.response.OrderDetailResponse;
import com.giftmarket.order.dto.response.OrderSummaryResponse;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.Shipment;
import com.giftmarket.order.entity.ShipmentType;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.order.repository.ShipmentRepository;
import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.payment.service.PaymentRefundBalance;
import com.giftmarket.payment.service.PaymentRefundBalanceService;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter
            ORDER_NUMBER_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String PAYMENT_CURRENCY = "KRW";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProperties paymentProperties;
    private final PaymentRefundBalanceService paymentRefundBalanceService;
    private final OrderInventoryService orderInventoryService;
    private final SellerOrderLifecycleService sellerOrderLifecycleService;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderCancellationRepository orderCancellationRepository;
    private final ShipmentRepository shipmentRepository;
    private final PurchaseConfirmationQuantities purchaseConfirmationQuantities;

    private final CartItemRepository cartItemRepository;

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductOptionGroupRepository productOptionGroupRepository;
    private final ProductVariantOptionValueRepository
            productVariantOptionValueRepository;

    private final UserRepository userRepository;

    /**
     * 장바구니 선택 주문 결제 준비.
     *
     * 하나의 트랜잭션 안에서:
     *
     * 1. CartItem 소유권 검증
     * 2. Product / Variant 재고 row lock
     * 3. 현재 판매 상태 / 옵션 상태 / 재고 재검증
     * 4. 서버 가격 기준 주문 Snapshot 생성
     * 5. 결제 대기 중 사용할 재고 예약 차감
     * 6. 옵션상품 Product 총재고 동기화
     * 7. PENDING_PAYMENT Order / OrderItem 저장
     * 8. READY Payment 저장
     *
     * 중간에 하나라도 실패하면 전체 rollback 됩니다.
     */
    @Transactional
    public OrderCreateResponse createOrder(
            Long userId,
            OrderCreateRequest request
    ) {
        User user = getAuthenticatedUserForUpdate(userId);

        Optional<OrderCreateResponse> existingPreparation =
                findExistingPreparation(
                        userId,
                        request.clientOrderRequestKey()
                );

        if (existingPreparation.isPresent()) {
            return existingPreparation.get();
        }

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
                Order.createPendingPayment(
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

        Map<Long, SellerOrder> sellerOrders =
                sellerOrderLifecycleService.createPendingPayment(
                        order,
                        preparedItems.stream()
                                .map(PreparedOrderItem::seller)
                                .toList()
                );

        List<OrderItem> orderItems =
                preparedItems.stream()
                        .map(prepared ->
                                createOrderItem(
                                        order,
                                        prepared,
                                        sellerOrders.get(
                                                prepared.seller().getId()
                                        )
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

        String orderName = createOrderNameFromPreparedItems(
                preparedItems
        );

        return createPaymentPreparation(
                order,
                request.clientOrderRequestKey(),
                orderName
        );
    }

    /**
     * 장바구니를 거치지 않는 단일 상품 바로구매 결제 준비.
     * Product / Variant를 직접 잠그고 서버의 현재 값으로 주문 Snapshot을 만듭니다.
     */
    @Transactional
    public OrderCreateResponse createDirectOrder(
            Long userId,
            DirectOrderCreateRequest request
    ) {
        User user = getAuthenticatedUserForUpdate(userId);

        Optional<OrderCreateResponse> existingPreparation =
                findExistingPreparation(
                        userId,
                        request.clientOrderRequestKey()
                );

        if (existingPreparation.isPresent()) {
            return existingPreparation.get();
        }

        Product product = getLockedDirectProduct(request.productId());

        validateProductPurchasable(product, true);

        boolean hasOptions = hasProductOptions(product.getId());

        if (hasOptions && request.variantId() == null) {
            throw new OrderException("상품 옵션을 선택해주세요.");
        }

        if (!hasOptions && request.variantId() != null) {
            throw new OrderException("옵션이 없는 상품에는 상품 옵션을 지정할 수 없습니다.");
        }

        ProductVariant variant = null;

        if (request.variantId() != null) {
            variant = getLockedDirectVariant(
                    request.variantId(),
                    product.getId()
            );

            validateVariantPurchasable(
                    variant,
                    request.quantity(),
                    true
            );

            variant.decreaseStock(request.quantity());
        } else {
            validateProductStock(
                    product,
                    request.quantity(),
                    true
            );

            product.decreaseStock(request.quantity());
        }

        long additionalPrice =
                variant == null
                        ? 0L
                        : variant.getAdditionalPrice();

        String optionSnapshot =
                variant == null
                        ? null
                        : createOptionSnapshot(variant.getId());

        Seller seller = product.getSeller();

        PreparedOrderItem preparedItem =
                new PreparedOrderItem(
                        product,
                        variant,
                        seller,
                        null,
                        product.getName(),
                        product.getBrandName(),
                        seller.getStoreName(),
                        product.getRepresentativeImageKey(),
                        optionSnapshot,
                        product.getPrice(),
                        additionalPrice,
                        product.getPrice() + additionalPrice,
                        request.quantity(),
                        (product.getPrice() + additionalPrice)
                                * request.quantity(),
                        product.isFreeShipping(),
                        product.isFreeShipping()
                                ? 0L
                                : product.getShippingFee(),
                        product.getReturnShippingFee(),
                        product.getExchangeShippingFee()
                );

        Order order =
                Order.createPendingPayment(
                        generateOrderNumber(),
                        user,
                        preparedItem.totalPrice(),
                        preparedItem.shippingFee(),
                        request.recipientName().trim(),
                        request.recipientPhone().trim(),
                        request.postalCode().trim(),
                        request.address().trim(),
                        normalizeNullableText(request.addressDetail())
                );

        orderRepository.save(order);
        Map<Long, SellerOrder> sellerOrders =
                sellerOrderLifecycleService.createPendingPayment(
                        order,
                        List.of(preparedItem.seller())
                );
        orderItemRepository.save(
                createOrderItem(
                        order,
                        preparedItem,
                        sellerOrders.get(preparedItem.seller().getId())
                )
        );

        if (variant != null) {
            synchronizeVariantProductStock(
                    product,
                    product.getId()
            );
        }

        return createPaymentPreparation(
                order,
                request.clientOrderRequestKey(),
                preparedItem.productName()
        );
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getMyOrders(
            Long userId
    ) {
        getAuthenticatedUser(userId);

        List<Order> orders = orderRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId);

        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream()
                .map(Order::getId)
                .toList();
        Map<Long, List<OrderItem>> itemsByOrderId = orderItemRepository
                .findAllByOrderIdInOrderByOrderIdAscIdAsc(orderIds)
                .stream()
                .collect(Collectors.groupingBy(
                        item -> item.getOrder().getId()
                ));
        Map<Long, List<SellerOrder>> sellerOrdersByOrderId =
                sellerOrderRepository
                        .findAllByOrderIdInOrderByOrderIdAscIdAsc(orderIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                sellerOrder -> sellerOrder.getOrder().getId()
                        ));

        return orders.stream()
                .map(order -> OrderSummaryResponse.from(
                        order,
                        itemsByOrderId.getOrDefault(order.getId(), List.of()),
                        sellerOrdersByOrderId
                                .getOrDefault(order.getId(), List.of())
                                .stream()
                                .map(SellerOrder::getStatus)
                                .toList()
                ))
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
        List<SellerOrder> sellerOrders = sellerOrderRepository
                .findAllByOrderIdOrderByIdAsc(order.getId());
        Map<Long, Shipment> originalShipments = shipmentRepository
                .findAllBySellerOrderIdInAndType(
                        sellerOrders.stream().map(SellerOrder::getId).toList(),
                        ShipmentType.ORIGINAL_OUTBOUND
                ).stream()
                .collect(Collectors.toMap(
                        shipment -> shipment.getSellerOrder().getId(),
                        shipment -> shipment
                ));

        Map<Long, Long> pendingCancellationQuantities = orderCancellationRepository
                .sumItemQuantitiesByStatuses(
                        orderItems.stream().map(OrderItem::getId).toList(),
                        Set.of(com.giftmarket.order.entity.OrderCancellationStatus.REQUESTED,
                                com.giftmarket.order.entity.OrderCancellationStatus.PROCESSING))
                .stream()
                .collect(Collectors.toMap(
                        com.giftmarket.order.repository.PendingCancellationQuantityProjection::getOrderItemId,
                        com.giftmarket.order.repository.PendingCancellationQuantityProjection::getPendingQuantity));

        var pendingClaims = purchaseConfirmationQuantities.load(
                orderItems.stream().map(OrderItem::getId).toList());
        Map<Long, Integer> confirmableQuantities = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getId, item -> {
                    Shipment original = originalShipments.get(item.getSellerOrder().getId());
                    if (original == null
                            || original.getStatus() != com.giftmarket.order.entity.ShipmentStatus.DELIVERED
                            || original.getDeliveredAt() == null) return 0;
                    return purchaseConfirmationQuantities.confirmable(item, pendingClaims);
                }));

        PaymentRefundBalance refundBalance = paymentRepository
                .findFirstByOrderIdAndOrderUserIdOrderByIdDesc(orderId, userId)
                .map(paymentRefundBalanceService::getBalance)
                .orElse(null);
        long refundedAmount = refundBalance == null
                ? 0L
                : refundBalance.succeededRefundAmount();
        long remainingPaymentAmount = refundBalance == null
                ? order.getTotalAmount()
                : Math.subtractExact(
                        refundBalance.originalAmount(),
                        refundBalance.succeededRefundAmount()
                );

        return OrderDetailResponse.from(
                order,
                orderItems,
                sellerOrders,
                originalShipments,
                pendingCancellationQuantities,
                confirmableQuantities,
                refundedAmount,
                remainingPaymentAmount
        );
    }

    @Transactional
    public OrderDetailResponse cancelOrder(
            Long userId,
            Long orderId
    ) {
        getAuthenticatedUser(userId);

        Order order =
                orderRepository
                        .findByIdAndUserIdForUpdate(
                                orderId,
                                userId
                        )
                        .orElseThrow(() ->
                                new OrderException(
                                        "주문 정보를 찾을 수 없습니다."
                                )
                        );

        if (order.getStatus()
                == OrderStatus.CANCELLED) {
            throw new OrderException(
                    "이미 취소된 주문입니다."
            );
        }

        if (order.getStatus()
                != OrderStatus.ORDERED) {
            throw new OrderException(
                    "현재 상태에서는 주문을 취소할 수 없습니다."
            );
        }

        List<OrderItem> orderItems =
                orderInventoryService.restore(order.getId());

        order.cancel();
        sellerOrderLifecycleService.cancel(order.getId());

        return OrderDetailResponse.from(
                order,
                orderItems,
                sellerOrderRepository.findAllByOrderIdOrderByIdAsc(order.getId()),
                Map.of()
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

        validateProductPurchasable(product, false);

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
                    cartItem.getQuantity(),
                    false
            );

            variant.decreaseStock(
                    cartItem.getQuantity()
            );

            variantProductIds.add(productId);
        } else {
            validateProductStock(
                    product,
                    cartItem.getQuantity(),
                    false
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
                cartItem.getId(),
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
                shippingFee,
                product.getReturnShippingFee(),
                product.getExchangeShippingFee()
        );
    }

    private OrderItem createOrderItem(
            Order order,
            PreparedOrderItem prepared,
            SellerOrder sellerOrder
    ) {
        if (sellerOrder == null
                || !sellerOrder.getSeller().getId()
                .equals(prepared.seller().getId())) {
            throw new OrderException("판매자 주문 연결 정보를 확인할 수 없습니다.");
        }
        return OrderItem.create(
                order,
                prepared.product(),
                prepared.variant(),
                prepared.seller(),
                sellerOrder,
                prepared.sourceCartItemId(),
                prepared.productName(),
                prepared.brandName(),
                prepared.storeName(),
                prepared.representativeImageKey(),
                prepared.optionSnapshot(),
                prepared.productPrice(),
                prepared.additionalPrice(),
                prepared.quantity(),
                prepared.freeShipping(),
                prepared.shippingFee(),
                prepared.returnShippingFee(),
                prepared.exchangeShippingFee()
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

    private Product getLockedDirectProduct(
            Long productId
    ) {
        return productRepository
                .findWithLockByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() ->
                        new OrderException(
                                "현재 구매할 수 없는 상품입니다."
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

    private ProductVariant getLockedDirectVariant(
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
                                "선택한 옵션 정보를 확인할 수 없습니다."
                        )
                );
    }

    private void validateProductPurchasable(
            Product product,
            boolean directPurchase
    ) {
        if (product.isDeleted()) {
            throw new OrderException(
                    directPurchase
                            ? "현재 구매할 수 없는 상품입니다."
                            : "현재 구매할 수 없는 상품이 포함되어 있습니다. 장바구니를 다시 확인해주세요."
            );
        }

        if (product.getStatus()
                == ProductStatus.SOLD_OUT) {
            throw new OrderException(
                    directPurchase
                            ? "상품 재고가 부족합니다. 상품 정보를 다시 확인해주세요."
                            : "품절된 상품이 포함되어 있습니다. 장바구니를 다시 확인해주세요."
            );
        }

        if (product.getStatus()
                != ProductStatus.ON_SALE) {
            throw new OrderException(
                    directPurchase
                            ? "현재 판매가 중지된 상품입니다."
                            : "현재 판매가 중지된 상품이 포함되어 있습니다. 장바구니를 다시 확인해주세요."
            );
        }
    }

    private void validateProductStock(
            Product product,
            Integer quantity,
            boolean directPurchase
    ) {
        validateOrderQuantity(quantity);

        if (product.getStockQuantity() < quantity) {
            throw new OrderException(
                    directPurchase
                            ? "상품 재고가 부족합니다. 상품 정보를 다시 확인해주세요."
                            : "상품 재고가 부족합니다. 장바구니를 다시 확인해주세요."
            );
        }
    }

    private void validateVariantPurchasable(
            ProductVariant variant,
            Integer quantity,
            boolean directPurchase
    ) {
        validateOrderQuantity(quantity);

        if (!variant.isActive()) {
            throw new OrderException(
                    directPurchase
                            ? "선택한 옵션은 현재 구매할 수 없습니다."
                            : "현재 구매할 수 없는 옵션이 포함되어 있습니다. 장바구니를 다시 확인해주세요."
            );
        }

        if (variant.getStockQuantity() < quantity) {
            throw new OrderException(
                    directPurchase
                            ? "선택한 옵션의 재고가 부족합니다."
                            : "선택한 상품 옵션의 재고가 부족합니다. 장바구니를 다시 확인해주세요."
            );
        }
    }

    private void validateOrderQuantity(
            Integer quantity
    ) {
        if (quantity == null
                || quantity <= 0) {
            throw new OrderException(
                    "구매 수량을 다시 확인해주세요."
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

    private User getAuthenticatedUserForUpdate(
            Long userId
    ) {
        if (userId == null) {
            throw new AuthenticationException(
                    "인증이 필요합니다."
            );
        }

        return userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new AuthenticationException(
                                "사용자 정보를 찾을 수 없습니다."
                        )
                );
    }

    private Optional<OrderCreateResponse> findExistingPreparation(
            Long userId,
            String clientOrderRequestKey
    ) {
        return paymentRepository
                .findByClientRequestKeyAndOrderUserId(
                        clientOrderRequestKey,
                        userId
                )
                .map(payment -> {
                    Order order = payment.getOrder();

                    if (order.getStatus()
                            != OrderStatus.PENDING_PAYMENT
                            || payment.getStatus()
                            != PaymentStatus.READY) {
                        throw new OrderException(
                                "현재 결제 준비 상태를 다시 사용할 수 없습니다."
                        );
                    }

                    List<OrderItem> orderItems =
                            orderItemRepository
                                    .findAllByOrderIdOrderByIdAsc(
                                            order.getId()
                                    );

                    if (orderItems.isEmpty()) {
                        throw new OrderException(
                                "주문 상품 정보를 확인할 수 없습니다."
                        );
                    }

                    return OrderCreateResponse.from(
                            order,
                            payment,
                            createOrderNameFromOrderItems(
                                    orderItems
                            )
                    );
                });
    }

    private OrderCreateResponse createPaymentPreparation(
            Order order,
            String clientOrderRequestKey,
            String orderName
    ) {
        LocalDateTime requestedAt = LocalDateTime.now();

        Payment payment = Payment.createReady(
                order,
                PaymentProvider.TOSS,
                generateMerchantPaymentId(order.getOrderNumber()),
                clientOrderRequestKey,
                UUID.randomUUID().toString(),
                order.getTotalAmount(),
                PAYMENT_CURRENCY,
                requestedAt,
                requestedAt.plusMinutes(
                        paymentProperties.getReservationMinutes()
                )
        );

        paymentRepository.save(payment);

        return OrderCreateResponse.from(
                order,
                payment,
                orderName
        );
    }

    private String createOrderNameFromPreparedItems(
            List<PreparedOrderItem> preparedItems
    ) {
        PreparedOrderItem firstItem = preparedItems.getFirst();

        return createOrderName(
                firstItem.productName(),
                preparedItems.size()
        );
    }

    private String createOrderNameFromOrderItems(
            List<OrderItem> orderItems
    ) {
        OrderItem firstItem = orderItems.getFirst();

        return createOrderName(
                firstItem.getProductName(),
                orderItems.size()
        );
    }

    private String createOrderName(
            String firstProductName,
            int itemCount
    ) {
        if (itemCount <= 1) {
            return firstProductName;
        }

        return firstProductName
                + " 외 "
                + (itemCount - 1)
                + "건";
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

    private String generateMerchantPaymentId(
            String orderNumber
    ) {
        String random = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();

        return "PAY-"
                + orderNumber
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

            Long sourceCartItemId,

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

            Long shippingFee,

            Long returnShippingFee,

            Long exchangeShippingFee

    ) {
    }
}
