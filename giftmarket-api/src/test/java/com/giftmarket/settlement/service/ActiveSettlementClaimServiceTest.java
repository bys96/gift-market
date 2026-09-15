package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.ExchangeRequestStatus;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.repository.ExchangeRequestRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.ReturnRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActiveSettlementClaimServiceTest {

    @Mock OrderCancellationRepository cancellationRepository;
    @Mock ReturnRequestRepository returnRepository;
    @Mock ExchangeRequestRepository exchangeRepository;

    @Test
    void combinesSellerOrdersWithAnyActiveClaim() {
        ActiveSettlementClaimService service = new ActiveSettlementClaimService(
                cancellationRepository,
                returnRepository,
                exchangeRepository
        );
        List<Long> orderIds = List.of(1L, 2L, 3L);
        given(cancellationRepository.findSellerOrderIdsWithStatuses(
                org.mockito.ArgumentMatchers.eq(orderIds),
                org.mockito.ArgumentMatchers.anyCollection()
        )).willReturn(List.of(1L));
        given(returnRepository.findSellerOrderIdsWithStatuses(
                org.mockito.ArgumentMatchers.eq(orderIds),
                org.mockito.ArgumentMatchers.anyCollection()
        )).willReturn(List.of(2L));
        given(exchangeRepository.findSellerOrderIdsWithStatuses(
                org.mockito.ArgumentMatchers.eq(orderIds),
                org.mockito.ArgumentMatchers.anyCollection()
        )).willReturn(List.of(3L));

        assertThat(service.findActiveSellerOrderIds(orderIds)).containsExactlyInAnyOrder(1L, 2L, 3L);

        ArgumentCaptor<Collection<OrderCancellationStatus>> cancellations = collectionCaptor();
        ArgumentCaptor<Collection<ReturnRequestStatus>> returns = collectionCaptor();
        ArgumentCaptor<Collection<ExchangeRequestStatus>> exchanges = collectionCaptor();
        verify(cancellationRepository).findSellerOrderIdsWithStatuses(
                org.mockito.ArgumentMatchers.eq(orderIds), cancellations.capture()
        );
        verify(returnRepository).findSellerOrderIdsWithStatuses(
                org.mockito.ArgumentMatchers.eq(orderIds), returns.capture()
        );
        verify(exchangeRepository).findSellerOrderIdsWithStatuses(
                org.mockito.ArgumentMatchers.eq(orderIds), exchanges.capture()
        );
        assertThat(cancellations.getValue()).containsExactlyInAnyOrder(
                OrderCancellationStatus.REQUESTED,
                OrderCancellationStatus.PROCESSING
        );
        assertThat(returns.getValue()).containsExactlyInAnyOrder(
                ReturnRequestStatus.REQUESTED,
                ReturnRequestStatus.APPROVED,
                ReturnRequestStatus.COLLECTING,
                ReturnRequestStatus.RECEIVED,
                ReturnRequestStatus.INSPECTED,
                ReturnRequestStatus.REFUNDING
        );
        assertThat(exchanges.getValue()).containsExactlyInAnyOrder(
                ExchangeRequestStatus.REQUESTED,
                ExchangeRequestStatus.APPROVED,
                ExchangeRequestStatus.PAYMENT_PENDING,
                ExchangeRequestStatus.COLLECTING,
                ExchangeRequestStatus.RECEIVED,
                ExchangeRequestStatus.INSPECTED,
                ExchangeRequestStatus.RESHIPPING
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> ArgumentCaptor<Collection<T>> collectionCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }
}
