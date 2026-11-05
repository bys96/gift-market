package com.giftmarket.product.search;

import com.giftmarket.product.entity.*;
import com.giftmarket.product.repository.*;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.*;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.giftmarket.product.service.GiftOccasionService;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {
    "spring.config.location=optional:classpath:/keyword-test.properties",
    "spring.datasource.url=jdbc:h2:mem:keyword;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SearchProductReader.class)
class KeywordSearchIntegrationTest {
    @Autowired ProductRepository products;
    @Autowired SellerRepository sellers;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired SearchProductReader reader;
    @MockitoBean GiftOccasionService occasions;

    @Test
    void databaseKeywordFiltersOrderingPaginationAndSuggestionsWorkWithoutApiKey() {
        var user = users.save(User.createOAuthUser("keyword@example.test", "seller", null, AuthProvider.GOOGLE, "keyword"));
        var seller = sellers.save(Seller.create(user, "shop", ""));
        var bedding = categories.save(Category.create(null, "bedding", 0));
        var other = categories.save(Category.create(null, "other", 1));
        save(seller, bedding, "Sleep Fabric Set", 3000L);
        save(seller, bedding, "Sleep Blanket", 1000L);
        save(seller, other, "Sleep Pillow", 2000L);
        products.flush();
        assertThat(products.findAll(ProductSpecifications.nameContains("Sleep Fabric Set"))).hasSize(1);
        assertThat(products.findAll(ProductSpecifications.nameContains("sleep"))).hasSize(3);
        assertThat(products.findAll(ProductSpecifications.nameContains("missing"))).isEmpty();
        var filtered = Specification.where(ProductSpecifications.nameContains("Sleep"))
                .and(ProductSpecifications.categoryIdIn(java.util.List.of(bedding.getId())))
                .and(ProductSpecifications.priceAtLeast(1500L)).and(ProductSpecifications.priceAtMost(3500L));
        assertThat(products.findAll(filtered)).extracting(Product::getName).containsExactly("Sleep Fabric Set");
        var first = products.findAll(ProductSpecifications.nameContains("Sleep"), PageRequest.of(0, 2, Sort.by("price")));
        assertThat(first.getContent()).extracting(Product::getPrice).containsExactly(1000L, 2000L);
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(products.findAll(ProductSpecifications.nameContains("Sleep"), PageRequest.of(1, 2, Sort.by("price")))
                .getContent()).extracting(Product::getPrice).containsExactly(3000L);
        assertThat(products.findSuggestionProducts("Sleep", PageRequest.of(0, 5))).hasSize(3);
        var ids = products.findAll().stream().map(Product::getId).toList();
        when(occasions.getActiveProductGiftOccasions(org.mockito.ArgumentMatchers.<java.util.List<Long>>any())).thenReturn(java.util.Map.of());
        var condition = new com.giftmarket.product.dto.request.ProductSearchCondition(java.util.List.of(bedding.getId()), null,
                "engine-only-term", 500L, 3500L, ProductSort.PRICE_ASC, false, 1, 1);
        var second = reader.read(ids, condition, false);
        assertThat(second.totalElements()).isEqualTo(2);
        assertThat(second.products()).extracting(p -> p.getPrice()).containsExactly(3000L);
        for (var sort : ProductSort.values()) {
            var sorted = new com.giftmarket.product.dto.request.ProductSearchCondition(java.util.List.of(), null,
                    "engine-term", null, null, sort, false, 0, 10);
            assertThat(reader.read(ids, sorted, false).totalElements()).isEqualTo(3);
        }
        var hidden = products.findById(ids.getFirst()).orElseThrow();
        hidden.hideByAdmin("test"); products.flush();
        assertThat(reader.read(ids, condition, false).totalElements()).isEqualTo(1);
    }

    private void save(Seller seller, Category category, String name, long price) {
        var product = Product.createDraft(seller, category, name, null, null, null, price, 2,
                "image.jpg", true, 0L, 1, 0L, 0L);
        product.startSale();
        products.save(product);
    }
}
