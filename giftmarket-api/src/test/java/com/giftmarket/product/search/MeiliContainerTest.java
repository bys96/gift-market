package com.giftmarket.product.search;

import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("meili-container")
class MeiliContainerTest {
    // Cold Docker hosts may exceed the production request budget; retry only the idempotent settings operation.
    private void configureWithRetry(MeiliClient client) {
        for (int attempt = 0; ; attempt++) {
            try { client.configure(); return; }
            catch (IllegalStateException ex) {
                if (attempt >= 2 || !"index-task-pending".equals(ex.getMessage())) throw ex;
            }
        }
    }
    @Test void dictionaryFixesMissingSynonymsOnExistingDocumentsWithoutReupload() {
        try (var container = new GenericContainer<>(DockerImageName.parse("getmeili/meilisearch:v1.16.0"))
                .withExposedPorts(7700).withEnv("MEILI_NO_ANALYTICS", "true")
                .withEnv("MEILI_MASTER_KEY", "local-test-only-not-a-production-key")
                .withEnv("MEILI_MAX_INDEXING_MEMORY", "128Mb").withEnv("MEILI_MAX_INDEXING_THREADS", "1")
                .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/health"))) {
            container.start();
            var config = new MeiliProperties();
            config.setIndexingEnabled(true);
            config.setApiKey("local-test-only-not-a-production-key");
            config.setUrl("http://" + container.getHost() + ":" + container.getMappedPort(7700));
            config.setTimeoutMs(3000);
            var client = new MeiliClient(config);
            configureWithRetry(client);
            assertThat(client.synonyms()).isEqualTo(config.effectiveSynonyms());
            client.updateSynonyms(Map.of());
            client.upsert(List.of(Map.of("id", 101, "name", "호텔 침구 세트", "category", "침구"),
                    Map.of("id", 102, "name", "포근한 담요", "category", "담요"),
                    Map.of("id", 103, "name", "머그컵")));
            assertThat(client.search("침구")).contains(101L);
            assertThat(client.search("이불")).isEmpty(); // Content exists, missing expansion.
            var index = new MeiliIndexService(org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class), client, config);
            assertThat(index.settings(true).status()).isEqualTo("APPLIED");
            assertThat(index.settings(false).matches()).isTrue();
            assertThat(index.settings(true).status()).isEqualTo("VERIFIED");
            assertThat(client.search("이불")).containsExactly(101L);
            assertThat(client.search("블랭킷")).containsExactly(102L);
            assertThat(client.search("매트리스")).isEmpty(); // No content, not a synonym/settings failure.
            assertThat(client.search("텀블러")).doesNotContain(103L); // Distinct product types stay distinct.
            var bedding = client.search("침구");
            var beddingCategory = client.search("침구류");
            for (String query : List.of("침구", "침구류", "침구", "침구", "침구류")) {
                assertThat(client.search(query)).as("history-independent: %s", query)
                        .isEqualTo(query.equals("침구") ? bedding : beddingCategory);
            }
            client.updateSynonyms(Map.of());
            assertThat(index.settings(false).status()).isEqualTo("MISMATCH");
            assertThat(client.search("이불")).isEmpty();
        }
    }
    @Test
    void realEngineKoreanTyposSynonymsWhitespaceAndDocumentLifecycle() {
        try (var container = new GenericContainer<>(DockerImageName.parse("getmeili/meilisearch:v1.16.0"))
                .withExposedPorts(7700).withEnv("MEILI_NO_ANALYTICS", "true")
                .withEnv("MEILI_MASTER_KEY", "local-test-only-not-a-production-key")
                .withEnv("MEILI_MAX_INDEXING_MEMORY", "128Mb").withEnv("MEILI_MAX_INDEXING_THREADS", "1")
                .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/health"))) {
            container.start();
            var config = new MeiliProperties();
            config.setUrl("http://" + container.getHost() + ":" + container.getMappedPort(7700));
            config.setTimeoutMs(3000);
            config.setApiKey("local-test-only-not-a-production-key");
            config.setSynonyms(Map.of("침구류", List.of("이불"), "이불", List.of("침구류")));
            var client = new MeiliClient(config);
            configureWithRetry(client);
            // Explicitly synthetic product data: this does NOT assert the contents of a production product.
            client.upsert(List.of(Map.of("id", 1, "name", "편안한 숙면 페브릭 세트", "nameCompact", "편안한숙면페브릭세트",
                    "description", "이불과 베개 커버", "category", "침구", "occasions", List.of("부모님 감사선물")),
                    Map.of("id", 2, "name", "꽃다발", "occasions", List.of("여자친구 생일선물")),
                    Map.of("id", 3, "name", "Chocolate gift")));
            for (String query : List.of("이불", "침구류", "숙면", "편안한 숙면 페브릭 세트", "편안한숙면페브릭세트", "부모님 감사선물"))
                assertThat(client.search(query)).as(query).contains(1L);
            assertThat(client.search("여자친구 생일선물")).contains(2L);
            assertThat(client.search("Choclate")).contains(3L);
            assertThat(client.search("숙먼")).contains(1L);
            client.upsert(List.of(Map.of("id", 1, "name", "Replacement")));
            assertThat(client.search("이불")).doesNotContain(1L);
            client.upsert(List.of(Map.of("id", 1, "name", "Replacement")));
            assertThat(client.search("Replacement")).containsExactly(1L);
            client.delete(List.of(1L));
            assertThat(client.search("Replacement")).isEmpty();
            client.clear();
            assertThat(client.search("꽃다발")).isEmpty();
            config.setMaxMatches(1);
            client.configure();
            client.upsert(List.of(Map.of("id", 8, "name", "bedding one"), Map.of("id", 9, "name", "bedding two")));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.search("bedding"))
                    .isInstanceOf(MeiliClient.CapacityExceeded.class);
        }
    }
}
