package com.giftmarket.order.entity;

import com.giftmarket.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "return_request_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_return_request_items_request_item",
                        columnNames = {"return_request_id", "order_item_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_return_request_items_order_item",
                        columnList = "order_item_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnRequestItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "return_request_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_return_request_items_request")
    )
    private ReturnRequest returnRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_item_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_return_request_items_order_item")
    )
    private OrderItem orderItem;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "inspection_result",
            length = 30
    )
    private ReturnInspectionResult inspectionResult;

    @Column(
            name = "restocked_quantity",
            nullable = false,
            columnDefinition = "int default 0"
    )
    private int restockedQuantity;

    private ReturnRequestItem(
            ReturnRequest returnRequest,
            OrderItem orderItem,
            int quantity
    ) {
        if (returnRequest == null || orderItem == null) {
            throw new IllegalArgumentException(
                    "반품 대상 주문 상품이 필요합니다."
            );
        }

        if (returnRequest.getSellerOrder() != orderItem.getSellerOrder()) {
            throw new IllegalArgumentException(
                    "반품 요청과 주문 상품의 판매자 주문이 일치하지 않습니다."
            );
        }

        int returnableQuantity =
                orderItem.getQuantity()
                        - orderItem.getCanceledQuantity()
                        - orderItem.getReturnedQuantity();

        if (quantity <= 0 || quantity > returnableQuantity) {
            throw new IllegalArgumentException(
                    "반품 수량이 주문 상품의 반품 가능 수량을 벗어났습니다."
            );
        }

        this.returnRequest = returnRequest;
        this.orderItem = orderItem;
        this.quantity = quantity;
        this.inspectionResult = null;
        this.restockedQuantity = 0;
    }

    public static ReturnRequestItem create(
            ReturnRequest returnRequest,
            OrderItem orderItem,
            int quantity
    ) {
        return new ReturnRequestItem(
                returnRequest,
                orderItem,
                quantity
        );
    }

    public void inspect(
            ReturnInspectionResult inspectionResult
    ) {
        if (inspectionResult == null) {
            throw new IllegalArgumentException(
                    "반품 검수 결과가 필요합니다."
            );
        }

        if (this.inspectionResult != null) {
            throw new IllegalStateException(
                    "이미 반품 검수가 완료된 상품입니다."
            );
        }

        this.inspectionResult = inspectionResult;
    }

    public void increaseRestockedQuantity(
            int restockQuantity
    ) {
        if (inspectionResult != ReturnInspectionResult.RESTOCKABLE) {
            throw new IllegalStateException(
                    "재판매 가능한 반품 상품만 재고에 복원할 수 있습니다."
            );
        }

        if (restockQuantity <= 0) {
            throw new IllegalArgumentException(
                    "재고 복원 수량은 1개 이상이어야 합니다."
            );
        }

        int updatedQuantity;

        try {
            updatedQuantity = Math.addExact(
                    restockedQuantity,
                    restockQuantity
            );
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "반품 재고 복원 수량이 허용 범위를 초과했습니다.",
                    exception
            );
        }

        if (updatedQuantity > quantity) {
            throw new IllegalArgumentException(
                    "재고 복원 수량이 반품 수량을 초과합니다."
            );
        }

        restockedQuantity = updatedQuantity;
    }
}