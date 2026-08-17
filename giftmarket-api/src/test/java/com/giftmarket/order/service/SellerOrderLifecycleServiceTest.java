package com.giftmarket.order.service;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.seller.entity.Seller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SellerOrderLifecycleServiceTest {

    @Mock
    private SellerOrderRepository sellerOrderRepository;

    @Test
    void createsOnePendingSellerOrderPerSeller() {
        SellerOrderLifecycleService service =
                new SellerOrderLifecycleService(sellerOrderRepository);
        Order order = mock(Order.class);
        Seller firstSeller = seller(10L);
        Seller secondSeller = seller(20L);

        Map<Long, SellerOrder> result = service.createPendingPayment(
                order,
                List.of(firstSeller, firstSeller, secondSeller)
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(10L).getSeller()).isSameAs(firstSeller);
        assertThat(result.get(20L).getSeller()).isSameAs(secondSeller);
        assertThat(result.values())
                .allMatch(value -> value.getStatus()
                        == SellerOrderStatus.PENDING_PAYMENT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<SellerOrder>> captor =
                ArgumentCaptor.forClass(Collection.class);
        verify(sellerOrderRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void synchronizesPaidAndCancelledStatesForWholeOrder() {
        SellerOrderLifecycleService service =
                new SellerOrderLifecycleService(sellerOrderRepository);
        Order order = mock(Order.class);
        SellerOrder first = SellerOrder.createPendingPayment(order, seller(10L));
        SellerOrder second = SellerOrder.createPendingPayment(order, seller(20L));
        given(sellerOrderRepository.findAllByOrderIdOrderByIdAsc(1L))
                .willReturn(List.of(first, second));

        service.markPaid(1L);
        assertThat(List.of(first.getStatus(), second.getStatus()))
                .containsOnly(SellerOrderStatus.PAID);

        service.cancel(1L);
        assertThat(List.of(first.getStatus(), second.getStatus()))
                .containsOnly(SellerOrderStatus.CANCELLED);
    }

    private Seller seller(Long id) {
        Seller seller = mock(Seller.class);
        lenient().when(seller.getId()).thenReturn(id);
        return seller;
    }
}
