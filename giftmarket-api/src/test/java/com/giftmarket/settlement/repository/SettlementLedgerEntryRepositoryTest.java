package com.giftmarket.settlement.repository;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementLedgerSourceType;
import com.giftmarket.settlement.entity.SettlementLedgerType;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:settlement-ledger;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.task.scheduling.enabled=false",
        "app.jwt.secret=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "app.jwt.refresh-token-encryption-key=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "payment.toss.secret-key=test-only-key"
})
@Transactional
class SettlementLedgerEntryRepositoryTest {

    @Autowired SettlementLedgerEntryRepository repository;
    @Autowired SettlementRepository settlementRepository;
    @Autowired EntityManager entityManager;

    private Seller seller;
    private SellerOrder sellerOrder;
    private User adminUser;

    @BeforeEach
    void setUp() {
        User buyer = User.createOAuthUser(
                "buyer-settlement@example.com",
                "buyer",
                null,
                AuthProvider.GOOGLE,
                "buyer-settlement-provider"
        );
        entityManager.persist(buyer);

        User sellerUser = User.createOAuthUser(
                "seller-settlement@example.com",
                "seller",
                null,
                AuthProvider.GOOGLE,
                "seller-settlement-provider"
        );
        entityManager.persist(sellerUser);
        adminUser = sellerUser;
        seller = Seller.create(sellerUser, "settlement-store", null);
        entityManager.persist(seller);

        Order order = Order.createPendingPayment(
                "ORDER-SETTLEMENT-1",
                buyer,
                10_000L,
                0L,
                "buyer",
                "01012345678",
                "12345",
                "address",
                null
        );
        entityManager.persist(order);
        sellerOrder = SellerOrder.createPendingPayment(order, seller);
        entityManager.persist(sellerOrder);
        entityManager.flush();
    }

    @Test
    void sourceKeyIsUnique() {
        repository.saveAndFlush(productSale("SALE_PRODUCT"));

        assertThatThrownBy(() -> repository.saveAndFlush(productSale("SALE_PRODUCT")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reversalReferenceIsUnique() {
        SettlementLedgerEntry original = repository.saveAndFlush(productSale("ORIGINAL"));
        repository.saveAndFlush(reversal(original, "REVERSAL-1"));

        assertThatThrownBy(() -> repository.saveAndFlush(reversal(original, "REVERSAL-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void settlementNumberIsUnique() {
        settlementRepository.saveAndFlush(settlement("ST-UNIQUE", 0));

        assertThatThrownBy(() -> settlementRepository.saveAndFlush(settlement("ST-UNIQUE", 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sellerPeriodIsUnique() {
        settlementRepository.saveAndFlush(settlement("ST-PERIOD-1", 0));

        assertThatThrownBy(() -> settlementRepository.saveAndFlush(settlement("ST-PERIOD-2", 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sellerSummaryQueryExcludesAssignedEntriesAndDetailIsOrdered() {
        Settlement assignedSettlement = settlement("ST-QUERY", 0);
        SettlementLedgerEntry assigned = productSale("ASSIGNED");
        assigned.activateEligibility(LocalDateTime.of(2026, 9, 2, 12, 0));
        assigned.assignTo(assignedSettlement);
        settlementRepository.saveAndFlush(assignedSettlement);
        repository.saveAndFlush(assigned);

        SettlementLedgerEntry unassigned = productSale("UNASSIGNED");
        repository.saveAndFlush(unassigned);
        entityManager.clear();

        var amounts = repository.findUnassignedAmounts(seller.getId());
        assertThat(amounts).hasSize(1);
        assertThat(amounts.getFirst().getAmount()).isEqualTo(10_000L);
        assertThat(amounts.getFirst().getEligibleAt()).isNull();

        var details = repository.findSellerSettlementEntries(
                assignedSettlement.getId(), seller.getId());
        assertThat(details).extracting(SettlementLedgerEntry::getId)
                .containsExactly(assigned.getId());
    }

    @Test
    void adminSettlementQueriesSupportNullableFiltersAndSellerDetails() {
        Settlement older = settlement("ST-ADMIN-OLDER", 0);
        Settlement newer = settlement("ST-ADMIN-NEWER", 1);
        settlementRepository.saveAndFlush(older);
        settlementRepository.saveAndFlush(newer);
        entityManager.clear();

        var all = settlementRepository.findAdminSettlements(
                null, null, null, null, PageRequest.of(0, 10));
        assertThat(all.getContent()).extracting(Settlement::getSettlementNumber)
                .containsExactly("ST-ADMIN-NEWER", "ST-ADMIN-OLDER");
        assertThat(all.getContent().getFirst().getSeller().getStoreName())
                .isEqualTo("settlement-store");

        var filtered = settlementRepository.findAdminSettlements(
                seller.getId(), com.giftmarket.settlement.entity.SettlementStatus.READY,
                older.getPeriodStart(), older.getPeriodEnd(), PageRequest.of(0, 10));
        assertThat(filtered.getContent()).extracting(Settlement::getId)
                .containsExactly(older.getId());
        assertThat(settlementRepository.findAdminById(newer.getId())).isPresent();
    }

    private Settlement settlement(String number, int startOffsetDays) {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0).plusDays(startOffsetDays);
        return Settlement.create(
                seller,
                number,
                start,
                start.plusDays(1),
                10_000L,
                0L,
                0L,
                0L,
                1_000L,
                0L,
                1
        );
    }

    private SettlementLedgerEntry productSale(String detailKey) {
        return SettlementLedgerEntry.create(
                seller,
                sellerOrder,
                SettlementLedgerType.SALE_PRODUCT,
                10_000L,
                SettlementLedgerSourceType.SELLER_ORDER,
                sellerOrder.getId(),
                detailKey,
                LocalDateTime.of(2026, 9, 1, 12, 0),
                null,
                1_000,
                10_000L,
                null,
                null,
                null
        );
    }

    private SettlementLedgerEntry reversal(
            SettlementLedgerEntry original,
            String detailKey
    ) {
        return SettlementLedgerEntry.create(
                seller,
                sellerOrder,
                SettlementLedgerType.MANUAL_ADJUSTMENT,
                -1_000L,
                SettlementLedgerSourceType.LEDGER_ENTRY,
                original.getId(),
                detailKey,
                LocalDateTime.of(2026, 9, 2, 12, 0),
                null,
                null,
                null,
                "원장 정정",
                adminUser,
                original
        );
    }
}
