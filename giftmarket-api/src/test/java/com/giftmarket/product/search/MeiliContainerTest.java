package com.giftmarket.product.search;

import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("meili-container")
class MeiliContainerTest {
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
            client.configure();
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
