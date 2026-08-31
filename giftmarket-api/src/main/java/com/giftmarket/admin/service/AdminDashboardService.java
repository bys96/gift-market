package com.giftmarket.admin.service;

import com.giftmarket.admin.dto.response.AdminDashboardResponse;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.entity.ExchangeRequestStatus;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.repository.ExchangeRequestRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.ReturnRequestRepository;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerApplicationStatus;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerApplicationRepository;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.entity.UserStatus;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final int RECENT_LIMIT = 5;
    private static final Sort RECENT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final SellerApplicationRepository sellerApplicationRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderCancellationRepository orderCancellationRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard(Long adminUserId) {
        getAdmin(adminUserId);

        return new AdminDashboardResponse(
                new AdminDashboardResponse.ActionCenter(
                        sellerApplicationRepository.countByStatus(SellerApplicationStatus.PENDING),
                        orderCancellationRepository.countByStatus(OrderCancellationStatus.REQUESTED),
                        returnRequestRepository.countByStatus(ReturnRequestStatus.REQUESTED),
                        exchangeRequestRepository.countByStatus(ExchangeRequestStatus.REQUESTED)
                ),
                new AdminDashboardResponse.Summary(
                        userRepository.countByStatusNot(UserStatus.WITHDRAWN),
                        sellerRepository.countByStatus(SellerStatus.ACTIVE),
                        productRepository.countByStatusAndDeletedAtIsNull(ProductStatus.ON_SALE),
                        orderRepository.count()
                ),
                recentOrders(),
                recentSellerApplications()
        );
    }

    private List<AdminDashboardResponse.RecentOrder> recentOrders() {
        return orderRepository.findAllBy(PageRequest.of(0, RECENT_LIMIT, RECENT_SORT))
                .stream()
                .map(this::toRecentOrder)
                .toList();
    }

    private AdminDashboardResponse.RecentOrder toRecentOrder(Order order) {
        return new AdminDashboardResponse.RecentOrder(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getOrderedAt(),
                order.getCreatedAt()
        );
    }

    private List<AdminDashboardResponse.RecentSellerApplication> recentSellerApplications() {
        return sellerApplicationRepository.findAllBy(
                        PageRequest.of(0, RECENT_LIMIT, RECENT_SORT)
                )
                .stream()
                .map(this::toRecentSellerApplication)
                .toList();
    }

    private AdminDashboardResponse.RecentSellerApplication toRecentSellerApplication(
            SellerApplication application
    ) {
        return new AdminDashboardResponse.RecentSellerApplication(
                application.getId(),
                application.getStoreName(),
                application.getUser().getName(),
                application.getStatus(),
                application.getCreatedAt()
        );
    }

    private User getAdmin(Long adminUserId) {
        if (adminUserId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new AuthenticationException("사용자를 찾을 수 없습니다."));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new AuthenticationException("관리자 권한이 필요합니다.");
        }

        return admin;
    }
}
