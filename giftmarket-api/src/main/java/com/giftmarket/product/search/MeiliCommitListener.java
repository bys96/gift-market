package com.giftmarket.product.search;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** After-commit invalidation only: no network/DB work or business-method changes. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "search.engine", havingValue = "meilisearch")
public class MeiliCommitListener implements SmartInitializingSingleton,
        PostCommitInsertEventListener, PostCommitUpdateEventListener, PostCommitDeleteEventListener {
    private final EntityManagerFactory factory;
    private final MeiliIndexService index;
    public MeiliCommitListener(EntityManagerFactory factory, MeiliIndexService index) { this.factory = factory; this.index = index; }
    @Override public void afterSingletonsInstantiated() {
        var registry = factory.unwrap(SessionFactoryImplementor.class).getServiceRegistry().getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_COMMIT_INSERT, this);
        registry.appendListeners(EventType.POST_COMMIT_UPDATE, this);
        registry.appendListeners(EventType.POST_COMMIT_DELETE, this);
    }
    @Override public boolean requiresPostCommitHandling(EntityPersister persister) {
        String name = persister.getMappedClass().getSimpleName();
        return java.util.Set.of("Product", "Category", "Seller", "GiftOccasion", "ProductGiftOccasion").contains(name);
    }
    private void changed(EntityPersister persister) { if (requiresPostCommitHandling(persister)) index.changed(); }
    @Override public void onPostInsert(PostInsertEvent event) { changed(event.getPersister()); }
    @Override public void onPostUpdate(PostUpdateEvent event) { changed(event.getPersister()); }
    @Override public void onPostDelete(PostDeleteEvent event) {
        if (event.getEntity() instanceof com.giftmarket.product.entity.Product product) index.deleted(product.getId());
        else changed(event.getPersister());
    }
    @Override public void onPostInsertCommitFailed(PostInsertEvent event) { }
    @Override public void onPostUpdateCommitFailed(PostUpdateEvent event) { }
    @Override public void onPostDeleteCommitFailed(PostDeleteEvent event) { }
}
