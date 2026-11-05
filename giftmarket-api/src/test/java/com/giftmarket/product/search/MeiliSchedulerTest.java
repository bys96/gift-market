package com.giftmarket.product.search;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MeiliSchedulerTest {
    @Configuration
    @EnableScheduling
    static class Scheduling { }

    @Test void searchSchedulerDoesNotReplaceApplicationSchedulerAndIsAbsentWhenDisabled() {
        var runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(Scheduling.class, MeiliSyncScheduler.class)
                .withBean(MeiliIndexService.class, () -> mock(MeiliIndexService.class));
        runner.run(context -> {
            assertThat(context).hasBean("taskScheduler");
            assertThat(context).doesNotHaveBean("meiliSearchScheduler");
        });
        runner.withPropertyValues("search.engine=meilisearch", "search.sync-delay-ms=3600000").run(context -> {
            assertThat(context).hasBean("taskScheduler");
            assertThat(context).hasBean("meiliSearchScheduler");
            assertThat(context.getBean("taskScheduler")).isNotSameAs(context.getBean("meiliSearchScheduler"));
        });
    }
}
