package com.giftmarket.order.entity;

import com.giftmarket.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "return_request_images",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_return_request_images_request_object",
                columnNames = {"return_request_id", "object_key"}
        ),
        indexes = @Index(
                name = "idx_return_request_images_request_sort",
                columnList = "return_request_id, sort_order"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnRequestImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_request_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_return_request_images_request"))
    private ReturnRequest returnRequest;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private ReturnRequestImage(ReturnRequest returnRequest, String objectKey, int sortOrder) {
        if (returnRequest == null) throw new IllegalArgumentException("반품 요청이 필요합니다.");
        if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("반품 이미지 키가 필요합니다.");
        if (sortOrder < 0 || sortOrder > 4) throw new IllegalArgumentException("반품 이미지 순서를 확인해주세요.");
        this.returnRequest = returnRequest;
        this.objectKey = objectKey.trim();
        this.sortOrder = sortOrder;
    }

    public static ReturnRequestImage create(ReturnRequest returnRequest, String objectKey, int sortOrder) {
        return new ReturnRequestImage(returnRequest, objectKey, sortOrder);
    }
}
