package com.giftmarket.inquiry.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.inquiry.dto.*;
import com.giftmarket.inquiry.entity.ProductInquiry;
import com.giftmarket.inquiry.exception.ProductInquiryException;
import com.giftmarket.inquiry.repository.ProductInquiryRepository;
import com.giftmarket.inquiry.repository.ProductInquiryAnswerRepository;
import com.giftmarket.inquiry.entity.ProductInquiryAnswer;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductInquiryService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<ProductStatus> VISIBLE_STATUSES = List.of(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT);
    private final ProductInquiryRepository inquiryRepository;
    private final ProductInquiryAnswerRepository answerRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ProductInquiryPageResponse getInquiries(Long viewerId, Long productId, int page, int size) {
        requireVisibleProduct(productId);
        var inquiries = inquiryRepository.findAllByProductIdAndDeletedAtIsNull(productId, pageable(page, size));
        Map<Long, ProductInquiryAnswer> answers = answers(inquiries.getContent());
        var result = inquiries.map(inquiry -> ProductInquiryResponse.from(inquiry, answers.get(inquiry.getId()), viewerId));
        return ProductInquiryPageResponse.from(result);
    }

    @Transactional
    public ProductInquiryResponse create(Long userId, Long productId, ProductInquiryRequest request) {
        requireAuthentication(userId);
        Product product = requireVisibleProduct(productId);
        User user = userRepository.findById(userId).orElseThrow(() -> new AuthenticationException("사용자 정보를 찾을 수 없습니다."));
        ProductInquiry saved = inquiryRepository.save(ProductInquiry.create(product, user, normalize(request.title()), normalize(request.content()), request.isPrivate()));
        return ProductInquiryResponse.from(saved, null, userId);
    }

    @Transactional
    public ProductInquiryResponse update(Long userId, Long productId, Long inquiryId, ProductInquiryRequest request) {
        requireAuthentication(userId);
        ProductInquiry inquiry = getInquiry(productId, inquiryId);
        requireOwner(inquiry, userId);
        try {
            inquiry.updateQuestion(normalize(request.title()), normalize(request.content()), request.isPrivate());
        } catch (IllegalStateException exception) {
            throw new ProductInquiryException(exception.getMessage());
        }
        return ProductInquiryResponse.from(inquiry, answerRepository.findByInquiryId(inquiryId).orElse(null), userId);
    }

    @Transactional
    public void delete(Long userId, Long productId, Long inquiryId) {
        requireAuthentication(userId);
        ProductInquiry inquiry = inquiryRepository.findByIdAndProductId(inquiryId, productId)
                .orElseThrow(() -> new ProductInquiryException("상품 문의를 찾을 수 없습니다."));
        requireOwner(inquiry, userId);
        inquiry.softDelete();
    }

    private Product requireVisibleProduct(Long productId) {
        return productRepository.findByIdAndStatusInAndAdminHiddenFalseAndDeletedAtIsNull(productId, VISIBLE_STATUSES)
                .orElseThrow(() -> new ProductInquiryException("상품을 찾을 수 없습니다."));
    }

    private ProductInquiry getInquiry(Long productId, Long inquiryId) {
        return inquiryRepository.findByIdAndProductIdAndDeletedAtIsNull(inquiryId, productId)
                .orElseThrow(() -> new ProductInquiryException("상품 문의를 찾을 수 없습니다."));
    }

    private void requireOwner(ProductInquiry inquiry, Long userId) {
        if (!inquiry.getUser().getId().equals(userId)) throw new ProductInquiryException("상품 문의를 수정하거나 삭제할 권한이 없습니다.");
    }

    private void requireAuthentication(Long userId) {
        if (userId == null) throw new AuthenticationException("인증이 필요합니다.");
    }

    private PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw new ProductInquiryException("페이지 정보를 확인해주세요.");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private String normalize(String value) { return value.trim(); }

    private Map<Long, ProductInquiryAnswer> answers(List<ProductInquiry> inquiries) {
        if (inquiries.isEmpty()) return Map.of();
        return answerRepository.findAllByInquiryIdIn(inquiries.stream().map(ProductInquiry::getId).toList())
                .stream().collect(Collectors.toMap(a -> a.getInquiry().getId(), Function.identity()));
    }
}
