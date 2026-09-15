package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.settlement.config.SettlementProperties;
import com.giftmarket.settlement.exception.SettlementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InitialSettlementLedgerService {

    public static final String SALE_PRODUCT_SOURCE_DETAIL = "SALE_PRODUCT";
    public static final String SALE_SHIPPING_SOURCE_DETAIL = "SALE_SHIPPING";
    public static final String COMMISSION_SOURCE_DETAIL = "COMMISSION";

    private final SellerOrderRepository sellerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SettlementLedgerService ledgerService;
    private final SettlementCommissionCalculator commissionCalculator;
    private final SettlementProperties settlementProperties;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInitialSales(Payment payment, Order order) {
        validatePaymentAndOrder(payment, order);

        List<SellerOrder> sellerOrders = sellerOrderRepository
                .findAllByOrderIdOrderByIdAsc(order.getId());
        List<OrderItem> orderItems = orderItemRepository
                .findAllByOrderIdOrderByIdAsc(order.getId());
        Map<Long, SellerOrderAllocation> allocations = initializeAllocations(
                order,
                sellerOrders
        );
        allocateItems(order, orderItems, allocations);
        validateOrderTotals(payment, order, allocations);
        createLedgers(payment.getApprovedAt(), allocations);
    }

    private Map<Long, SellerOrderAllocation> initializeAllocations(
            Order order,
            List<SellerOrder> sellerOrders
    ) {
        if (sellerOrders == null || sellerOrders.isEmpty()) {
            throw new SettlementException("결제 주문의 판매자 주문을 찾을 수 없습니다.");
        }

        Map<Long, SellerOrderAllocation> allocations = new LinkedHashMap<>();
        for (SellerOrder sellerOrder : sellerOrders) {
            if (sellerOrder == null || sellerOrder.getId() == null
                    || sellerOrder.getSeller() == null || sellerOrder.getSeller().getId() == null
                    || sellerOrder.getOrder() == null
                    || !Objects.equals(sellerOrder.getOrder().getId(), order.getId())
                    || sellerOrder.getStatus() != SellerOrderStatus.PAID
                    || allocations.put(
                            sellerOrder.getId(),
                            new SellerOrderAllocation(sellerOrder)
                    ) != null) {
                throw new SettlementException("판매자 주문의 정산 귀속 정보를 확인할 수 없습니다.");
            }
        }
        return allocations;
    }

    private void allocateItems(
            Order order,
            List<OrderItem> orderItems,
            Map<Long, SellerOrderAllocation> allocations
    ) {
        if (orderItems == null || orderItems.isEmpty()) {
            throw new SettlementException("결제 주문의 주문 상품을 찾을 수 없습니다.");
        }

        for (OrderItem orderItem : orderItems) {
            if (orderItem == null || orderItem.getOrder() == null
                    || !Objects.equals(orderItem.getOrder().getId(), order.getId())
                    || orderItem.getSellerOrder() == null
                    || orderItem.getSellerOrder().getId() == null) {
                throw new SettlementException("주문 상품의 정산 귀속 정보를 확인할 수 없습니다.");
            }

            SellerOrderAllocation allocation = allocations.get(
                    orderItem.getSellerOrder().getId()
            );
            if (allocation == null || orderItem.getSeller() == null
                    || !Objects.equals(
                            allocation.sellerOrder().getSeller().getId(),
                            orderItem.getSeller().getId()
                    )
                    || orderItem.getTotalPrice() == null || orderItem.getTotalPrice() < 0L
                    || orderItem.getShippingFee() == null || orderItem.getShippingFee() < 0L) {
                throw new SettlementException("주문 상품의 판매자 또는 금액 snapshot이 올바르지 않습니다.");
            }
            allocation.add(orderItem.getTotalPrice(), orderItem.getShippingFee());
        }

        if (allocations.values().stream().anyMatch(value -> value.itemCount() == 0)) {
            throw new SettlementException("주문 상품이 없는 판매자 주문은 정산할 수 없습니다.");
        }
    }

    private void validateOrderTotals(
            Payment payment,
            Order order,
            Map<Long, SellerOrderAllocation> allocations
    ) {
        if (order.getTotalProductAmount() == null || order.getTotalProductAmount() < 0L
                || order.getTotalShippingFee() == null || order.getTotalShippingFee() < 0L
                || order.getTotalAmount() == null || order.getTotalAmount() < 0L
                || payment.getAmount() == null || payment.getAmount() < 0L) {
            throw new SettlementException("주문 또는 결제 금액을 확인할 수 없습니다.");
        }

        long productTotal = 0L;
        long shippingTotal = 0L;
        long sellerOrderTotal = 0L;
        try {
            for (SellerOrderAllocation allocation : allocations.values()) {
                productTotal = Math.addExact(productTotal, allocation.productSalesAmount());
                shippingTotal = Math.addExact(shippingTotal, allocation.shippingSalesAmount());
                sellerOrderTotal = Math.addExact(
                        sellerOrderTotal,
                        allocation.initialSellerOrderAmount()
                );
            }
        } catch (ArithmeticException exception) {
            throw new SettlementException("판매자 주문별 정산 금액 합계를 안전하게 계산할 수 없습니다.");
        }

        if (!Objects.equals(productTotal, order.getTotalProductAmount())) {
            throw new SettlementException("판매자 주문별 상품 매출 합계가 주문 상품금액과 일치하지 않습니다.");
        }
        if (!Objects.equals(shippingTotal, order.getTotalShippingFee())) {
            throw new SettlementException("판매자 주문별 배송비 합계가 주문 배송비와 일치하지 않습니다.");
        }
        if (!Objects.equals(sellerOrderTotal, order.getTotalAmount())) {
            throw new SettlementException("판매자 주문별 총액 합계가 주문 총액과 일치하지 않습니다.");
        }
        if (!Objects.equals(payment.getAmount(), order.getTotalAmount())) {
            throw new SettlementException("결제 금액과 주문 총액이 일치하지 않습니다.");
        }
    }

    private void createLedgers(
            LocalDateTime approvedAt,
            Map<Long, SellerOrderAllocation> allocations
    ) {
        if (approvedAt == null) {
            throw new SettlementException("결제 승인 시각을 확인할 수 없습니다.");
        }
        int commissionRateBps = settlementProperties.getCommissionRateBps();

        for (SellerOrderAllocation allocation : allocations.values()) {
            SellerOrder sellerOrder = allocation.sellerOrder();
            Seller seller = sellerOrder.getSeller();
            long productSalesAmount = allocation.productSalesAmount();
            if (productSalesAmount <= 0L) {
                throw new SettlementException("판매자 주문의 상품 매출은 0보다 커야 합니다.");
            }

            ledgerService.recordProductSale(SettlementLedgerCommand.commission(
                    seller.getId(),
                    sellerOrder.getId(),
                    productSalesAmount,
                    sellerOrder.getId(),
                    SALE_PRODUCT_SOURCE_DETAIL,
                    approvedAt,
                    null,
                    commissionRateBps,
                    productSalesAmount
            ));

            if (allocation.shippingSalesAmount() > 0L) {
                ledgerService.recordShippingSale(SettlementLedgerCommand.standard(
                        seller.getId(),
                        sellerOrder.getId(),
                        allocation.shippingSalesAmount(),
                        sellerOrder.getId(),
                        SALE_SHIPPING_SOURCE_DETAIL,
                        approvedAt,
                        null
                ));
            }

            long commissionAmount = commissionCalculator.calculate(
                    productSalesAmount,
                    commissionRateBps
            );
            if (commissionAmount > 0L) {
                ledgerService.recordCommission(SettlementLedgerCommand.commission(
                        seller.getId(),
                        sellerOrder.getId(),
                        commissionAmount,
                        sellerOrder.getId(),
                        COMMISSION_SOURCE_DETAIL,
                        approvedAt,
                        null,
                        commissionRateBps,
                        productSalesAmount
                ));
            }
        }
    }

    private void validatePaymentAndOrder(Payment payment, Order order) {
        if (payment == null || order == null || payment.getOrder() == null
                || order.getId() == null
                || !Objects.equals(payment.getOrder().getId(), order.getId())
                || payment.getStatus() != PaymentStatus.PAID
                || order.getStatus() != OrderStatus.PAID) {
            throw new SettlementException("결제와 주문 정보가 일치하지 않습니다.");
        }
    }

    private static final class SellerOrderAllocation {

        private final SellerOrder sellerOrder;
        private long productSalesAmount;
        private long shippingSalesAmount;
        private int itemCount;

        private SellerOrderAllocation(SellerOrder sellerOrder) {
            this.sellerOrder = sellerOrder;
        }

        private void add(long productAmount, long shippingAmount) {
            try {
                productSalesAmount = Math.addExact(productSalesAmount, productAmount);
                shippingSalesAmount = Math.addExact(shippingSalesAmount, shippingAmount);
                itemCount = Math.addExact(itemCount, 1);
            } catch (ArithmeticException exception) {
                throw new SettlementException("판매자 주문 금액을 안전하게 합산할 수 없습니다.");
            }
        }

        private SellerOrder sellerOrder() {
            return sellerOrder;
        }

        private long productSalesAmount() {
            return productSalesAmount;
        }

        private long shippingSalesAmount() {
            return shippingSalesAmount;
        }

        private long initialSellerOrderAmount() {
            try {
                return Math.addExact(productSalesAmount, shippingSalesAmount);
            } catch (ArithmeticException exception) {
                throw new SettlementException("판매자 주문 총액을 안전하게 계산할 수 없습니다.");
            }
        }

        private int itemCount() {
            return itemCount;
        }
    }
}
