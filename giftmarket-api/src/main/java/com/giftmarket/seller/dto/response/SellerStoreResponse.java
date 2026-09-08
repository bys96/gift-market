package com.giftmarket.seller.dto.response;

import com.giftmarket.seller.entity.SellerStore;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class SellerStoreResponse {
    private Long id; private String storeName; private String introduction; private String logoImageKey; private String bannerImageKey;
    private String customerServicePhone; private String customerServiceEmail; private String customerServiceHours;
    private String shippingGuide; private String returnExchangeGuide;
    public static SellerStoreResponse from(SellerStore s) { return SellerStoreResponse.builder().id(s.getId()).storeName(s.getStoreName()).introduction(s.getIntroduction()).logoImageKey(s.getLogoImageKey()).bannerImageKey(s.getBannerImageKey()).customerServicePhone(s.getCustomerServicePhone()).customerServiceEmail(s.getCustomerServiceEmail()).customerServiceHours(s.getCustomerServiceHours()).shippingGuide(s.getShippingGuide()).returnExchangeGuide(s.getReturnExchangeGuide()).build(); }
}
