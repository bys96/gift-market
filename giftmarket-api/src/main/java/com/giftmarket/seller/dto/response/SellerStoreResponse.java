package com.giftmarket.seller.dto.response;

import com.giftmarket.seller.entity.SellerStore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SellerStoreResponse {
    private Long id; private String storeName; private String introduction; private String logoImageKey; private String bannerImageKey;
    private String logoImageUrl; private String bannerImageUrl;
    private String customerServicePhone; private String customerServiceEmail;
    private String customerServiceOpenTime;
    private String customerServiceCloseTime;
    private String customerServiceClosedDays;
    private String customerServiceNote;

    public static SellerStoreResponse from(SellerStore store) {
        return from(store, null);
    }

    public static SellerStoreResponse from(
            SellerStore store,
            java.util.function.Function<String, String> readUrlResolver
    ) {
        return SellerStoreResponse.builder()
                .id(store.getId())
                .storeName(store.getStoreName())
                .introduction(store.getIntroduction())
                .logoImageKey(store.getLogoImageKey())
                .bannerImageKey(store.getBannerImageKey())
                .logoImageUrl(resolveReadUrl(store.getLogoImageKey(), readUrlResolver))
                .bannerImageUrl(resolveReadUrl(store.getBannerImageKey(), readUrlResolver))
                .customerServicePhone(store.getCustomerServicePhone())
                .customerServiceEmail(store.getCustomerServiceEmail())
                .customerServiceOpenTime(store.getCustomerServiceOpenTime())
                .customerServiceCloseTime(store.getCustomerServiceCloseTime())
                .customerServiceClosedDays(store.getCustomerServiceClosedDays())
                .customerServiceNote(store.getCustomerServiceNote())
                .build();
    }

    private static String resolveReadUrl(
            String imageKey,
            java.util.function.Function<String, String> readUrlResolver
    ) {
        return imageKey == null || imageKey.isBlank() || readUrlResolver == null
                ? null
                : readUrlResolver.apply(imageKey);
    }
}
