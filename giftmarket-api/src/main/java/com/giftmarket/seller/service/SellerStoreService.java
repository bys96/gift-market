package com.giftmarket.seller.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.seller.dto.request.SellerStoreUpdateRequest;
import com.giftmarket.seller.dto.response.SellerStoreResponse;
import com.giftmarket.seller.entity.*;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.*;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class SellerStoreService {
    private final SellerRepository sellerRepository;
    private final SellerStoreRepository storeRepository;
    private final UserRepository userRepository;

    @Transactional
    public SellerStoreResponse get(Long userId) {
        Seller seller = seller(userId);
        Seller lockedSeller = sellerRepository.findByIdForUpdate(seller.getId()).orElseThrow();
        SellerStore store = storeRepository.findBySellerIdForUpdate(lockedSeller.getId())
                .orElseGet(() -> storeRepository.save(SellerStore.create(lockedSeller, lockedSeller.getStoreName(), lockedSeller.getIntroduction())));
        return SellerStoreResponse.from(store);
    }

    @Transactional
    public SellerStoreResponse update(Long userId, SellerStoreUpdateRequest request) {
        Seller seller = seller(userId);
        String name = request.storeName().trim();
        SellerStore store = storeRepository.findBySellerId(seller.getId()).orElse(null);
        if ((store == null && storeRepository.existsByStoreName(name)) || (store != null && storeRepository.existsByStoreNameAndIdNot(name, store.getId()))) {
            throw new SellerException("이미 사용 중인 스토어명입니다.");
        }
        if (!validKey(request.logoImageKey(), seller.getId(), "logo") || !validKey(request.bannerImageKey(), seller.getId(), "banner")) {
            throw new SellerException("스토어 이미지 키가 올바르지 않습니다.");
        }
        if (store == null) store = storeRepository.save(SellerStore.create(seller, name, trim(request.introduction())));
        store.update(name, trim(request.introduction()), trim(request.logoImageKey()), trim(request.bannerImageKey()), trim(request.customerServicePhone()), trim(request.customerServiceEmail()), trim(request.customerServiceHours()));
        return SellerStoreResponse.from(store);
    }
    private Seller seller(Long userId) {
        if (userId == null) throw new AuthenticationException("인증이 필요합니다.");
        Seller seller = sellerRepository.findByUserId(userId).orElseThrow(() -> new SellerException("판매자 정보를 찾을 수 없습니다."));
        if (seller.getStatus() != SellerStatus.ACTIVE && seller.getStatus() != SellerStatus.SALES_SUSPENDED) throw new SellerException("현재 판매자 상태에서는 스토어 설정을 사용할 수 없습니다.");
        return seller;
    }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private boolean validKey(String key, Long sellerId, String folder) { return key == null || key.isBlank() || key.matches("stores/" + sellerId + "/" + folder + "/[a-f0-9-]+\\.(jpg|jpeg|png|webp)"); }
}
