package com.giftmarket.order.repository;

import com.giftmarket.order.dto.request.DirectOrderCreateRequest;
import com.giftmarket.order.dto.response.OrderCreateResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.service.OrderService;
import com.giftmarket.order.service.PurchaseConfirmationQuantities;
import com.giftmarket.product.entity.*;
import com.giftmarket.product.repository.*;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:claim-pending-quantity;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.task.scheduling.enabled=false",
        "app.jwt.secret=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "payment.toss.secret-key=test-only-key"
})
@Transactional
class ClaimPendingQuantityRepositoryTest {

    private static final int QUANTITY = 10;
    private static final int STOCK = 30;
    private static final long PRODUCT_PRICE = 10_000L;
    private static final long ADDITIONAL_PRICE = 1_000L;

    @Autowired OrderService orderService;
    @Autowired PurchaseConfirmationQuantities quantities;
    @Autowired UserRepository userRepository;
    @Autowired SellerRepository sellerRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductOptionGroupRepository optionGroupRepository;
    @Autowired ProductOptionValueRepository optionValueRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired ProductVariantOptionValueRepository variantOptionValueRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired OrderCancellationRepository cancellationRepository;
    @Autowired OrderCancellationItemRepository cancellationItemRepository;
    @Autowired ReturnRequestRepository returnRepository;
    @Autowired ReturnRequestItemRepository returnItemRepository;
    @Autowired ExchangeRequestRepository exchangeRepository;
    @Autowired ExchangeRequestItemRepository exchangeItemRepository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void noClaimsReturnZeroWithoutMixingOrderItems() {
        ItemFixture first = createOrderItem("zero-a");
        ItemFixture second = createOrderItem("zero-b");

        entityManager.flush();
        entityManager.clear();

        OrderItem firstItem = orderItemRepository.findById(first.orderItemId()).orElseThrow();
        OrderItem secondItem = orderItemRepository.findById(second.orderItemId()).orElseThrow();
        PurchaseConfirmationQuantities.PendingQuantities pending = quantities.load(
                List.of(firstItem.getId(), secondItem.getId())
        );

        assertThat(pending.cancellations()).isEmpty();
        assertThat(pending.returns()).isEmpty();
        assertThat(pending.exchanges()).isEmpty();
        assertThat(quantities.confirmable(firstItem, pending)).isEqualTo(QUANTITY);
        assertThat(quantities.confirmable(secondItem, pending)).isEqualTo(QUANTITY);
    }

    @Test
    void activeClaimsAreSeparatedAndTerminalClaimsAreNotCounted() {
        ItemFixture primary = createOrderItem("primary");
        ItemFixture isolated = createOrderItem("isolated");
        OrderItem primaryItem = orderItemRepository.findById(primary.orderItemId()).orElseThrow();
        OrderItem isolatedItem = orderItemRepository.findById(isolated.orderItemId()).orElseThrow();

        primaryItem.confirmCancellation(1);
        primaryItem.confirmReturn(1);
        primaryItem.confirmExchange(1);
        primaryItem.confirmPurchase(1);

        createCancellation(primaryItem, 2, OrderCancellationStatus.PROCESSING, "active-cancel");
        createCancellation(primaryItem, 1, OrderCancellationStatus.COMPLETED, "done-cancel");
        createCancellation(primaryItem, 1, OrderCancellationStatus.REJECTED, "rejected-cancel");
        createReturn(primaryItem, 1, ReturnRequestStatus.REFUNDING, "active-return");
        createReturn(primaryItem, 1, ReturnRequestStatus.COMPLETED, "done-return");
        createReturn(primaryItem, 1, ReturnRequestStatus.CANCELED, "canceled-return");
        createExchange(primaryItem, 1, ExchangeRequestStatus.PAYMENT_PENDING, "active-exchange");
        createExchange(primaryItem, 1, ExchangeRequestStatus.COMPLETED, "done-exchange");
        createExchange(primaryItem, 1, ExchangeRequestStatus.FAILED, "failed-exchange");

        createCancellation(isolatedItem, 4, OrderCancellationStatus.REQUESTED, "isolated-cancel");
        createReturn(isolatedItem, 4, ReturnRequestStatus.APPROVED, "isolated-return");
        createExchange(isolatedItem, 4, ExchangeRequestStatus.REQUESTED, "isolated-exchange");

        entityManager.flush();
        entityManager.clear();

        OrderItem reloadedPrimary = orderItemRepository.findById(primary.orderItemId()).orElseThrow();
        OrderItem reloadedIsolated = orderItemRepository.findById(isolated.orderItemId()).orElseThrow();
        PurchaseConfirmationQuantities.PendingQuantities pending = quantities.load(
                List.of(reloadedPrimary.getId(), reloadedIsolated.getId())
        );

        assertThat(pending.cancellations())
                .containsEntry(reloadedPrimary.getId(), 2L)
                .containsEntry(reloadedIsolated.getId(), 4L)
                .hasSize(2);
        assertThat(pending.returns())
                .containsEntry(reloadedPrimary.getId(), 1L)
                .containsEntry(reloadedIsolated.getId(), 4L)
                .hasSize(2);
        assertThat(pending.exchanges())
                .containsEntry(reloadedPrimary.getId(), 1L)
                .containsEntry(reloadedIsolated.getId(), 4L)
                .hasSize(2);
        assertThat(reloadedPrimary.getExchangedQuantity()).isEqualTo(1);
        assertThat(quantities.confirmable(reloadedPrimary, pending)).isEqualTo(3);
        assertThat(quantities.confirmable(reloadedIsolated, pending)).isZero();
    }

