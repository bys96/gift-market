package com.giftmarket.seller.dto.response;

import com.giftmarket.seller.entity.SellerStore;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class SellerStoreResponse {
    private Long id; private String storeName; private String introduction; private String logoImageKey; private String bannerImageKey;
    private String customerServicePhone; private String customerServiceEmail;
    private String customerServiceOpenTime;
    private String customerServiceCloseTime;
    private String customerServiceClosedDays;
    private String customerServiceNote;

    public static SellerStoreResponse from(SellerStore store) {
        return SellerStoreResponse.builder()
                .id(store.getId())
                .storeName(store.getStoreName())
                .introduction(store.getIntroduction())
                .logoImageKey(store.getLogoImageKey())
                .bannerImageKey(store.getBannerImageKey())
                .customerServicePhone(store.getCustomerServicePhone())
                .customerServiceEmail(store.getCustomerServiceEmail())
                .customerServiceOpenTime(store.getCustomerServiceOpenTime())
                .customerServiceCloseTime(store.getCustomerServiceCloseTime())
                .customerServiceClosedDays(store.getCustomerServiceClosedDays())
                .customerServiceNote(store.getCustomerServiceNote())
                .build();
    }
}
