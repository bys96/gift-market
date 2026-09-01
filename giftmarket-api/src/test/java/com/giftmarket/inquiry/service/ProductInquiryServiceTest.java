package com.giftmarket.inquiry.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.inquiry.dto.ProductInquiryRequest;
import com.giftmarket.inquiry.entity.ProductInquiry;
import com.giftmarket.inquiry.entity.ProductInquiryStatus;
import com.giftmarket.inquiry.exception.ProductInquiryException;
import com.giftmarket.inquiry.repository.ProductInquiryRepository;
import com.giftmarket.inquiry.repository.ProductInquiryAnswerRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductInquiryServiceTest {
    @Mock ProductInquiryRepository inquiries; @Mock ProductInquiryAnswerRepository answers; @Mock ProductRepository products; @Mock UserRepository users;
    @Mock Product product; @Mock User writer; @Mock User sellerUser; @Mock Seller seller; @Mock ProductInquiry inquiry;
    ProductInquiryService service;
    @BeforeEach void setup(){ service = new ProductInquiryService(inquiries, answers, products, users); }

    @Test void createsInquiry(){ visible(); given(users.findById(1L)).willReturn(Optional.of(writer)); given(inquiries.save(any())).willAnswer(i -> i.getArgument(0)); given(writer.getId()).willReturn(1L); nested(product, writer, sellerUser); var r=service.create(1L,10L,new ProductInquiryRequest(" 제목 "," 내용 ",true)); assertThat(r.title()).isEqualTo("제목"); verify(inquiries).save(any()); }
    @Test void unauthenticatedCreateFails(){ assertThatThrownBy(() -> service.create(null,10L,new ProductInquiryRequest("a","b",false))).isInstanceOf(AuthenticationException.class); }
    @Test void hiddenOrDeletedProductCreateFails(){ given(products.findByIdAndStatusInAndAdminHiddenFalseAndDeletedAtIsNull(eq(10L),any())).willReturn(Optional.empty()); assertThatThrownBy(() -> service.create(1L,10L,new ProductInquiryRequest("a","b",false))).isInstanceOf(ProductInquiryException.class); }
    @Test void publicInquiryIsVisible(){ visible(); question(false,1L,2L); given(inquiries.findAllByProductIdAndDeletedAtIsNull(eq(10L),any())).willReturn(new PageImpl<>(List.of(inquiry))); assertThat(service.getInquiries(null,10L,0,10).inquiries().getFirst().content()).isEqualTo("내용"); }
    @Test void privateOwnerCanRead(){ visible(); question(true,1L,2L); given(inquiries.findAllByProductIdAndDeletedAtIsNull(eq(10L),any())).willReturn(new PageImpl<>(List.of(inquiry))); assertThat(service.getInquiries(1L,10L,0,10).inquiries().getFirst().masked()).isFalse(); }
    @Test void privateOtherBuyerIsMasked(){ visible(); question(true,1L,2L); given(inquiries.findAllByProductIdAndDeletedAtIsNull(eq(10L),any())).willReturn(new PageImpl<>(List.of(inquiry))); var r=service.getInquiries(3L,10L,0,10).inquiries().getFirst(); assertThat(r.masked()).isTrue(); assertThat(r.content()).isNull(); assertThat(r.answerContent()).isNull(); }
    @Test void ownerUpdatesWaitingInquiry(){ question(false,1L,2L); given(inquiries.findByIdAndProductIdAndDeletedAtIsNull(5L,10L)).willReturn(Optional.of(inquiry)); service.update(1L,10L,5L,new ProductInquiryRequest("수정","내용",false)); verify(inquiry).updateQuestion("수정","내용",false); }
    @Test void otherBuyerCannotUpdate(){ question(false,1L,2L); given(inquiries.findByIdAndProductIdAndDeletedAtIsNull(5L,10L)).willReturn(Optional.of(inquiry)); assertThatThrownBy(() -> service.update(3L,10L,5L,new ProductInquiryRequest("수정","내용",false))).isInstanceOf(ProductInquiryException.class); }
    @Test void answeredInquiryUpdateIsBlocked(){ question(false,1L,2L); given(inquiries.findByIdAndProductIdAndDeletedAtIsNull(5L,10L)).willReturn(Optional.of(inquiry)); doThrow(new IllegalStateException("답변 완료")).when(inquiry).updateQuestion(any(),any(),anyBoolean()); assertThatThrownBy(() -> service.update(1L,10L,5L,new ProductInquiryRequest("수정","내용",false))).isInstanceOf(ProductInquiryException.class); }
    @Test void ownerSoftDeletesWaitingInquiry(){ question(false,1L,2L); given(inquiries.findByIdAndProductId(5L,10L)).willReturn(Optional.of(inquiry)); service.delete(1L,10L,5L); verify(inquiry).softDelete(); verify(inquiries,never()).delete(any()); }
    @Test void answeredInquiryCanBeSoftDeleted(){ question(false,1L,2L); given(inquiry.getStatus()).willReturn(ProductInquiryStatus.ANSWERED); given(inquiries.findByIdAndProductId(5L,10L)).willReturn(Optional.of(inquiry)); service.delete(1L,10L,5L); verify(inquiry).softDelete(); verifyNoInteractions(answers); }
    @Test void otherBuyerCannotDelete(){ question(false,1L,2L); given(inquiries.findByIdAndProductId(5L,10L)).willReturn(Optional.of(inquiry)); assertThatThrownBy(() -> service.delete(3L,10L,5L)).isInstanceOf(ProductInquiryException.class); verify(inquiry,never()).softDelete(); }
    @Test void paginationUsesNewestFirst(){ visible(); given(inquiries.findAllByProductIdAndDeletedAtIsNull(eq(10L),any())).willReturn(new PageImpl<>(List.of())); service.getInquiries(null,10L,2,10); var captor=org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class); verify(inquiries).findAllByProductIdAndDeletedAtIsNull(eq(10L),captor.capture()); assertThat(captor.getValue().getPageNumber()).isEqualTo(2); assertThat(captor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue(); }

    private void visible(){ given(products.findByIdAndStatusInAndAdminHiddenFalseAndDeletedAtIsNull(eq(10L),any())).willReturn(Optional.of(product)); }
    private void question(boolean privacy,long writerId,long sellerUserId){ given(inquiry.getUser()).willReturn(writer); given(writer.getId()).willReturn(writerId); given(writer.getName()).willReturn("홍길동"); given(inquiry.getProduct()).willReturn(product); nested(product,writer,sellerUser); given(sellerUser.getId()).willReturn(sellerUserId); given(inquiry.getStatus()).willReturn(ProductInquiryStatus.WAITING); given(inquiry.isPrivateInquiry()).willReturn(privacy); given(inquiry.getTitle()).willReturn("제목"); given(inquiry.getContent()).willReturn("내용"); }
    private void nested(Product p,User w,User su){ given(p.getSeller()).willReturn(seller); given(seller.getUser()).willReturn(su); }
}
