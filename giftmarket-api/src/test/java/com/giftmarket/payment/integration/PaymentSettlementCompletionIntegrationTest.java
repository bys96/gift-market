package com.giftmarket.payment.integration;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentMethod;
import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.gateway.GatewayPaymentQueryResult;
import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.payment.service.PaymentTransactionService;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.service.InitialSettlementLedgerService;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-settlement-completion;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.task.scheduling.enabled=false",
        "app.jwt.secret=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "app.jwt.refresh-token-encryption-key=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "payment.toss.secret-key=test-only-key"
})
class PaymentSettlementCompletionIntegrationTest {

    private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 9, 15, 13, 0);

    @Autowired PaymentTransactionService transactionService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired SellerOrderRepository sellerOrderRepository;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactionTemplate;

    @MockitoBean InitialSettlementLedgerService initialSettlementLedgerService;

    private Long userId;
    private Long orderId;
    private Long sellerOrderId;
    private Long paymentId;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            User buyer = User.createOAuthUser(
                    "payment-settlement-buyer@example.com",
                    "buyer",
                    null,
                    AuthProvider.GOOGLE,
                    "payment-settlement-buyer"
            );
            entityManager.persist(buyer);

            User sellerUser = User.createOAuthUser(
                    "payment-settlement-seller@example.com",
                    "seller",
                    null,
                    AuthProvider.GOOGLE,
                    "payment-settlement-seller"
            );
            entityManager.persist(sellerUser);
            Seller seller = Seller.create(sellerUser, "settlement-store", null);
            entityManager.persist(seller);

            Order order = Order.createPendingPayment(
                    "ORDER-PAYMENT-SETTLEMENT",
                    buyer,
                    10_000L,
                    0L,
                    "buyer",
                    "01012345678",
                    "12345",
                    "address",
                    null
            );
            entityManager.persist(order);
            SellerOrder sellerOrder = SellerOrder.createPendingPayment(order, seller);
            entityManager.persist(sellerOrder);
            Payment payment = Payment.createReady(
                    order,
                    PaymentProvider.TOSS,
                    "PAYMENT-SETTLEMENT",
                    "client-key",
                    "confirm-key",
                    10_000L,
                    "KRW",
                    LocalDateTime.now(),
                    LocalDateTime.now().plusMinutes(30)
            );
            entityManager.persist(payment);
            entityManager.flush();

            userId = buyer.getId();
            orderId = order.getId();
            sellerOrderId = sellerOrder.getId();
            paymentId = payment.getId();
        });
    }

    @Test
    void ledgerFailureRollsBackCompletionAndReconciliationRetriesSamePrimitive() {
        transactionService.startConfirm(
                userId,
                paymentId,
                new PaymentConfirmRequest(
                        "provider-key",
                        "PAYMENT-SETTLEMENT",
                        10_000L
                )
        );
        doThrow(new SettlementException("forced ledger failure"))
                .when(initialSettlementLedgerService)
                .recordInitialSales(any(), any());

        assertThatThrownBy(() -> transactionService.reconcile(
                paymentId,
                paidQueryResult()
        )).isInstanceOf(SettlementException.class);

        assertState(
                PaymentStatus.CONFIRMING,
                OrderStatus.PENDING_PAYMENT,
                SellerOrderStatus.PENDING_PAYMENT
        );

        reset(initialSettlementLedgerService);
        transactionService.reconcile(paymentId, paidQueryResult());

        assertState(PaymentStatus.PAID, OrderStatus.PAID, SellerOrderStatus.PAID);
        verify(initialSettlementLedgerService).recordInitialSales(any(), any());
    }

    private void assertState(
            PaymentStatus paymentStatus,
            OrderStatus orderStatus,
            SellerOrderStatus sellerOrderStatus
    ) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        Order order = orderRepository.findById(orderId).orElseThrow();
        SellerOrder sellerOrder = sellerOrderRepository.findById(sellerOrderId).orElseThrow();

        assertThat(payment.getStatus()).isEqualTo(paymentStatus);
        assertThat(order.getStatus()).isEqualTo(orderStatus);
        assertThat(sellerOrder.getStatus()).isEqualTo(sellerOrderStatus);
    }

    private GatewayPaymentQueryResult paidQueryResult() {
        return new GatewayPaymentQueryResult(
                GatewayPaymentStatus.PAID,
                "provider-key",
                "transaction-key",
                "PAYMENT-SETTLEMENT",
                10_000L,
                "KRW",
                PaymentMethod.CARD,
                null,
                "DONE",
                APPROVED_AT
        );
    }
}
