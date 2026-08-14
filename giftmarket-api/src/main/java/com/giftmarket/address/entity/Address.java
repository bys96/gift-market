package com.giftmarket.address.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "addresses",
        indexes = {
                @Index(
                        name = "idx_addresses_user_id",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_addresses_user_default",
                        columnList = "user_id, is_default"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_addresses_user"
            )
    )
    private User user;

    @Column(
            nullable = false,
            length = 20
    )
    private String name;

    @Column(
            name = "recipient_name",
            nullable = false,
            length = 30
    )
    private String recipientName;

    @Column(
            name = "phone_number",
            nullable = false,
            length = 20
    )
    private String phoneNumber;

    @Column(
            name = "postal_code",
            nullable = false,
            length = 10
    )
    private String postalCode;

    @Column(
            nullable = false,
            length = 500
    )
    private String address;

    @Column(
            name = "detail_address",
            length = 500
    )
    private String detailAddress;

    @Column(
            name = "is_default",
            nullable = false
    )
    private boolean defaultAddress;

    private Address(
            User user,
            String name,
            String recipientName,
            String phoneNumber,
            String postalCode,
            String address,
            String detailAddress,
            boolean defaultAddress
    ) {
        this.user = user;
        this.name = name;
        this.recipientName = recipientName;
        this.phoneNumber = phoneNumber;
        this.postalCode = postalCode;
        this.address = address;
        this.detailAddress = detailAddress;
        this.defaultAddress = defaultAddress;
    }

    public static Address create(
            User user,
            String name,
            String recipientName,
            String phoneNumber,
            String postalCode,
            String address,
            String detailAddress,
            boolean defaultAddress
    ) {
        return new Address(
                user,
                name,
                recipientName,
                phoneNumber,
                postalCode,
                address,
                detailAddress,
                defaultAddress
        );
    }

    public void update(
            String name,
            String recipientName,
            String phoneNumber,
            String postalCode,
            String address,
            String detailAddress
    ) {
        this.name = name;
        this.recipientName = recipientName;
        this.phoneNumber = phoneNumber;
        this.postalCode = postalCode;
        this.address = address;
        this.detailAddress = detailAddress;
    }

    public void setAsDefault() {
        this.defaultAddress = true;
    }

    public void unsetDefault() {
        this.defaultAddress = false;
    }
}