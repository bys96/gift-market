package com.giftmarket.settlement.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.settlement.dto.request.AdminSettlementGenerateRequest;
import com.giftmarket.settlement.dto.response.AdminSettlementDetailResponse;
import com.giftmarket.settlement.dto.response.AdminSettlementGenerateResponse;
import com.giftmarket.settlement.dto.response.AdminSettlementLedgerResponse;
import com.giftmarket.settlement.dto.response.AdminSettlementPageResponse;
import com.giftmarket.settlement.dto.response.AdminSettlementResponse;
import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementStatus;
import com.giftmarket.settlement.exception.AdminSettlementException;
import com.giftmarket.settlement.exception.AdminSettlementOperationException;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import com.giftmarket.settlement.repository.SettlementRepository;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminSettlementService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementLedgerEntryRepository ledgerRepository;
    private final SettlementGenerationService generationService;
    private final SettlementManagementService managementService;

    @Transactional(readOnly = true)
    public AdminSettlementPageResponse getSettlements(
            Long adminUserId,
            Long sellerId,
            SettlementStatus status,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            int page,
            int size
    ) {
        requireAdmin(adminUserId);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || sellerId != null && sellerId <= 0L
                || periodStart != null && periodEnd != null && !periodStart.isBefore(periodEnd)) {
            throw new AdminSettlementOperationException("정산 목록 조회 조건이 올바르지 않습니다.");
        }
        return AdminSettlementPageResponse.from(settlementRepository.findAdminSettlements(
                sellerId, status, periodStart, periodEnd, PageRequest.of(page, size)
        ).map(AdminSettlementResponse::from));
    }

    @Transactional(readOnly = true)
    public AdminSettlementDetailResponse getSettlement(Long adminUserId, Long settlementId) {
        requireAdmin(adminUserId);
        Settlement settlement = settlementRepository.findAdminById(settlementId)
                .orElseThrow(() -> new AdminSettlementException("정산을 찾을 수 없습니다."));
        return AdminSettlementDetailResponse.from(
                settlement,
                ledgerRepository.findAdminSettlementEntries(settlementId).stream()
                        .map(AdminSettlementLedgerResponse::from).toList()
        );
    }

    @Transactional
    public AdminSettlementGenerateResponse generate(
            Long adminUserId,
            AdminSettlementGenerateRequest request
    ) {
        requireAdmin(adminUserId);
        if (request == null || request.periodStart() == null || request.periodEnd() == null
                || !request.periodStart().isBefore(request.periodEnd())) {
            throw new AdminSettlementOperationException("정산 시작 시각은 종료 시각보다 이전이어야 합니다.");
        }
        try {
            return generationService.generate(new SettlementGenerationCommand(
                    request.sellerId(), request.periodStart(), request.periodEnd(), request.cutoff()
            )).map(settlement -> new AdminSettlementGenerateResponse(
                    true, AdminSettlementResponse.from(settlement)
            )).orElseGet(() -> new AdminSettlementGenerateResponse(false, null));
        } catch (SettlementException exception) {
            throw new AdminSettlementOperationException(exception.getMessage());
        }
    }

    @Transactional
    public AdminSettlementResponse hold(Long adminUserId, Long settlementId, String reason) {
        try {
            return AdminSettlementResponse.from(managementService.hold(settlementId, adminUserId, reason));
        } catch (SettlementException exception) {
            throw new AdminSettlementOperationException(exception.getMessage());
        }
    }

    @Transactional
    public AdminSettlementResponse release(Long adminUserId, Long settlementId) {
        try {
            return AdminSettlementResponse.from(managementService.releaseHold(settlementId, adminUserId));
        } catch (SettlementException exception) {
            throw new AdminSettlementOperationException(exception.getMessage());
        }
    }

    @Transactional
    public AdminSettlementResponse confirm(Long adminUserId, Long settlementId) {
        try {
            return AdminSettlementResponse.from(managementService.confirm(settlementId, adminUserId));
        } catch (SettlementException exception) {
            throw new AdminSettlementOperationException(exception.getMessage());
        }
    }

    private void requireAdmin(Long adminUserId) {
        if (adminUserId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }
        if (userRepository.findById(adminUserId)
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .isEmpty()) {
            throw new AuthenticationException("관리자 권한이 필요합니다.");
        }
    }
}
