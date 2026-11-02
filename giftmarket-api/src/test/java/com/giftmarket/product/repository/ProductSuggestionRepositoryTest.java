package com.giftmarket.product.repository;

import com.giftmarket.product.entity.Category;
import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = {
        "spring.config.location=optional:classpath:/suggest-test.properties",
        "spring.datasource.url=jdbc:h2:mem:suggest;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductSuggestionRepositoryTest {
    @Autowired ProductRepository products;
    @Autowired SellerRepository sellers;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;

    @Test
    void onlyVisibleInStockProductsAreReturnedAndResultsAreLimited() {
        var user = users.save(User.createOAuthUser("seller@example.test", "판매자", null, AuthProvider.GOOGLE, UUID.randomUUID().toString()));
        var seller = sellers.save(Seller.create(user, "상점", ""));
        var suspendedUser = users.save(User.createOAuthUser("other@example.test", "판매자2", null, AuthProvider.GOOGLE, UUID.randomUUID().toString()));
        var suspended = sellers.save(Seller.create(suspendedUser, "정지 상점", ""));
        suspended.suspend();
        var category = categories.save(Category.create(null, "선물", 0));
        for (int i = 0; i < 7; i++) products.save(product(seller, category, "초콜릿 " + i, 2, true));
        products.save(product(seller, category, "초콜릿 품절", 0, true));
        products.save(product(seller, category, "초콜릿 미판매", 2, false));
        products.save(product(suspended, category, "초콜릿 정지", 2, true));
        var hidden = product(seller, category, "초콜릿 숨김", 2, true);
        hidden.hideByAdmin("test"); products.save(hidden);
        var deleted = product(seller, category, "초콜릿 삭제", 2, true);
        deleted.softDelete(); products.save(deleted);
        products.flush();
        var all = products.findSuggestionProducts("초콜릿", PageRequest.of(0, 20));
        assertThat(all).hasSize(7).allMatch(p -> p.getName().matches("초콜릿 [0-6]"));
        assertThat(products.findSuggestionProducts("초콜릿", PageRequest.of(0, 5))).hasSize(5);
    }

    private static Product product(Seller seller, Category category, String name, int stock, boolean sale) {
        Product product = Product.createDraft(seller, category, name, null, null, null, 1000L, stock,
                "image.jpg", true, 0L, 1, 0L, 0L);
        if (sale) product.startSale();
        return product;
    }
}
