package com.giftmarket.user.repository;

import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:user-last-login;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserLastLoginRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;

    @Test
    void onlyUpdatesLastLoginAndKeepsTheLatestTimestamp() {
        User user = userRepository.saveAndFlush(User.createOAuthUser(
                "last-login@example.test", "Member", null, AuthProvider.GOOGLE, "last-login"
        ));
        Long userId = user.getId();
        entityManager.clear();

        User existing = userRepository.findById(userId).orElseThrow();
        LocalDateTime updatedAt = existing.getUpdatedAt();
        assertThat(existing.getLastLoginAt()).isNull();

        LocalDateTime firstLogin = LocalDateTime.of(2026, 1, 1, 10, 0, 0, 123_456_000);
        LocalDateTime latestLogin = firstLogin.plusSeconds(1);
        userRepository.updateLastLoginAt(userId, firstLogin);
        userRepository.updateLastLoginAt(userId, latestLogin);
        userRepository.updateLastLoginAt(userId, firstLogin);
        entityManager.clear();

        User loggedIn = userRepository.findById(userId).orElseThrow();
        assertThat(loggedIn.getLastLoginAt()).isEqualTo(latestLogin);
        assertThat(loggedIn.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
