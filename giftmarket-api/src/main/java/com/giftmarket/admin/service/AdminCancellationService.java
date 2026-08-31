package com.giftmarket.admin.service;
import com.giftmarket.admin.dto.response.*;
import com.giftmarket.admin.exception.AdminCancellationException;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.repository.*;
import com.giftmarket.user.entity.*;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class AdminCancellationService {
 private static final Sort SORT=Sort.by(Sort.Order.desc("requestedAt"),Sort.Order.desc("id"));
 private final UserRepository users;private final OrderCancellationRepository cancellations;private final OrderCancellationItemRepository items;private final PaymentRepository payments;private final PaymentCancellationRepository paymentCancellations;
 @Transactional(readOnly=true) public AdminCancellationPageResponse getCancellations(Long adminId,int page,int size,String keyword,OrderCancellationStatus status,Boolean approval){admin(adminId);var p=cancellations.findAdminCancellations(normalize(keyword),status,approval,PageRequest.of(page,size,SORT));var ids=p.getContent().stream().map(OrderCancellation::getId).toList();if(ids.isEmpty())return AdminCancellationPageResponse.from(p,List.of());var summaries=items.summarizeAdminCancellations(ids).stream().collect(Collectors.toMap(AdminCancellationItemSummaryProjection::getCancellationId,Function.identity()));var refunds=paymentCancellations.findAllByOrderCancellationIdIn(ids).stream().collect(Collectors.toMap(pc->pc.getOrderCancellation().getId(),Function.identity()));var content=p.stream().map(c->{var s=summaries.get(c.getId());return AdminCancellationSummaryResponse.from(c,s==null?null:s.getRepresentativeProductName(),s==null?0:s.getProductTypeCount(),s==null?0:s.getRequestedQuantity(),refunds.get(c.getId()));}).toList();return AdminCancellationPageResponse.from(p,content);}
 @Transactional(readOnly=true) public AdminCancellationDetailResponse getCancellation(Long adminId,Long id){admin(adminId);var c=cancellations.findAdminById(id).orElseThrow(()->new AdminCancellationException("취소 요청을 찾을 수 없습니다."));var cis=items.findAdminByOrderCancellationIdOrderByIdAsc(id);var pc=paymentCancellations.findByOrderCancellationId(id).orElse(null);var payment=payments.findFirstByOrderIdOrderByIdDesc(c.getOrder().getId()).orElse(null);long refunded=payment==null?0:paymentCancellations.sumAmountByPaymentIdAndStatus(payment.getId(),PaymentCancellationStatus.SUCCEEDED);var o=c.getOrder();var so=c.getSellerOrder();return new AdminCancellationDetailResponse(c.getId(),c.getStatus(),c.isRequiresSellerApproval(),c.getReason(),c.getRejectedReason(),c.getRequestedAt(),c.getProcessingAt(),c.getCompletedAt(),c.getRejectedAt(),c.getFailedAt(),new AdminCancellationDetailResponse.OrderInfo(o.getId(),o.getOrderNumber(),o.getStatus(),o.getOrderedAt()),AdminCancellationDetailResponse.Buyer.from(o.getUser()),new AdminCancellationDetailResponse.SellerInfo(so.getId(),so.getStatus(),so.getSeller().getId(),so.getSeller().getStoreName()),cis.stream().map(AdminCancellationDetailResponse.Item::from).toList(),payment==null?null:new AdminCancellationDetailResponse.PaymentInfo(payment.getId(),payment.getStatus(),payment.getAmount(),refunded),AdminCancellationDetailResponse.PaymentCancellationInfo.from(pc));}
 private String normalize(String v){return v==null||v.isBlank()?null:v.trim();}private void admin(Long id){if(id==null)throw new AuthenticationException("인증이 필요합니다.");var u=users.findById(id).orElseThrow(()->new AuthenticationException("사용자를 찾을 수 없습니다."));if(u.getRole()!=UserRole.ADMIN)throw new AuthenticationException("관리자 권한이 필요합니다.");}
}
