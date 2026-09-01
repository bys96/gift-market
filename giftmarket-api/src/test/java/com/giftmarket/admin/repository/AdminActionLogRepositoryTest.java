package com.giftmarket.admin.repository;

import com.giftmarket.admin.entity.AdminActionLog;
import com.giftmarket.admin.entity.AdminActionTargetType;
import com.giftmarket.admin.entity.AdminActionType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-action-log;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.task.scheduling.enabled=false",
        "app.jwt.secret=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "payment.toss.secret-key=test-only-key"
})
@Transactional
class AdminActionLogRepositoryTest {

    @Autowired
    AdminActionLogRepository repository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void savesAndLoadsActionLogWithTrimmedReason() {
        AdminActionLog saved = repository.saveAndFlush(AdminActionLog.create(
                10L,
                AdminActionType.USER_SUSPENDED,
                AdminActionTargetType.USER,
                20L,
                "  운영 정책 위반  "
        ));

        entityManager.clear();

        AdminActionLog found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getAdminUserId()).isEqualTo(10L);
        assertThat(found.getActionType()).isEqualTo(AdminActionType.USER_SUSPENDED);
        assertThat(found.getTargetType()).isEqualTo(AdminActionTargetType.USER);
        assertThat(found.getTargetId()).isEqualTo(20L);
        assertThat(found.getReason()).isEqualTo("운영 정책 위반");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void storesEnumsAsStrings() {
        AdminActionLog saved = repository.saveAndFlush(AdminActionLog.create(
                10L,
                AdminActionType.SELLER_SALES_SUSPENDED,
                AdminActionTargetType.SELLER,
                30L,
                "판매 운영 정책 위반"
        ));

        String actionType = jdbcTemplate.queryForObject(
                "select action_type from admin_action_logs where id = ?",
                String.class,
                saved.getId()
        );
        String targetType = jdbcTemplate.queryForObject(
                "select target_type from admin_action_logs where id = ?",
                String.class,
                saved.getId()
        );

        assertThat(actionType).isEqualTo("SELLER_SALES_SUSPENDED");
        assertThat(targetType).isEqualTo("SELLER");
    }

    @Test
    void acceptsReasonWithFiveHundredCharacters() {
        AdminActionLog log = AdminActionLog.create(
                10L,
                AdminActionType.PRODUCT_HIDDEN,
                AdminActionTargetType.PRODUCT,
                40L,
                "가".repeat(500)
        );

        assertThat(log.getReason()).hasSize(500);
    }

    @Test
    void rejectsNullReason() {
        assertThatThrownBy(() -> createWithReason(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyReason() {
        assertThatThrownBy(() -> createWithReason(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankReason() {
        assertThatThrownBy(() -> createWithReason("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsReasonOverFiveHundredCharacters() {
        assertThatThrownBy(() -> createWithReason("가".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AdminActionLog createWithReason(String reason) {
        return AdminActionLog.create(
                10L,
                AdminActionType.USER_REACTIVATED,
                AdminActionTargetType.USER,
                20L,
                reason
        );
    }
}
