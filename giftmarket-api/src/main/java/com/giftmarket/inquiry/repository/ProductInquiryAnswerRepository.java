package com.giftmarket.inquiry.repository;

import com.giftmarket.inquiry.entity.ProductInquiryAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductInquiryAnswerRepository extends JpaRepository<ProductInquiryAnswer, Long> {
    Optional<ProductInquiryAnswer> findByInquiryId(Long inquiryId);
    boolean existsByInquiryId(Long inquiryId);

    @EntityGraph(attributePaths = {"inquiry", "seller"})
    List<ProductInquiryAnswer> findAllByInquiryIdIn(Collection<Long> inquiryIds);
}
