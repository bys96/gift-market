package com.giftmarket.order.service;

import com.giftmarket.order.dto.response.SellerOrderCancellationPageResponse;
import com.giftmarket.order.dto.response.SellerOrderCancellationResponse;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.OrderCancellationItemRepository;
import com.giftmarket.order.repository.OrderCancellationOwnershipProjection;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SellerOrderCancellationServiceTest {

    private static final long USER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final long PAYMENT_ID = 3L;
    private static final long ORDER_ID = 4L;
    private static final long SELLER_ORDER_ID = 5L;
    private static final long CANCELLATION_ID = 6L;
    private static final long ORDER_ITEM_ID = 7L;

    @Mock SellerRepository sellerRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock OrderRepository orderRepository;
    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock OrderCancellationRepository cancellationRepository;
    @Mock OrderCancellationItemRepository cancellationItemRepository;

    private SellerOrderCancellationService service;
    private Seller seller;
    private Payment payment;
    private Order order;
    private SellerOrder sellerOrder;
    private OrderCancellation cancellation;
    private OrderCancellationItem cancellationItem;
    private OrderCancellationOwnershipProjection ownership;

    @BeforeEach
    void setUp() {
        service = new SellerOrderCancellationService(
                sellerRepository,
                paymentRepository,
                orderRepository,
                sellerOrderRepository,
                cancellationRepository,
                cancellationItemRepository
        );
        seller = mock(Seller.class);
        given(seller.getId()).willReturn(SELLER_ID);
        given(seller.getStatus()).willReturn(SellerStatus.ACTIVE);
        given(sellerRepository.findByUserId(USER_ID)).willReturn(Optional.of(seller));

        User buyer = mock(User.class);
        order = Order.createPendingPayment(
                "GM-ORDER", buyer, 20_000L, 0L,
                "수령인", "010-1234-5678", "12345", "서울", null
        );
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.markPaid(LocalDateTime.now());

        sellerOrder = SellerOrder.createPendingPayment(order, seller);
        ReflectionTestUtils.setField(sellerOrder, "id", SELLER_ORDER_ID);
        sellerOrder.markPaid();
        sellerOrder.prepare(LocalDateTime.now());

        cancellation = requestedCancellation(true);
        OrderItem orderItem = orderItem();
        cancellationItem = OrderCancellationItem.create(cancellation, orderItem, 1);

        payment = mock(Payment.class);
        given(payment.getId()).willReturn(PAYMENT_ID);
        given(payment.getStatus()).willReturn(PaymentStatus.PAID);

        ownership = mock(OrderCancellationOwnershipProjection.class);
        given(ownership.getOrderId()).willReturn(ORDER_ID);
        given(ownership.getSellerOrderId()).willReturn(SELLER_ORDER_ID);
        given(cancellationRepository.findOwnership(CANCELLATION_ID, SELLER_ID))
                .willReturn(Optional.of(ownership));
        given(paymentRepository.findFirstByOrderIdOrderByIdDesc(ORDER_ID))
                .willReturn(Optional.of(payment));
        given(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .willReturn(Optional.of(payment));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(sellerOrderRepository.findByIdAndSellerIdForUpdate(SELLER_ORDER_ID, SELLER_ID))
                .willReturn(Optional.of(sellerOrder));
        given(cancellationRepository.findByIdForUpdate(CANCELLATION_ID))
                .willReturn(Optional.of(cancellation));
        given(cancellationRepository.findById(CANCELLATION_ID))
                .willReturn(Optional.of(cancellation));
        given(cancellationItemRepository.findAllByOrderCancellationIdOrderByIdAsc(CANCELLATION_ID))
                .willReturn(List.of(cancellationItem));
    }

    @Test
    void listsOnlySellerApprovalCancellationsWithItems() {
        given(cancellationRepository.findSellerApprovalCancellations(
                SELLER_ID,
                OrderCancellationStatus.REQUESTED,
                PageRequest.of(0, 20)
        )).willReturn(new PageImpl<>(
                List.of(cancellation),
                PageRequest.of(0, 20),
                1
        ));
        given(cancellationItemRepository
                .findAllByOrderCancellationIdInOrderByOrderCancellationIdAscOrderItemIdAsc(
                        List.of(CANCELLATION_ID)
                )).willReturn(List.of(cancellationItem));

        SellerOrderCancellationPageResponse response = service.getCancellations(
                USER_ID,
                OrderCancellationStatus.REQUESTED,
                0,
                20
        );

        assertThat(response.cancellations()).singleElement()
                .satisfies(value -> {
                    assertThat(value.cancellationId()).isEqualTo(CANCELLATION_ID);
                    assertThat(value.recipientName()).isEqualTo("수령인");
                    assertThat(value.items()).singleElement()
                            .satisfies(item -> {
                                assertThat(item.productName()).isEqualTo("상품 snapshot");
                                assertThat(item.requestedQuantity()).isEqualTo(1);
                            });
                });
    }

    @Test
    void anotherSellerCannotReadOrProcessCancellation() {
        given(cancellationRepository.findOwnership(CANCELLATION_ID, SELLER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCancellation(USER_ID, CANCELLATION_ID))
                .isInstanceOf(SellerException.class);
        assertThatThrownBy(() -> service.approve(USER_ID, CANCELLATION_ID))
                .isInstanceOf(SellerException.class);
        verify(paymentRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void approvesRequestedPreparingCancellation() {
        SellerOrderCancellationResponse response = service.approve(USER_ID, CANCELLATION_ID);

        assertThat(response.status()).isEqualTo(OrderCancellationStatus.PROCESSING);
        assertThat(response.processingAt()).isNotNull();
        assertThat(cancellation.getRejectedAt()).isNull();
    }

    @Test
    void rejectsRequestedCancellationWithTrimmedReason() {
        SellerOrderCancellationResponse response = service.reject(
                USER_ID,
                CANCELLATION_ID,
                "  출고 준비 완료  "
        );

        assertThat(response.status()).isEqualTo(OrderCancellationStatus.REJECTED);
        assertThat(response.rejectedReason()).isEqualTo("출고 준비 완료");
        assertThat(response.rejectedAt()).isNotNull();
    }

    @Test
    void blankOrTooLongRejectReasonIsRejectedBeforeLocks() {
        assertThatThrownBy(() -> service.reject(USER_ID, CANCELLATION_ID, "  "))
                .isInstanceOf(SellerException.class);
        assertThatThrownBy(() -> service.reject(USER_ID, CANCELLATION_ID, "가".repeat(501)))
                .isInstanceOf(SellerException.class);

        verify(paymentRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void immediatePaidFlowCannotBeApprovedOrRejectedBySeller() {
        cancellation = requestedCancellation(false);
        given(cancellationRepository.findByIdForUpdate(CANCELLATION_ID))
                .willReturn(Optional.of(cancellation));

        assertThatThrownBy(() -> service.approve(USER_ID, CANCELLATION_ID))
                .isInstanceOf(SellerException.class);
        assertThatThrownBy(() -> service.reject(USER_ID, CANCELLATION_ID, "거절"))
                .isInstanceOf(SellerException.class);
    }

    @Test
    void processedCancellationCannotBeApprovedOrRejectedAgain() {
        cancellation.startProcessing(LocalDateTime.now());

        assertThatThrownBy(() -> service.approve(USER_ID, CANCELLATION_ID))
                .isInstanceOf(SellerException.class);
        assertThatThrownBy(() -> service.reject(USER_ID, CANCELLATION_ID, "거절"))
                .isInstanceOf(SellerException.class);
    }

    @Test
    void shippedSellerOrderOrCanceledOrderCannotBeApproved() {
        ReflectionTestUtils.setField(sellerOrder, "status", SellerOrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.approve(USER_ID, CANCELLATION_ID))
                .isInstanceOf(SellerException.class);

        ReflectionTestUtils.setField(sellerOrder, "status", SellerOrderStatus.PREPARING);
        ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.approve(USER_ID, CANCELLATION_ID))
                .isInstanceOf(SellerException.class);
    }

    @Test
    void locksPaymentOrderSellerOrderAndCancellationInThatOrder() {
        service.approve(USER_ID, CANCELLATION_ID);

        InOrder locks = inOrder(
                paymentRepository,
                orderRepository,
                sellerOrderRepository,
                cancellationRepository
        );
        locks.verify(paymentRepository).findByIdForUpdate(PAYMENT_ID);
        locks.verify(orderRepository).findByIdForUpdate(ORDER_ID);
        locks.verify(sellerOrderRepository)
                .findByIdAndSellerIdForUpdate(SELLER_ORDER_ID, SELLER_ID);
        locks.verify(cancellationRepository).findByIdForUpdate(CANCELLATION_ID);
    }

    private OrderCancellation requestedCancellation(boolean requiresSellerApproval) {
        OrderCancellation value = OrderCancellation.createRequested(
                order,
                sellerOrder,
                UUID.randomUUID().toString(),
                "단순 변심",
                requiresSellerApproval,
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(value, "id", CANCELLATION_ID);
        return value;
    }

    private OrderItem orderItem() {
        OrderItem value = OrderItem.create(
                order,
                mock(Product.class),
                null,
                seller,
                sellerOrder,
                null,
                "상품 snapshot",
                null,
                "상점",
                null,
                "색상: 블랙",
                20_000L,
                0L,
                2,
                true,
                0L
        );
        ReflectionTestUtils.setField(value, "id", ORDER_ITEM_ID);
        return value;
    }
}
