package com.giftmarket.order.integration;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.entity.Shipment;
import com.giftmarket.order.entity.ShipmentStatus;
import com.giftmarket.order.entity.ShipmentType;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.order.repository.ShipmentRepository;
import com.giftmarket.order.service.SellerOrderManagementService;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.service.SettlementEligibilityService;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:settlement-delivery-eligibility;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.task.scheduling.enabled=false",
        "app.jwt.secret=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "app.jwt.refresh-token-encryption-key=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "payment.toss.secret-key=test-only-key"
})
class SettlementDeliveryEligibilityIntegrationTest {

    @Autowired SellerOrderManagementService sellerOrderManagementService;
    @Autowired SellerOrderRepository sellerOrderRepository;
    @Autowired ShipmentRepository shipmentRepository;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactionTemplate;

    @MockitoBean SettlementEligibilityService settlementEligibilityService;

    private Long sellerUserId;
    private Long sellerOrderId;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            User buyer = User.createOAuthUser(
                    "delivery-eligibility-buyer@example.com",
                    "buyer",
                    null,
                    AuthProvider.GOOGLE,
                    "delivery-eligibility-buyer"
            );
            entityManager.persist(buyer);
            User sellerUser = User.createOAuthUser(
                    "delivery-eligibility-seller@example.com",
                    "seller",
                    null,
                    AuthProvider.GOOGLE,
                    "delivery-eligibility-seller"
            );
            entityManager.persist(sellerUser);
            Seller seller = Seller.create(sellerUser, "delivery-store", null);
            entityManager.persist(seller);

            LocalDateTime shippedAt = LocalDateTime.of(2026, 9, 14, 14, 0);
            Order order = Order.createPendingPayment(
                    "ORDER-DELIVERY-ELIGIBILITY",
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
            order.markPaid(shippedAt.minusDays(1));

            SellerOrder sellerOrder = SellerOrder.createPendingPayment(order, seller);
            entityManager.persist(sellerOrder);
            sellerOrder.markPaid();
            sellerOrder.prepare(shippedAt.minusHours(1));
            sellerOrder.markShipped(shippedAt);

            Shipment shipment = Shipment.createShipped(
                    sellerOrder,
                    ShipmentType.ORIGINAL_OUTBOUND,
                    "carrier",
                    "tracking-number",
                    shippedAt
            );
            entityManager.persist(shipment);
            entityManager.flush();

            sellerUserId = sellerUser.getId();
            sellerOrderId = sellerOrder.getId();
        });
    }

    @Test
    void eligibilityFailureRollsBackShipmentAndSellerOrderDelivery() {
        doThrow(new SettlementException("forced eligibility failure"))
                .when(settlementEligibilityService)
                .activateInitialSalesEligibility(any(), any());

        assertThatThrownBy(() -> sellerOrderManagementService.deliver(
                sellerUserId,
                sellerOrderId
        )).isInstanceOf(SettlementException.class);

        SellerOrder sellerOrder = sellerOrderRepository.findById(sellerOrderId).orElseThrow();
        Shipment shipment = shipmentRepository.findBySellerOrderIdAndType(
                sellerOrderId,
                ShipmentType.ORIGINAL_OUTBOUND
        ).orElseThrow();
        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.SHIPPED);
        assertThat(sellerOrder.getDeliveredAt()).isNull();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        assertThat(shipment.getDeliveredAt()).isNull();
    }
}
