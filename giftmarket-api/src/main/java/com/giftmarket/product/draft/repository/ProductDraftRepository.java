package com.giftmarket.product.draft.repository;

import com.giftmarket.product.draft.entity.ProductDraft;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductDraftRepository
        extends JpaRepository<ProductDraft, Long> {

    @EntityGraph(attributePaths = {
            "seller",
            "product"
    })
    Optional<ProductDraft> findByIdAndSellerId(
            Long draftId,
            Long sellerId
    );

    @EntityGraph(attributePaths = {
            "seller",
            "product"
    })
    Optional<ProductDraft> findBySellerIdAndProductId(
            Long sellerId,
            Long productId
    );

    @EntityGraph(attributePaths = {
            "product"
    })
    List<ProductDraft> findAllBySellerIdOrderByUpdatedAtDesc(
            Long sellerId
    );

    @EntityGraph(attributePaths = {
            "product"
    })
    List<ProductDraft> findAllBySellerIdAndProductIsNullOrderByUpdatedAtDesc(
            Long sellerId
    );

    boolean existsBySellerIdAndProductId(
            Long sellerId,
            Long productId
    );
}