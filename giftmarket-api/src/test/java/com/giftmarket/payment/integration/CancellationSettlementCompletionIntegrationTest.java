package com.giftmarket.payment.integration;

import com.giftmarket.order.dto.response.OrderCancellationCompletionResult;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.order.service.OrderCancellationCompletionService;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentCancellation;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.entity.PaymentMethod;
import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.gateway.GatewayCancelResult;
import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.payment.service.PartialCancellationStart;
import com.giftmarket.payment.service.PartialPaymentCancellationTransactionService;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.service.CancellationSettlementLedgerService;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cancellation-settlement-completion;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.task.scheduling.enabled=false",
        "app.jwt.secret=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "app.jwt.refresh-token-encryption-key=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "payment.toss.secret-key=test-only-key"
})
class CancellationSettlementCompletionIntegrationTest {

    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 9, 20, 15, 0);

    @Autowired PartialPaymentCancellationTransactionService transactionService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PaymentCancellationRepository paymentCancellationRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired SellerOrderRepository sellerOrderRepository;
    @Autowired OrderCancellationRepository cancellationRepository;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactionTemplate;

    @MockitoBean OrderCancellationCompletionService completionService;
    @MockitoBean CancellationSettlementLedgerService cancellationSettlementLedgerService;

    private Long paymentId;
    private Long paymentCancellationId;
    private Long orderId;
    private Long sellerOrderId;
    private Long cancellationId;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            User buyer = User.createOAuthUser(
                    "cancellation-settlement-buyer@example.com",
                    "buyer",
                    null,
                    AuthProvider.GOOGLE,
                    "cancellation-settlement-buyer"
            );
            entityManager.persist(buyer);
            User sellerUser = User.createOAuthUser(
                    "cancellation-settlement-seller@example.com",
                    "seller",
                    null,
                    AuthProvider.GOOGLE,
                    "cancellation-settlement-seller"
            );
            entityManager.persist(sellerUser);
            Seller seller = Seller.create(sellerUser, "settlement-store", null);
            entityManager.persist(seller);

            Order order = Order.createPendingPayment(
                    "ORDER-CANCELLATION-SETTLEMENT",
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
            order.markPaid(LocalDateTime.of(2026, 9, 15, 13, 0));
            SellerOrder sellerOrder = SellerOrder.createPendingPayment(order, seller);
            entityManager.persist(sellerOrder);
            sellerOrder.markPaid();

            OrderCancellation cancellation = OrderCancellation.createProcessing(
                    order,
                    sellerOrder,
                    "cancellation-request-key",
                    "buyer cancellation",
                    LocalDateTime.of(2026, 9, 20, 14, 0)
            );
            entityManager.persist(cancellation);
            Payment payment = Payment.createReady(
                    order,
                    PaymentProvider.TOSS,
                    "ORDER-CANCELLATION-SETTLEMENT",
                    "payment-client-key",
                    "payment-confirm-key",
                    10_000L,
                    "KRW",
                    LocalDateTime.of(2026, 9, 15, 12, 0),
                    LocalDateTime.of(2026, 9, 15, 12, 30)
            );
            payment.complete(
                    "provider-payment-key",
                    "provider-transaction-key",
                    PaymentMethod.CARD,
                    null,
                    "DONE",
                    LocalDateTime.of(2026, 9, 15, 13, 0)
            );
            entityManager.persist(payment);
            PaymentCancellation paymentCancellation = PaymentCancellation.createPartial(
                    payment,
                    cancellation,
                    "payment-cancellation-client-key",
                    "payment-cancellation-idempotency-key",
                    3_000L,
                    "buyer cancellation",
                    LocalDateTime.of(2026, 9, 20, 14, 0)
            );
            entityManager.persist(paymentCancellation);
            entityManager.flush();

            paymentId = payment.getId();
            paymentCancellationId = paymentCancellation.getId();
            orderId = order.getId();
            sellerOrderId = sellerOrder.getId();
            cancellationId = cancellation.getId();
        });

        doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            OrderCancellation cancellation = cancellationRepository.findByIdForUpdate(id)
                    .orElseThrow();
            cancellation.complete(CANCELED_AT);
            return new OrderCancellationCompletionResult(
                    cancellation.getId(),
                    cancellation.getStatus(),
                    cancellation.getSellerOrder().getStatus()
            );
        }).when(completionService).complete(cancellationId);
    }

    @Test
    void settlementFailureRollsBackCancellationCompletionTransaction() {
        doThrow(new SettlementException("forced cancellation ledger failure"))
                .when(cancellationSettlementLedgerService)
                .recordCancellation(any(), any(), any());

        assertThatThrownBy(() -> transactionService.complete(start(), result()))
                .isInstanceOf(SettlementException.class);

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PAID);
        assertThat(paymentCancellationRepository.findById(paymentCancellationId)
                .orElseThrow().getStatus()).isEqualTo(PaymentCancellationStatus.REQUESTED);
        assertThat(cancellationRepository.findById(cancellationId).orElseThrow().getStatus())
                .isEqualTo(OrderCancellationStatus.PROCESSING);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
        assertThat(sellerOrderRepository.findById(sellerOrderId).orElseThrow().getStatus())
                .isEqualTo(SellerOrderStatus.PAID);
    }

    private PartialCancellationStart start() {
        return new PartialCancellationStart(
                PartialCancellationStart.Action.EXECUTE,
                cancellationId,
                paymentId,
                paymentCancellationId,
                PaymentProvider.TOSS,
                "provider-payment-key",
                "ORDER-CANCELLATION-SETTLEMENT",
                10_000L,
                3_000L,
                "KRW",
                "buyer cancellation",
                "payment-cancellation-idempotency-key"
        );
    }

    private GatewayCancelResult result() {
        return new GatewayCancelResult(
                GatewayPaymentStatus.PARTIALLY_CANCELED,
                "provider-payment-key",
                "cancellation-transaction-key",
                "ORDER-CANCELLATION-SETTLEMENT",
                10_000L,
                7_000L,
                "KRW",
                "PARTIAL_CANCELED",
                CANCELED_AT,
                3_000L,
                "DONE",
                7_000L
        );
    }
}
