package com.giftmarket.product.service;

import com.giftmarket.product.dto.request.GiftOccasionActiveUpdateRequest;
import com.giftmarket.product.dto.request.GiftOccasionCreateRequest;
import com.giftmarket.product.dto.request.GiftOccasionUpdateRequest;
import com.giftmarket.product.dto.response.AdminGiftOccasionResponse;
import com.giftmarket.product.dto.response.GiftOccasionResponse;
import com.giftmarket.product.entity.GiftOccasion;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductGiftOccasion;
import com.giftmarket.product.exception.ProductException;
import com.giftmarket.product.repository.GiftOccasionRepository;
import com.giftmarket.product.repository.ProductGiftOccasionRepository;
import com.giftmarket.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GiftOccasionService {

    public static final int MAX_PRODUCT_GIFT_OCCASION_COUNT = 3;

    private final GiftOccasionRepository giftOccasionRepository;
    private final ProductGiftOccasionRepository productGiftOccasionRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<GiftOccasionResponse> getActiveGiftOccasions() {
        return giftOccasionRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc()
                .stream()
                .map(GiftOccasionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminGiftOccasionResponse> getAllGiftOccasions() {
        return giftOccasionRepository.findAllByOrderByDisplayOrderAscIdAsc()
                .stream()
                .map(AdminGiftOccasionResponse::from)
                .toList();
    }

    @Transactional
    public AdminGiftOccasionResponse createGiftOccasion(GiftOccasionCreateRequest request) {
        String name = normalizeName(request.name());
        validateDuplicateName(name, null);
        GiftOccasion saved = giftOccasionRepository.save(
                GiftOccasion.create(name, request.displayOrder())
        );
        return AdminGiftOccasionResponse.from(saved);
    }

    @Transactional
    public AdminGiftOccasionResponse updateGiftOccasion(
            Long giftOccasionId,
            GiftOccasionUpdateRequest request
    ) {
        GiftOccasion giftOccasion = getGiftOccasion(giftOccasionId);
        String name = normalizeName(request.name());
        validateDuplicateName(name, giftOccasionId);
        giftOccasion.update(name, request.displayOrder());
        return AdminGiftOccasionResponse.from(giftOccasion);
    }

    @Transactional
    public AdminGiftOccasionResponse updateActive(
            Long giftOccasionId,
            GiftOccasionActiveUpdateRequest request
    ) {
        GiftOccasion giftOccasion = getGiftOccasion(giftOccasionId);
        giftOccasion.changeActive(request.active());
        return AdminGiftOccasionResponse.from(giftOccasion);
    }

    @Transactional
    public void synchronizeProductGiftOccasions(Long productId, List<Long> requestedIds) {
        List<Long> ids = requestedIds == null ? List.of() : List.copyOf(requestedIds);
        validateRequestedIds(ids);

        List<GiftOccasion> occasions = ids.isEmpty()
                ? List.of()
                : giftOccasionRepository.findAllByIdInAndActiveTrue(ids);
        if (occasions.size() != ids.size()) {
            throw new ProductException("존재하지 않거나 비활성화된 선물 상황은 선택할 수 없습니다.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductException("상품을 찾을 수 없습니다."));

        productGiftOccasionRepository.deleteAllByProductId(productId);
        productGiftOccasionRepository.flush();
        productGiftOccasionRepository.saveAll(
                occasions.stream()
                        .map(occasion -> ProductGiftOccasion.create(product, occasion))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<GiftOccasionResponse> getActiveProductGiftOccasions(Long productId) {
        return productGiftOccasionRepository.findActiveByProductId(productId)
                .stream()
                .map(ProductGiftOccasion::getGiftOccasion)
                .map(GiftOccasionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<GiftOccasionResponse>> getActiveProductGiftOccasions(
            List<Long> productIds
    ) {
        if (productIds.isEmpty()) {
            return Map.of();
        }

        return productGiftOccasionRepository.findActiveByProductIdIn(productIds)
                .stream()
                .collect(Collectors.groupingBy(
                        relation -> relation.getProduct().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(
                                relation -> GiftOccasionResponse.from(relation.getGiftOccasion()),
                                Collectors.toList()
                        )
                ));
    }

    private void validateRequestedIds(List<Long> ids) {
        if (ids.size() > MAX_PRODUCT_GIFT_OCCASION_COUNT) {
            throw new ProductException("선물 상황은 최대 3개까지 선택할 수 있습니다.");
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ProductException("선물 상황 ID를 확인해주세요.");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ProductException("선물 상황은 중복해서 선택할 수 없습니다.");
        }
    }

    private GiftOccasion getGiftOccasion(Long id) {
        return giftOccasionRepository.findById(id)
                .orElseThrow(() -> new ProductException("선물 상황을 찾을 수 없습니다."));
    }

    private void validateDuplicateName(String name, Long excludedId) {
        boolean duplicated = excludedId == null
                ? giftOccasionRepository.existsByNameIgnoreCase(name)
                : giftOccasionRepository.existsByNameIgnoreCaseAndIdNot(name, excludedId);
        if (duplicated) {
            throw new ProductException("이미 등록된 선물 상황 이름입니다.");
        }
    }

    private String normalizeName(String name) {
        return name.trim();
    }
}
