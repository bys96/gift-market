package com.giftmarket.admin.service;

import com.giftmarket.admin.exception.AdminOrderException;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.repository.*;
import com.giftmarket.user.entity.*;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {
    @Mock UserRepository users; @Mock OrderRepository orders; @Mock PaymentRepository payments;
    @Mock SellerOrderRepository sellerOrders; @Mock OrderItemRepository items; @Mock ShipmentRepository shipments;
    @Mock OrderCancellationRepository cancellations; @Mock ReturnRequestRepository returns;
    @Mock ExchangeRequestRepository exchanges; @Mock PaymentCancellationRepository paymentCancellations;
    @Mock User admin;
    AdminOrderService service;

    @BeforeEach void setUp(){service=new AdminOrderService(users,orders,payments,sellerOrders,items,shipments,cancellations,returns,exchanges,paymentCancellations);}
    void admin(){given(users.findById(1L)).willReturn(Optional.of(admin));given(admin.getRole()).willReturn(UserRole.ADMIN);}

    @Test void rejectsNullAdmin(){assertThatThrownBy(()->service.getOrders(null,0,20,null,null,null,null)).isInstanceOf(AuthenticationException.class);verifyNoInteractions(orders);}
    @Test void rejectsMissingAndNonAdmin(){given(users.findById(1L)).willReturn(Optional.empty());assertThatThrownBy(()->service.getOrder(1L,2L)).isInstanceOf(AuthenticationException.class).hasMessage("사용자를 찾을 수 없습니다.");given(users.findById(1L)).willReturn(Optional.of(admin));given(admin.getRole()).willReturn(UserRole.USER);assertThatThrownBy(()->service.getOrder(1L,2L)).isInstanceOf(AuthenticationException.class).hasMessage("관리자 권한이 필요합니다.");}
    @Test void passesTrimmedFiltersPaginationAndStableSort(){admin();given(orders.findAdminOrders(any(),any(),any(),any(),any())).willReturn(Page.empty());service.getOrders(1L,2,20,"  GM  ",OrderStatus.PAID,PaymentStatus.PAID,SellerOrderStatus.SHIPPED);ArgumentCaptor<Pageable> p=ArgumentCaptor.forClass(Pageable.class);verify(orders).findAdminOrders(eq("GM"),eq(OrderStatus.PAID),eq(PaymentStatus.PAID),eq(SellerOrderStatus.SHIPPED),p.capture());assertThat(p.getValue().getPageNumber()).isEqualTo(2);assertThat(p.getValue().getSort().getOrderFor("orderedAt").isDescending()).isTrue();assertThat(p.getValue().getSort().getOrderFor("id").isDescending()).isTrue();}
    @Test void mapsBuyerPaymentItemAndMultiSellerStatuses(){admin();Order o=mock(Order.class);User buyer=mock(User.class);Payment payment=mock(Payment.class);AdminOrderItemSummaryProjection summary=mock(AdminOrderItemSummaryProjection.class);SellerOrder first=mock(SellerOrder.class),second=mock(SellerOrder.class);given(o.getId()).willReturn(10L);given(o.getUser()).willReturn(buyer);given(buyer.getId()).willReturn(20L);given(payment.getOrder()).willReturn(o);given(payment.getStatus()).willReturn(PaymentStatus.PAID);given(summary.getOrderId()).willReturn(10L);given(summary.getRepresentativeProductName()).willReturn("선물");given(summary.getProductTypeCount()).willReturn(2L);given(summary.getTotalItemCount()).willReturn(3L);given(first.getOrder()).willReturn(o);given(second.getOrder()).willReturn(o);given(first.getStatus()).willReturn(SellerOrderStatus.PREPARING);given(second.getStatus()).willReturn(SellerOrderStatus.SHIPPED);given(orders.findAdminOrders(any(),any(),any(),any(),any())).willReturn(new PageImpl<>(List.of(o)));given(payments.findLatestByOrderIds(List.of(10L))).willReturn(List.of(payment));given(items.summarizeAdminOrders(List.of(10L))).willReturn(List.of(summary));given(sellerOrders.findAllByOrderIdInOrderByOrderIdAscIdAsc(List.of(10L))).willReturn(List.of(first,second));var result=service.getOrders(1L,0,20,null,null,null,null).content().getFirst();assertThat(result.userId()).isEqualTo(20L);assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.PAID);assertThat(result.representativeProductName()).isEqualTo("선물");assertThat(result.sellerOrderStatuses()).containsExactly(SellerOrderStatus.PREPARING,SellerOrderStatus.SHIPPED);}
    @Test void throwsNotFoundForMissingOrder(){admin();given(orders.findAdminById(99L)).willReturn(Optional.empty());assertThatThrownBy(()->service.getOrder(1L,99L)).isInstanceOf(AdminOrderException.class).hasMessage("주문을 찾을 수 없습니다.");}
    @Test void returnsDetailWithoutPaymentOrSellerOrdersAndCountsClaims(){admin();Order o=mock(Order.class);User buyer=mock(User.class);given(o.getId()).willReturn(10L);given(o.getUser()).willReturn(buyer);given(buyer.getId()).willReturn(20L);given(orders.findAdminById(10L)).willReturn(Optional.of(o));given(payments.findFirstByOrderIdOrderByIdDesc(10L)).willReturn(Optional.empty());given(sellerOrders.findAllByOrderIdInOrderByOrderIdAscIdAsc(List.of(10L))).willReturn(List.of());given(cancellations.countByOrderId(10L)).willReturn(2L);given(returns.countByOrderId(10L)).willReturn(1L);given(exchanges.countByOrderId(10L)).willReturn(3L);var result=service.getOrder(1L,10L);assertThat(result.payment()).isNull();assertThat(result.sellerOrders()).isEmpty();assertThat(result.claims().cancellationCount()).isEqualTo(2);assertThat(result.claims().returnCount()).isEqualTo(1);assertThat(result.claims().exchangeCount()).isEqualTo(3);assertThat(result.refund().succeededAmount()).isZero();}
}
