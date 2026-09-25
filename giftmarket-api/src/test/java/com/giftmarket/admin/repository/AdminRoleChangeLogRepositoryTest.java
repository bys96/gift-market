package com.giftmarket.admin.repository;

import com.giftmarket.admin.entity.AdminRoleChangeLog;
import com.giftmarket.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-role-change-log;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class AdminRoleChangeLogRepositoryTest {

    @Autowired
    AdminRoleChangeLogRepository repository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void savesRoleChangeHistoryAsImmutableRoleSnapshots() {
        AdminRoleChangeLog saved = repository.saveAndFlush(AdminRoleChangeLog.create(
                1L,
                10L,
                UserRole.SELLER,
                UserRole.ADMIN
        ));

        entityManager.clear();

        AdminRoleChangeLog found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getOperatorUserId()).isEqualTo(1L);
        assertThat(found.getTargetUserId()).isEqualTo(10L);
        assertThat(found.getPreviousRole()).isEqualTo(UserRole.SELLER);
        assertThat(found.getChangedRole()).isEqualTo(UserRole.ADMIN);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select previous_role from admin_role_change_logs where id = ?",
                String.class,
                saved.getId()
        )).isEqualTo("SELLER");
    }

    @Test
    void findsLatestHistoryForTargetFirst() {
        repository.saveAndFlush(AdminRoleChangeLog.create(
                1L, 10L, UserRole.USER, UserRole.ADMIN
        ));
        AdminRoleChangeLog latest = repository.saveAndFlush(AdminRoleChangeLog.create(
                1L, 10L, UserRole.ADMIN, UserRole.USER
        ));

        List<AdminRoleChangeLog> logs = repository
                .findByTargetUserIdInOrderByCreatedAtDescIdDesc(List.of(10L));

        assertThat(logs).hasSize(2);
        assertThat(logs.getFirst().getId()).isEqualTo(latest.getId());
    }
}
