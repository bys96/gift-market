package com.giftmarket.payment.service;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.payment.entity.PaymentCancellation;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PartialPaymentCancellationOrphanRecoveryServiceTest {

    @Mock OrderCancellationRepository orderCancellationRepository;
    @Mock PaymentCancellationRepository paymentCancellationRepository;
    private PartialPaymentCancellationOrphanRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new PartialPaymentCancellationOrphanRecoveryService(
                orderCancellationRepository,
                paymentCancellationRepository
        );
    }

    @Test
    void failsProcessingCancellationWhenPaymentCancellationWasNotCreated() {
        OrderCancellation cancellation = processingCancellation();
        given(orderCancellationRepository.findByIdForUpdate(4L))
                .willReturn(Optional.of(cancellation));
        given(paymentCancellationRepository.findByOrderCancellationId(4L))
                .willReturn(Optional.empty());

        service.failIfPaymentCancellationWasNotCreated(4L);

        assertThat(cancellation.getStatus()).isEqualTo(OrderCancellationStatus.FAILED);
        assertThat(cancellation.getFailedAt()).isNotNull();
    }

    @Test
    void keepsProcessingWhenRequestedPaymentCancellationExists() {
        OrderCancellation cancellation = processingCancellation();
        given(orderCancellationRepository.findByIdForUpdate(4L))
                .willReturn(Optional.of(cancellation));
        given(paymentCancellationRepository.findByOrderCancellationId(4L))
                .willReturn(Optional.of(mock(PaymentCancellation.class)));

        service.failIfPaymentCancellationWasNotCreated(4L);

        assertThat(cancellation.getStatus()).isEqualTo(OrderCancellationStatus.PROCESSING);
        assertThat(cancellation.getFailedAt()).isNull();
    }

    private OrderCancellation processingCancellation() {
        Order order = mock(Order.class);
        SellerOrder sellerOrder = mock(SellerOrder.class);
        given(sellerOrder.getOrder()).willReturn(order);
        return OrderCancellation.createProcessing(
                order,
                sellerOrder,
                "request-key",
                "구매자 상세 취소 사유",
                LocalDateTime.now()
        );
    }
}
