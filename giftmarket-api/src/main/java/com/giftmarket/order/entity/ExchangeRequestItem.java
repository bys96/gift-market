package com.giftmarket.order.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "exchange_request_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exchange_request_items_request_item",
                columnNames = {"exchange_request_id", "order_item_id"}
        ),
        indexes = @Index(name = "idx_exchange_request_items_order_item", columnList = "order_item_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRequestItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exchange_request_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exchange_request_items_request"))
    private ExchangeRequest exchangeRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exchange_request_items_order_item"))
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exchange_request_items_target_product"))
    private Product targetProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_variant_id",
            foreignKey = @ForeignKey(name = "fk_exchange_request_items_target_variant"))
    private ProductVariant targetVariant;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "target_product_name", nullable = false, length = 200)
    private String targetProductName;

    @Column(name = "target_option_snapshot", length = 1000)
    private String targetOptionSnapshot;

    @Column(name = "target_unit_price", nullable = false)
    private long targetUnitPrice;

    @Column(name = "reserved_quantity", nullable = false, columnDefinition = "int default 0")
    private int reservedQuantity;

    @Column(name = "released_quantity", nullable = false, columnDefinition = "int default 0")
    private int releasedQuantity;

    @Column(name = "consumed_quantity", nullable = false, columnDefinition = "int default 0")
    private int consumedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_result", length = 30)
    private ExchangeInspectionResult inspectionResult;

    @Column(name = "restocked_quantity", nullable = false, columnDefinition = "int default 0")
    private int restockedQuantity;

    private ExchangeRequestItem(
            ExchangeRequest exchangeRequest, OrderItem orderItem, int quantity,
            Product targetProduct, ProductVariant targetVariant,
            String targetProductName, String targetOptionSnapshot, long targetUnitPrice
    ) {
        if (exchangeRequest == null || orderItem == null || targetProduct == null) {
            throw new IllegalArgumentException("교환 대상 주문 상품과 target 상품이 필요합니다.");
        }
        if (exchangeRequest.getSellerOrder() != orderItem.getSellerOrder()) {
            throw new IllegalArgumentException("교환 요청과 주문 상품의 판매자 주문이 일치하지 않습니다.");
        }
        if (orderItem.getProduct() != targetProduct) {
            throw new IllegalArgumentException("원 주문 상품과 같은 상품으로만 교환할 수 있습니다.");
        }
        if (targetVariant != null && targetVariant.getProduct() != targetProduct) {
            throw new IllegalArgumentException("교환 Variant가 target 상품에 속하지 않습니다.");
        }
        if ((orderItem.getVariant() == null) != (targetVariant == null)) {
            throw new IllegalArgumentException("옵션 상품의 교환 target Variant를 확인해주세요.");
        }
        if (quantity <= 0 || quantity > orderItem.getExchangeableQuantity()) {
            throw new IllegalArgumentException("교환 수량이 교환 가능 수량을 벗어났습니다.");
        }
        if (targetUnitPrice <= 0L) throw new IllegalArgumentException("교환 target 판매단가가 필요합니다.");
        this.exchangeRequest = exchangeRequest;
        this.orderItem = orderItem;
        this.quantity = quantity;
        this.targetProduct = targetProduct;
        this.targetVariant = targetVariant;
        this.targetProductName = requireText(targetProductName, "교환 target 상품명 snapshot이 필요합니다.");
        this.targetOptionSnapshot = nullableText(targetOptionSnapshot);
        this.targetUnitPrice = targetUnitPrice;
        this.reservedQuantity = 0;
        this.releasedQuantity = 0;
        this.consumedQuantity = 0;
        this.restockedQuantity = 0;
    }

    public static ExchangeRequestItem create(
            ExchangeRequest exchangeRequest, OrderItem orderItem, int quantity,
            Product targetProduct, ProductVariant targetVariant,
            String targetProductName, String targetOptionSnapshot, long targetUnitPrice
    ) {
        return new ExchangeRequestItem(exchangeRequest, orderItem, quantity, targetProduct,
                targetVariant, targetProductName, targetOptionSnapshot, targetUnitPrice);
    }

    public int getEffectiveReservedQuantity() {
        validateReservationState();
        return reservedQuantity - releasedQuantity - consumedQuantity;
    }

    public void reserveTargetStock(int reserveQuantity) {
        validateReservationState();
        if (reserveQuantity != quantity || reservedQuantity != 0) {
            throw new IllegalArgumentException("교환 target 재고는 요청 수량만큼 한 번만 예약할 수 있습니다.");
        }
        reservedQuantity = reserveQuantity;
    }

    public void releaseTargetStockReservation(int releaseQuantity) {
        int effectiveReservedQuantity = getEffectiveReservedQuantity();
        if (releaseQuantity <= 0 || releaseQuantity > effectiveReservedQuantity) {
            throw new IllegalArgumentException("유효한 교환 target 예약 수량 이내만 해제할 수 있습니다.");
        }
        releasedQuantity = Math.addExact(releasedQuantity, releaseQuantity);
    }

    public void consumeTargetStockReservation(int consumeQuantity) {
        int effectiveReservedQuantity = getEffectiveReservedQuantity();
        if (consumeQuantity <= 0 || consumeQuantity > effectiveReservedQuantity) {
            throw new IllegalArgumentException("유효한 교환 target 예약 수량 이내만 출고 소비할 수 있습니다.");
        }
        consumedQuantity = Math.addExact(consumedQuantity, consumeQuantity);
    }

    public void inspect(ExchangeInspectionResult inspectionResult) {
        if (inspectionResult == null) throw new IllegalArgumentException("교환 검수 결과가 필요합니다.");
        if (this.inspectionResult != null) throw new IllegalStateException("이미 교환 검수가 완료된 상품입니다.");
        this.inspectionResult = inspectionResult;
    }

    public void increaseRestockedQuantity(int restockQuantity) {
        if (inspectionResult != ExchangeInspectionResult.RESTOCKABLE) {
            throw new IllegalStateException("재판매 가능한 교환 회수 상품만 재고에 복원할 수 있습니다.");
        }
        if (restockQuantity <= 0) throw new IllegalArgumentException("재고 복원 수량은 1개 이상이어야 합니다.");
        int updated = Math.addExact(restockedQuantity, restockQuantity);
        if (updated > quantity) throw new IllegalArgumentException("재고 복원 수량이 교환 수량을 초과합니다.");
        restockedQuantity = updated;
    }

    private void validateReservationState() {
        if (reservedQuantity < 0 || releasedQuantity < 0 || consumedQuantity < 0
                || (long) releasedQuantity + consumedQuantity > reservedQuantity
                || reservedQuantity > quantity) {
            throw new IllegalStateException("Invalid exchange reservation state.");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static String nullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