    private void createCancellation(
            OrderItem item, int quantity,
            OrderCancellationStatus status, String suffix
    ) {
        OrderCancellation cancellation = cancellationRepository.save(OrderCancellation.createRequested(
                item.getOrder(), item.getSellerOrder(), key(suffix), "test", LocalDateTime.now()
        ));
        cancellationItemRepository.save(OrderCancellationItem.create(cancellation, item, quantity));
        entityManager.flush();
        jdbcTemplate.update(
                "update order_cancellations set status = ? where id = ?", status.name(), cancellation.getId()
        );
    }

    private void createReturn(
            OrderItem item, int quantity,
            ReturnRequestStatus status, String suffix
    ) {
        ReturnRequest request = returnRepository.save(ReturnRequest.createRequested(
                item.getOrder(), item.getSellerOrder(), key(suffix), ReturnReasonType.DEFECTIVE, "test",
                "recipient", "010-1111-2222", "12345", "address", null, LocalDateTime.now()
        ));
        returnItemRepository.save(ReturnRequestItem.create(request, item, quantity));
        entityManager.flush();
        jdbcTemplate.update("update return_requests set status = ? where id = ?", status.name(), request.getId());
    }

    private void createExchange(
            OrderItem item, int quantity,
            ExchangeRequestStatus status, String suffix
    ) {
        ExchangeRequest request = exchangeRepository.save(ExchangeRequest.createRequested(
                item.getOrder(), item.getSellerOrder(), key(suffix), ExchangeReasonType.CHANGE_OF_MIND, "test",
                "recipient", "010-1111-2222", "12345", "address", null,
                "recipient", "010-1111-2222", "12345", "address", null, LocalDateTime.now()
        ));
        exchangeItemRepository.save(ExchangeRequestItem.create(
                request, item, quantity, item.getProduct(), item.getVariant(),
                item.getProductName(), item.getOptionSnapshot(), item.getUnitPrice()
        ));
        entityManager.flush();
        jdbcTemplate.update("update exchange_requests set status = ? where id = ?", status.name(), request.getId());
    }

    private ItemFixture createOrderItem(String suffix) {
        User buyer = userRepository.save(User.createOAuthUser(
                "buyer-" + suffix + "@example.test", "buyer", null,
                AuthProvider.GOOGLE, "buyer-" + suffix
        ));
        User owner = userRepository.save(User.createOAuthUser(
                "seller-" + suffix + "@example.test", "seller", null,
                AuthProvider.GOOGLE, "seller-" + suffix
        ));
        Seller seller = sellerRepository.save(Seller.create(owner, "store-" + suffix, "description"));
        Category category = categoryRepository.save(Category.create(null, "category-" + suffix, 1));
        Product product = productRepository.save(Product.createDraft(
                seller, category, "product-" + suffix, "brand", "summary", "description",
                PRODUCT_PRICE, STOCK, "products/test/image.jpg", false,
                3_000L, 1, 3_000L, 6_000L
        ));
        ProductOptionGroup group = optionGroupRepository.save(ProductOptionGroup.create(product, "color", 1));
        ProductOptionValue value = optionValueRepository.save(ProductOptionValue.create(group, "red", 1));
        ProductVariant variant = variantRepository.save(ProductVariant.create(
                product, "SKU-" + suffix, "color-red-" + suffix, ADDITIONAL_PRICE, STOCK
        ));
        variantOptionValueRepository.save(ProductVariantOptionValue.create(variant, value));
        product.startSale();
        entityManager.flush();

        OrderCreateResponse order = orderService.createDirectOrder(
                buyer.getId(),
                new DirectOrderCreateRequest(
                        key("order-" + suffix), product.getId(), variant.getId(), QUANTITY,
                        "recipient", "010-1234-5678", "12345", "address", null
                )
        );
        OrderItem item = orderItemRepository.findAllByOrderIdOrderByIdAsc(order.orderId()).getFirst();
        return new ItemFixture(item.getId());
    }

    private String key(String suffix) {
        return suffix + "-" + UUID.randomUUID();
    }

    private record ItemFixture(Long orderItemId) {}
}
