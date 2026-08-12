package com.giftmarket.product.draft.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.product.draft.dto.request.ProductDraftCreateRequest;
import com.giftmarket.product.draft.dto.request.ProductDraftUpdateRequest;
import com.giftmarket.product.draft.dto.response.ProductDraftResponse;
import com.giftmarket.product.draft.entity.ProductDraft;
import com.giftmarket.product.draft.repository.ProductDraftRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.exception.ProductException;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductDraftService {

    private final ProductDraftRepository productDraftRepository;
    private final ProductRepository productRepository;
    private final SellerRepository sellerRepository;

    @Transactional
    public ProductDraftResponse createDraft(
            Long userId,
            ProductDraftCreateRequest request
    ) {
        Seller seller = getActiveSeller(userId);

        Product product = null;

        if (request.productId() != null) {
            product = getSellerProduct(
                    request.productId(),
                    seller.getId()
            );

            productDraftRepository
                    .findBySellerIdAndProductId(
                            seller.getId(),
                            product.getId()
                    )
                    .ifPresent(existingDraft -> {
                        throw new ProductException(
                                "이미 임시저장된 수정 내용이 있습니다."
                        );
                    });
        }

        ProductDraft draft = ProductDraft.create(
                seller,
                product,
                request.draftData()
        );

        ProductDraft savedDraft =
                productDraftRepository.save(draft);

        return ProductDraftResponse.from(savedDraft);
    }

    @Transactional(readOnly = true)
    public ProductDraftResponse getDraft(
            Long userId,
            Long draftId
    ) {
        Seller seller = getActiveSeller(userId);

        ProductDraft draft = getSellerDraft(
                draftId,
                seller.getId()
        );

        return ProductDraftResponse.from(draft);
    }

    @Transactional(readOnly = true)
    public ProductDraftResponse getProductDraft(
            Long userId,
            Long productId
    ) {
        Seller seller = getActiveSeller(userId);

        getSellerProduct(
                productId,
                seller.getId()
        );

        ProductDraft draft =
                productDraftRepository
                        .findBySellerIdAndProductId(
                                seller.getId(),
                                productId
                        )
                        .orElseThrow(() ->
                                new ProductException(
                                        "임시저장된 수정 내용을 찾을 수 없습니다."
                                )
                        );

        return ProductDraftResponse.from(draft);
    }

    @Transactional(readOnly = true)
    public List<ProductDraftResponse> getMyDrafts(
            Long userId
    ) {
        Seller seller = getActiveSeller(userId);

        return productDraftRepository
                .findAllBySellerIdOrderByUpdatedAtDesc(
                        seller.getId()
                )
                .stream()
                .map(ProductDraftResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductDraftResponse> getMyNewProductDrafts(
            Long userId
    ) {
        Seller seller = getActiveSeller(userId);

        return productDraftRepository
                .findAllBySellerIdAndProductIsNullOrderByUpdatedAtDesc(
                        seller.getId()
                )
                .stream()
                .map(ProductDraftResponse::from)
                .toList();
    }

    @Transactional
    public ProductDraftResponse updateDraft(
            Long userId,
            Long draftId,
            ProductDraftUpdateRequest request
    ) {
        Seller seller = getActiveSeller(userId);

        ProductDraft draft = getSellerDraft(
                draftId,
                seller.getId()
        );

        draft.updateDraftData(
                request.draftData()
        );

        return ProductDraftResponse.from(draft);
    }

    @Transactional
    public void deleteDraft(
            Long userId,
            Long draftId
    ) {
        Seller seller = getActiveSeller(userId);

        ProductDraft draft = getSellerDraft(
                draftId,
                seller.getId()
        );

        productDraftRepository.delete(draft);
    }

    private ProductDraft getSellerDraft(
            Long draftId,
            Long sellerId
    ) {
        return productDraftRepository
                .findByIdAndSellerId(
                        draftId,
                        sellerId
                )
                .orElseThrow(() ->
                        new ProductException(
                                "임시저장 상품을 찾을 수 없습니다."
                        )
                );
    }

    private Product getSellerProduct(
            Long productId,
            Long sellerId
    ) {
        return productRepository
                .findByIdAndSellerIdAndDeletedAtIsNull(
                        productId,
                        sellerId
                )
                .orElseThrow(() ->
                        new ProductException(
                                "상품을 찾을 수 없습니다."
                        )
                );
    }

    private Seller getActiveSeller(
            Long userId
    ) {
        if (userId == null) {
            throw new AuthenticationException(
                    "인증이 필요합니다."
            );
        }

        Seller seller =
                sellerRepository
                        .findByUserId(userId)
                        .orElseThrow(() ->
                                new ProductException(
                                        "판매자 정보를 찾을 수 없습니다."
                                )
                        );

        if (seller.getStatus()
                != SellerStatus.ACTIVE) {
            throw new ProductException(
                    "활성 상태의 판매자만 상품을 관리할 수 있습니다."
            );
        }

        return seller;
    }
}