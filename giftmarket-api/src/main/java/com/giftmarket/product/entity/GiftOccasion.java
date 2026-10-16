package com.giftmarket.product.entity;

import com.giftmarket.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "gift_occasions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_gift_occasions_name",
                columnNames = "name"
        ),
        indexes = @Index(
                name = "idx_gift_occasions_active_display_order",
                columnList = "active, display_order"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GiftOccasion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    private GiftOccasion(String name, Integer displayOrder) {
        this.name = name;
        this.displayOrder = displayOrder;
        this.active = true;
    }

    public static GiftOccasion create(String name, Integer displayOrder) {
        return new GiftOccasion(name, displayOrder);
    }

    public void update(String name, Integer displayOrder) {
        this.name = name;
        this.displayOrder = displayOrder;
    }

    public void changeActive(boolean active) {
        this.active = active;
    }
}
