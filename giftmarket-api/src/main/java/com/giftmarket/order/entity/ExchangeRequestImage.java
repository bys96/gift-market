package com.giftmarket.order.entity;

import com.giftmarket.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "exchange_request_images",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exchange_request_images_request_object",
                columnNames = {"exchange_request_id", "object_key"}
        ),
        indexes = @Index(
                name = "idx_exchange_request_images_request_sort",
                columnList = "exchange_request_id, sort_order"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRequestImage extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exchange_request_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exchange_request_images_request"))
    private ExchangeRequest exchangeRequest;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private ExchangeRequestImage(ExchangeRequest exchangeRequest, String objectKey, int sortOrder) {
        if (exchangeRequest == null) throw new IllegalArgumentException("교환 요청이 필요합니다.");
        if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("교환 이미지 키가 필요합니다.");
        if (sortOrder < 0 || sortOrder > 4) throw new IllegalArgumentException("교환 이미지 순서를 확인해주세요.");
        this.exchangeRequest = exchangeRequest;
        this.objectKey = objectKey.trim();
        this.sortOrder = sortOrder;
    }

    public static ExchangeRequestImage create(ExchangeRequest exchangeRequest, String objectKey, int sortOrder) {
        return new ExchangeRequestImage(exchangeRequest, objectKey, sortOrder);
    }
}
