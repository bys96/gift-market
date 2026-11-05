package com.giftmarket.product.search;

import com.giftmarket.product.entity.Category;
import com.giftmarket.product.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {
        "spring.config.location=optional:classpath:/meili-commit-test.properties", "search.engine=meilisearch",
        "spring.datasource.url=jdbc:h2:mem:meili_commit;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MeiliCommitListener.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MeiliCommitIntegrationTest {
    @MockitoBean MeiliIndexService index;
    @Autowired CategoryRepository categories;
    @Autowired PlatformTransactionManager transactions;
    @Autowired com.giftmarket.product.repository.ProductRepository products;
    @Autowired com.giftmarket.user.repository.UserRepository users;
    @Autowired com.giftmarket.seller.repository.SellerRepository sellers;

    @Test void productInsertUpdateAndDeleteAreObservedOnlyAfterCommit() {
        var tx = new TransactionTemplate(transactions);
        Long id = tx.execute(status -> {
            var user = users.save(com.giftmarket.user.entity.User.createOAuthUser("commit@example.test", "seller", null,
                    com.giftmarket.user.entity.AuthProvider.GOOGLE, "commit"));
            var seller = sellers.save(com.giftmarket.seller.entity.Seller.create(user, "shop", ""));
            var category = categories.save(Category.create(null, "Bedding", 2));
            var product = products.saveAndFlush(com.giftmarket.product.entity.Product.createDraft(seller, category,
                    "Fabric", null, "Sleep", "Actual description", 1000L, 1, "image.jpg", true, 0L, 1, 0L, 0L));
            verifyNoInteractions(index);
            return product.getId();
        });
        verify(index, atLeastOnce()).changed();
        clearInvocations(index);
        tx.executeWithoutResult(status -> products.findById(id).orElseThrow().startSale());
        verify(index).changed();
        clearInvocations(index);
        tx.executeWithoutResult(status -> products.deleteById(id));
        verify(index).deleted(id);
    }
    @Test void invalidatesOnlyAfterSuccessfulCommitAndNeverOnRollback() {
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            categories.saveAndFlush(Category.create(null, "Before commit", 0));
            verifyNoInteractions(index);
        });
        verify(index).changed();
        clearInvocations(index);
        tx.executeWithoutResult(status -> {
            categories.saveAndFlush(Category.create(null, "Rollback", 1));
            status.setRollbackOnly();
        });
        verifyNoInteractions(index);
    }
}
