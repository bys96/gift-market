package com.giftmarket.settlement.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.settlement.dto.request.AdminSettlementGenerateRequest;
import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementLedgerSourceType;
import com.giftmarket.settlement.entity.SettlementLedgerType;
import com.giftmarket.settlement.entity.SettlementStatus;
import com.giftmarket.settlement.exception.AdminSettlementException;
import com.giftmarket.settlement.exception.AdminSettlementOperationException;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import com.giftmarket.settlement.repository.SettlementRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSettlementServiceTest {

    @Mock UserRepository userRepository;
    @Mock SettlementRepository settlementRepository;
    @Mock SettlementLedgerEntryRepository ledgerRepository;
    @Mock SettlementGenerationService generationService;
    @Mock SettlementManagementService managementService;
    @Mock User admin;
    @Mock User otherUser;
    @Mock Seller seller;
    @Mock SellerOrder sellerOrder;

    private AdminSettlementService service;
    private Settlement settlement;
    private AdminSettlementGenerateRequest request;

    @BeforeEach
    void setUp() {
        service = new AdminSettlementService(userRepository, settlementRepository, ledgerRepository,
                generationService, managementService);
        given(admin.getRole()).willReturn(UserRole.ADMIN);
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));
        given(seller.getId()).willReturn(20L);
        given(seller.getStoreName()).willReturn("선물 상점");
        settlement = Settlement.create(seller, "ST-20260916-test",
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 16, 0, 0),
                10_000L, 3_000L, 1_000L, 2_000L, 700L, 0L, 5);
        ReflectionTestUtils.setField(settlement, "id", 50L);
        request = new AdminSettlementGenerateRequest(20L,
                LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 9, 16, 0, 0),
                LocalDateTime.of(2026, 9, 16, 0, 0));
    }

    @Test
    void adminListUsesFiltersPaginationAndSellerStoreSnapshot() {
        PageRequest pageRequest = PageRequest.of(1, 2);
        given(settlementRepository.findAdminSettlements(20L, SettlementStatus.READY,
                request.periodStart(), request.periodEnd(), pageRequest))
                .willReturn(new PageImpl<>(List.of(settlement), pageRequest, 3));

        var result = service.getSettlements(1L, 20L, SettlementStatus.READY,
                request.periodStart(), request.periodEnd(), 1, 2);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.content().getFirst().storeName()).isEqualTo("선물 상점");
        assertThat(result.content().getFirst().settlementAmount()).isEqualTo(9_300L);
        verify(settlementRepository).findAdminSettlements(20L, SettlementStatus.READY,
                request.periodStart(), request.periodEnd(), pageRequest);
    }

    @Test
    void detailIncludesLedgerSourceAndAuditFields() {
        given(sellerOrder.getSeller()).willReturn(seller);
        given(sellerOrder.getId()).willReturn(99L);
        SettlementLedgerEntry entry = SettlementLedgerEntry.create(seller, sellerOrder,
                SettlementLedgerType.SALE_PRODUCT, 10_000L,
                SettlementLedgerSourceType.SELLER_ORDER, 99L, "SALE_PRODUCT",
                LocalDateTime.of(2026, 9, 15, 12, 0), null,
                1_000, 10_000L, null, null, null);
        ReflectionTestUtils.setField(entry, "id", 80L);
        given(settlementRepository.findAdminById(50L)).willReturn(Optional.of(settlement));
        given(ledgerRepository.findAdminSettlementEntries(50L)).willReturn(List.of(entry));

        var detail = service.getSettlement(1L, 50L);

        assertThat(detail.settlement().sellerId()).isEqualTo(20L);
        assertThat(detail.ledgerEntries()).hasSize(1);
        assertThat(detail.ledgerEntries().getFirst().sourceType())
                .isEqualTo(SettlementLedgerSourceType.SELLER_ORDER);
        assertThat(detail.ledgerEntries().getFirst().sellerOrderId()).isEqualTo(99L);
    }

    @Test
    void missingDetailIsNotFound() {
        assertThatThrownBy(() -> service.getSettlement(1L, 999L))
                .isInstanceOf(AdminSettlementException.class);
    }

    @Test
    void userOrSellerCannotReadOrGenerate() {
        given(otherUser.getRole()).willReturn(UserRole.USER);
        given(userRepository.findById(2L)).willReturn(Optional.of(otherUser));
        assertThatThrownBy(() -> service.getSettlements(2L, null, null, null, null, 0, 20))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> service.generate(2L, request))
                .isInstanceOf(AuthenticationException.class);
        given(otherUser.getRole()).willReturn(UserRole.SELLER);
        assertThatThrownBy(() -> service.getSettlement(2L, 50L))
                .isInstanceOf(AuthenticationException.class);
        verify(generationService, never()).generate(new SettlementGenerationCommand(
                request.sellerId(), request.periodStart(), request.periodEnd(), request.cutoff()));
    }

    @Test
    void generateReturnsCreatedSettlement() {
        var command = new SettlementGenerationCommand(request.sellerId(), request.periodStart(),
                request.periodEnd(), request.cutoff());
        given(generationService.generate(command)).willReturn(Optional.of(settlement));

        var result = service.generate(1L, request);

        assertThat(result.created()).isTrue();
        assertThat(result.settlement().settlementId()).isEqualTo(50L);
        verify(generationService).generate(command);
    }

    @Test
    void noCandidatesReturnsExplicitNoOp() {
        var command = new SettlementGenerationCommand(request.sellerId(), request.periodStart(),
                request.periodEnd(), request.cutoff());
        given(generationService.generate(command)).willReturn(Optional.empty());

        var result = service.generate(1L, request);

        assertThat(result.created()).isFalse();
        assertThat(result.settlement()).isNull();
    }

    @Test
    void duplicatePeriodAndClaimRulesRemainInGenerationService() {
        var command = new SettlementGenerationCommand(request.sellerId(), request.periodStart(),
                request.periodEnd(), request.cutoff());
        given(generationService.generate(command)).willThrow(new SettlementException("동일 기간"));

        assertThatThrownBy(() -> service.generate(1L, request))
                .isInstanceOf(AdminSettlementOperationException.class).hasMessageContaining("동일 기간");
        verify(generationService).generate(command);
    }

    @Test
    void holdReleaseAndConfirmDelegateToPhaseSixServices() {
        given(managementService.hold(50L, 1L, "확인 필요")).willReturn(settlement);
        given(managementService.releaseHold(50L, 1L)).willReturn(settlement);
        given(managementService.confirm(50L, 1L)).willReturn(settlement);

        assertThat(service.hold(1L, 50L, "확인 필요").settlementId()).isEqualTo(50L);
        assertThat(service.release(1L, 50L).settlementId()).isEqualTo(50L);
        assertThat(service.confirm(1L, 50L).settlementId()).isEqualTo(50L);
        verify(managementService).hold(50L, 1L, "확인 필요");
        verify(managementService).releaseHold(50L, 1L);
        verify(managementService).confirm(50L, 1L);
    }

    @Test
    void illegalStatusTransitionsAreNotTranslatedOrBypassed() {
        given(managementService.hold(50L, 1L, "다시 보류")).willThrow(new SettlementException("CONFIRMED"));
        given(managementService.releaseHold(50L, 1L)).willThrow(new SettlementException("READY"));
        given(managementService.confirm(50L, 1L)).willThrow(new SettlementException("ON_HOLD"));

        assertThatThrownBy(() -> service.hold(1L, 50L, "다시 보류"))
                .isInstanceOf(AdminSettlementOperationException.class);
        assertThatThrownBy(() -> service.release(1L, 50L))
                .isInstanceOf(AdminSettlementOperationException.class);
        assertThatThrownBy(() -> service.confirm(1L, 50L))
                .isInstanceOf(AdminSettlementOperationException.class);
    }

    @Test
    void invalidPeriodAndPageAreRejected() {
        assertThatThrownBy(() -> service.getSettlements(1L, null, null,
                request.periodEnd(), request.periodStart(), 0, 20))
                .isInstanceOf(AdminSettlementOperationException.class);
        assertThatThrownBy(() -> service.getSettlements(1L, null, null,
                null, null, -1, 20))
                .isInstanceOf(AdminSettlementOperationException.class);
        assertThatThrownBy(() -> service.generate(1L, new AdminSettlementGenerateRequest(
                20L, request.periodEnd(), request.periodStart(), request.cutoff())))
                .isInstanceOf(AdminSettlementOperationException.class);
    }
}
