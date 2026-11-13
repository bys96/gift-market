package com.giftmarket.product.repository;

import com.giftmarket.product.entity.Category;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = {
        "spring.config.location=optional:classpath:/seller-product-search-test.properties",
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:seller_product_search;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SellerProductSearchRepositoryTest {
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired SellerRepository sellers;
    private Seller seller;
    private Category category;

    @BeforeEach
    void setUp() {
        seller = seller("first");
        Seller other = seller("second");
        category = categories.save(Category.create(null, "테스트", 0));
        product(seller, "새집 디퓨저 플로럴", ProductStatus.ON_SALE);
        product(seller, "디퓨저 머스크", ProductStatus.HIDDEN);
        product(seller, "DIFFUSER mini", ProductStatus.ON_SALE);
        product(seller, "할인 100%", ProductStatus.ON_SALE);
        product(seller, "선물_세트", ProductStatus.ON_SALE);
        product(seller, "경로\\옵션", ProductStatus.ON_SALE);
        Product deleted = product(seller, "삭제 디퓨저", ProductStatus.ON_SALE);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        products.saveAndFlush(deleted);
        product(other, "다른 판매자 디퓨저", ProductStatus.ON_SALE);
    }

    @Test
    void matchesFullNameAndSubstringWithoutLeakingOtherSellersOrDeletedProducts() {
        var page = PageRequest.of(0, 20);
        assertThat(products.findAllBySellerIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(
                seller.getId(), "새집 디퓨저 플로럴", page).getContent()).extracting(Product::getName)
                .containsExactly("새집 디퓨저 플로럴");
        assertThat(products.findAllBySellerIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(
                seller.getId(), "디퓨저", page).getContent()).extracting(Product::getName)
                .containsExactlyInAnyOrder("새집 디퓨저 플로럴", "디퓨저 머스크");
    }

    @Test
    void matchesCaseInsensitivelyAndReturnsAnEmptyPageForMissingNames() {
        var page = PageRequest.of(0, 20);
        assertThat(products.findAllBySellerIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(
                seller.getId(), "diffuser", page).getTotalElements()).isEqualTo(1);
        assertThat(products.findAllBySellerIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(
                seller.getId(), "없는 상품", page).getTotalElements()).isZero();
    }

    @Test
    void combinesStatusAndKeywordWithAccuratePageCounts() {
        var allStatuses = products.findAllBySellerIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(
                seller.getId(), "디퓨저", PageRequest.of(0, 1, Sort.by("id")));
        assertThat(allStatuses.getTotalElements()).isEqualTo(2);
        assertThat(allStatuses.getTotalPages()).isEqualTo(2);
        var second = products.findAllBySellerIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(
                seller.getId(), "디퓨저", PageRequest.of(1, 1, Sort.by("id")));
        assertThat(second.getContent()).extracting(Product::getName).containsExactly("디퓨저 머스크");
        var filtered = products.findAllBySellerIdAndStatusAndNameContainingIgnoreCaseAndDeletedAtIsNull(
                seller.getId(), ProductStatus.ON_SALE, "디퓨저", PageRequest.of(0, 1));
        assertThat(filtered.getTotalElements()).isEqualTo(1);
        assertThat(filtered.getTotalPages()).isEqualTo(1);
    }

    @Test
    void treatsPercentUnderscoreAndEscapeCharacterAsLiteralText() {
        var page = PageRequest.of(0, 20);
        for (String keyword : new String[]{"%", "_", "\\"}) {
            var result = products.findAllBySellerIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(
                    seller.getId(), keyword, page);
            assertThat(result.getContent()).singleElement().satisfies(product -> assertThat(product.getName()).contains(keyword));
        }
        assertThat(products.findAllBySellerIdAndStatusAndNameContainingIgnoreCaseAndDeletedAtIsNull(
                seller.getId(), ProductStatus.ON_SALE, "%", page).getTotalElements()).isEqualTo(1);
    }

    private Seller seller(String name) {
        User user = users.save(User.createOAuthUser(name + "@example.test", name, null, AuthProvider.GOOGLE, name));
        return sellers.save(Seller.create(user, name, ""));
    }

    private Product product(Seller owner, String name, ProductStatus status) {
        Product product = Product.createDraft(owner, category, name, null, "브랜드", "설명", 25_000L, 10,
                "test.jpg", true, 0L, 3, 3_000L, 6_000L);
        product.changeStatus(status);
        return products.saveAndFlush(product);
    }
}
