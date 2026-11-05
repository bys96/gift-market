package com.giftmarket.product.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MeiliSchedulerTest {
    @ParameterizedTest
    @CsvSource({"mysql,false,false", "mysql,true,true", "meilisearch,false,true", "meilisearch,true,true"})
    void indexingControlsSchedulerAndCommitListener(String engine, boolean indexing, boolean active) {
        var factory = mock(EntityManagerFactory.class);
        var sessionFactory = mock(SessionFactoryImplementor.class, RETURNS_DEEP_STUBS);
        var services = sessionFactory.getServiceRegistry();
        var listeners = mock(EventListenerRegistry.class);
        doReturn(listeners).when(services).getService(EventListenerRegistry.class);
        when(factory.unwrap(SessionFactoryImplementor.class)).thenReturn(sessionFactory);
        new ApplicationContextRunner()
                .withUserConfiguration(MeiliSyncScheduler.class, MeiliCommitListener.class)
                .withBean(EntityManagerFactory.class, () -> factory)
                .withBean(MeiliIndexService.class, () -> mock(MeiliIndexService.class))
                .withPropertyValues("search.engine=" + engine, "search.indexing-enabled=" + indexing,
                        "search.sync-delay-ms=3600000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.containsBean("meiliSearchScheduler")).isEqualTo(active);
                    assertThat(context.getBeansOfType(MeiliCommitListener.class)).hasSize(active ? 1 : 0);
                });
    }
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
