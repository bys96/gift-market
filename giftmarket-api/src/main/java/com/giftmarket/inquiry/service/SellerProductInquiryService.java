package com.giftmarket.inquiry.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.inquiry.dto.*;
import com.giftmarket.inquiry.entity.ProductInquiry;
import com.giftmarket.inquiry.entity.ProductInquiryStatus;
import com.giftmarket.inquiry.exception.ProductInquiryException;
import com.giftmarket.inquiry.repository.ProductInquiryRepository;
import com.giftmarket.inquiry.repository.ProductInquiryAnswerRepository;
import com.giftmarket.inquiry.entity.ProductInquiryAnswer;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerProductInquiryService {
    private final ProductInquiryRepository inquiryRepository;
    private final ProductInquiryAnswerRepository answerRepository;
    private final SellerRepository sellerRepository;

    public ProductInquiryPageResponse getInquiries(Long userId, ProductInquiryStatus status, int page, int size) {
        Seller seller = activeSeller(userId);
        if (page < 0 || size < 1 || size > 100) throw new ProductInquiryException("페이지 정보를 확인해주세요.");
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProductInquiry> inquiries = status == null
                ? inquiryRepository.findAllByProductSellerIdAndDeletedAtIsNull(seller.getId(), pageable)
                : inquiryRepository.findAllByProductSellerIdAndStatusAndDeletedAtIsNull(seller.getId(), status, pageable);
        Map<Long, ProductInquiryAnswer> answers = answers(inquiries.getContent());
        return ProductInquiryPageResponse.from(inquiries.map(inquiry -> ProductInquiryResponse.from(inquiry, answers.get(inquiry.getId()), userId)));
    }

    public ProductInquiryResponse getInquiry(Long userId, Long inquiryId) {
        Seller seller = activeSeller(userId);
        ProductInquiry inquiry = findMine(inquiryId, seller.getId());
        return ProductInquiryResponse.from(inquiry, answerRepository.findByInquiryId(inquiryId).orElse(null), userId);
    }

    @Transactional
    public ProductInquiryResponse answer(Long userId, Long inquiryId, ProductInquiryAnswerRequest request) {
        Seller seller = activeSeller(userId);
        ProductInquiry inquiry = inquiryRepository.findActiveByIdAndSellerIdForUpdate(inquiryId, seller.getId())
                .orElseThrow(() -> new ProductInquiryException("상품 문의를 찾을 수 없습니다."));
        ProductInquiryAnswer answer = answerRepository.findByInquiryId(inquiryId).orElse(null);
        if (inquiry.getStatus() == ProductInquiryStatus.WAITING) {
            if (answer != null) throw new ProductInquiryException("문의 답변 상태가 올바르지 않습니다.");
            answer = answerRepository.save(ProductInquiryAnswer.create(inquiry, seller, request.content().trim()));
            inquiry.markAnswered();
        } else {
            if (answer == null) throw new ProductInquiryException("문의 답변 정보를 찾을 수 없습니다.");
            answer.updateContent(request.content().trim());
        }
        return ProductInquiryResponse.from(inquiry, answer, userId);
    }

    private ProductInquiry findMine(Long inquiryId, Long sellerId) {
        return inquiryRepository.findByIdAndProductSellerIdAndDeletedAtIsNull(inquiryId, sellerId)
                .orElseThrow(() -> new ProductInquiryException("상품 문의를 찾을 수 없습니다."));
    }

    private Seller activeSeller(Long userId) {
        if (userId == null) throw new AuthenticationException("인증이 필요합니다.");
        Seller seller = sellerRepository.findByUserId(userId).orElseThrow(() -> new ProductInquiryException("판매자 정보를 찾을 수 없습니다."));
        if (seller.getStatus() != SellerStatus.ACTIVE
                && seller.getStatus() != SellerStatus.SALES_SUSPENDED) {
            throw new ProductInquiryException("상품 문의를 관리할 수 없는 판매자 상태입니다.");
        }
        return seller;
    }

    private Map<Long, ProductInquiryAnswer> answers(java.util.List<ProductInquiry> inquiries) {
        if (inquiries.isEmpty()) return Map.of();
        return answerRepository.findAllByInquiryIdIn(inquiries.stream().map(ProductInquiry::getId).toList())
                .stream().collect(Collectors.toMap(a -> a.getInquiry().getId(), Function.identity()));
    }
}
