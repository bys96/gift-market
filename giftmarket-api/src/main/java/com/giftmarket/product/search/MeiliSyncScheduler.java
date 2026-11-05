package com.giftmarket.product.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Never occupy the application's payment/order scheduler while waiting on the search engine. */
@Configuration
@ConditionalOnExpression("'${search.engine:mysql}' == 'meilisearch' or ${search.indexing-enabled:false}")
public class MeiliSyncScheduler {
    private final MeiliIndexService index;
    public MeiliSyncScheduler(MeiliIndexService index) { this.index = index; }
    @Bean(value = "meiliSearchScheduler", defaultCandidate = false)
    public ThreadPoolTaskScheduler meiliSearchScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("meili-sync-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
    @Scheduled(scheduler = "meiliSearchScheduler", initialDelayString = "${search.sync-delay-ms:5000}",
            fixedDelayString = "${search.sync-delay-ms:5000}")
    public void synchronize() { index.synchronize(); }
}
