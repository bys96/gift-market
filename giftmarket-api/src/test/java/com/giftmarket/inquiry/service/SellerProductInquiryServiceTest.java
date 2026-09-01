package com.giftmarket.inquiry.service;

import com.giftmarket.inquiry.dto.ProductInquiryAnswerRequest;
import com.giftmarket.inquiry.entity.ProductInquiry;
import com.giftmarket.inquiry.entity.ProductInquiryAnswer;
import com.giftmarket.inquiry.entity.ProductInquiryStatus;
import com.giftmarket.inquiry.exception.ProductInquiryException;
import com.giftmarket.inquiry.repository.ProductInquiryRepository;
import com.giftmarket.inquiry.repository.ProductInquiryAnswerRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SellerProductInquiryServiceTest {
    @Mock ProductInquiryRepository inquiries; @Mock ProductInquiryAnswerRepository answers; @Mock SellerRepository sellers; @Mock Seller seller; @Mock ProductInquiry inquiry; @Mock ProductInquiryAnswer answer; @Mock Product product; @Mock User sellerUser; @Mock User writer;
    SellerProductInquiryService service;
    @BeforeEach void setup(){ service=new SellerProductInquiryService(inquiries,answers,sellers); given(sellers.findByUserId(1L)).willReturn(Optional.of(seller)); lenient().when(seller.getStatus()).thenReturn(SellerStatus.ACTIVE); given(seller.getId()).willReturn(9L); }
    @Test void listsOnlyOwnProductInquiries(){ given(inquiries.findAllByProductSellerIdAndDeletedAtIsNull(eq(9L),any())).willReturn(new PageImpl<>(List.of())); service.getInquiries(1L,null,0,20); verify(inquiries).findAllByProductSellerIdAndDeletedAtIsNull(eq(9L),any()); }
    @Test void salesSuspendedSellerCanAnswerExistingInquiry(){ given(seller.getStatus()).willReturn(SellerStatus.SALES_SUSPENDED); fullInquiry(); given(inquiries.findActiveByIdAndSellerIdForUpdate(5L,9L)).willReturn(Optional.of(inquiry)); given(answers.save(any())).willReturn(answer); service.answer(1L,5L,new ProductInquiryAnswerRequest("답변")); verify(answers).save(any(ProductInquiryAnswer.class)); }
    @Test void waitingFilter(){ given(inquiries.findAllByProductSellerIdAndStatusAndDeletedAtIsNull(eq(9L),eq(ProductInquiryStatus.WAITING),any())).willReturn(new PageImpl<>(List.of())); service.getInquiries(1L,ProductInquiryStatus.WAITING,0,20); verify(inquiries).findAllByProductSellerIdAndStatusAndDeletedAtIsNull(eq(9L),eq(ProductInquiryStatus.WAITING),any()); }
    @Test void answeredFilter(){ given(inquiries.findAllByProductSellerIdAndStatusAndDeletedAtIsNull(eq(9L),eq(ProductInquiryStatus.ANSWERED),any())).willReturn(new PageImpl<>(List.of())); service.getInquiries(1L,ProductInquiryStatus.ANSWERED,0,20); verify(inquiries).findAllByProductSellerIdAndStatusAndDeletedAtIsNull(eq(9L),eq(ProductInquiryStatus.ANSWERED),any()); }
    @Test void detailChecksSellerOwnership(){ given(inquiries.findByIdAndProductSellerIdAndDeletedAtIsNull(5L,9L)).willReturn(Optional.empty()); assertThatThrownBy(() -> service.getInquiry(1L,5L)).isInstanceOf(ProductInquiryException.class); }
    @Test void registersAnswerAndMarksInquiryAnswered(){ fullInquiry(); given(inquiries.findActiveByIdAndSellerIdForUpdate(5L,9L)).willReturn(Optional.of(inquiry)); given(answers.save(any())).willReturn(answer); service.answer(1L,5L,new ProductInquiryAnswerRequest(" 답변 ")); verify(answers).save(any(ProductInquiryAnswer.class)); verify(inquiry).markAnswered(); }
    @Test void modifiesExistingAnswerWithoutNewRow(){ fullInquiry(); given(inquiry.getStatus()).willReturn(ProductInquiryStatus.ANSWERED); given(inquiries.findActiveByIdAndSellerIdForUpdate(5L,9L)).willReturn(Optional.of(inquiry)); given(answers.findByInquiryId(5L)).willReturn(Optional.of(answer)); service.answer(1L,5L,new ProductInquiryAnswerRequest("수정 답변")); verify(answer).updateContent("수정 답변"); verify(answers,never()).save(any()); verify(inquiry,never()).markAnswered(); }
    @Test void inconsistentWaitingStatePreventsSecondAnswer(){ given(inquiry.getStatus()).willReturn(ProductInquiryStatus.WAITING); given(inquiries.findActiveByIdAndSellerIdForUpdate(5L,9L)).willReturn(Optional.of(inquiry)); given(answers.findByInquiryId(5L)).willReturn(Optional.of(answer)); assertThatThrownBy(() -> service.answer(1L,5L,new ProductInquiryAnswerRequest("답변"))).isInstanceOf(ProductInquiryException.class); verify(answers,never()).save(any()); }
    @Test void otherSellerOrDeletedInquiryCannotAnswer(){ given(inquiries.findActiveByIdAndSellerIdForUpdate(5L,9L)).willReturn(Optional.empty()); assertThatThrownBy(() -> service.answer(1L,5L,new ProductInquiryAnswerRequest("답변"))).isInstanceOf(ProductInquiryException.class); verifyNoInteractions(answers); }
    private void fullInquiry(){ given(inquiry.getProduct()).willReturn(product); given(product.getSeller()).willReturn(seller); given(seller.getUser()).willReturn(sellerUser); given(sellerUser.getId()).willReturn(1L); given(inquiry.getUser()).willReturn(writer); given(writer.getName()).willReturn("구매자"); given(inquiry.getStatus()).willReturn(ProductInquiryStatus.WAITING); }
}
