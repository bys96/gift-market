package com.giftmarket.seller.dto.response;

import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SellerResponse {

    private Long id;

    private String storeName;

    private String introduction;

    private SellerStatus status;

    public static SellerResponse from(Seller seller) {
        return SellerResponse.builder()
                .id(seller.getId())
                .storeName(seller.getStoreName())
                .introduction(seller.getIntroduction())
                .status(seller.getStatus())
                .build();
    }
}