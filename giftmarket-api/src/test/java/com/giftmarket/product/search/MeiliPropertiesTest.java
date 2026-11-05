package com.giftmarket.product.search;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

class MeiliPropertiesTest {
    @Configuration
    @EnableConfigurationProperties(MeiliProperties.class)
    static class PropertiesConfiguration { }

    @Test void environmentVariablesEnableIndexingWithoutSwitchingSearchEngine() {
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class)
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("testEnvironment", Map.of(
                                "SEARCH_ENGINE", "mysql", "SEARCH_INDEXING_ENABLED", "true"))))
                .run(context -> {
                    var config = context.getBean(MeiliProperties.class);
                    assertThat(config.enabled()).isFalse();
                    assertThat(config.indexingEnabled()).isTrue();
                });
    }

    @Test void omittedSettingsPreserveDefaults() {
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class).run(context -> {
            var config = context.getBean(MeiliProperties.class);
            assertThat(config.enabled()).isFalse();
            assertThat(config.indexingEnabled()).isFalse();
        });
    }
}
