package com.giftmarket.product.service;

import com.giftmarket.product.draft.service.ProductDraftService;
import com.giftmarket.product.dto.request.*;
import com.giftmarket.product.entity.*;
import com.giftmarket.product.exception.ProductException;
import com.giftmarket.product.repository.*;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = {
        "spring.config.location=optional:classpath:/product-stock-test.properties",
        "spring.datasource.url=jdbc:h2:mem:product-stock;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProductService.class, ProductRegistrationService.class, ProductModificationService.class,
        ProductOptionService.class, ProductVariantService.class, ProductDescriptionSanitizer.class})
class ProductStockIntegrationTest {

    @Autowired ProductRegistrationService registrationService;
    @Autowired ProductModificationService modificationService;
    @Autowired ProductVariantService variantService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired ProductOptionGroupRepository groupRepository;
    @Autowired ProductOptionValueRepository valueRepository;
    @Autowired ProductVariantOptionValueRepository mappingRepository;
    @Autowired UserRepository userRepository;
    @Autowired SellerRepository sellerRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired EntityManager entityManager;
    @MockitoBean ProductDraftService draftService;

    private Long userId;
    private Long categoryId;
    private String imageKey;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.createOAuthUser(
                UUID.randomUUID() + "@example.test", "재고 판매자", null, AuthProvider.GOOGLE, UUID.randomUUID().toString()
        ));
        Seller seller = sellerRepository.save(Seller.create(user, "재고 상점", "테스트"));
        userId = user.getId();
        categoryId = categoryRepository.save(Category.create(null, "재고 카테고리", 0)).getId();
        imageKey = "products/" + seller.getId() + "/representative/stock.jpg";
    }

    @Test
    void registersOptionlessProductWithFiftyStock() {
        Long productId = register(50);

        assertStock(productId, 50);
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId)).isEmpty();
    }

    @Test
    void modifiesOptionlessStockFromFiftyToTwenty() {
        Long productId = register(50);

        var response = modificationService.modifyProduct(userId, productId, request(20, "수정 상품"));

        assertThat(response.getStockQuantity()).isEqualTo(20);
        assertStock(productId, 20);
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId)).isEmpty();
    }

    @Test
    void editingOtherFieldsPreservesRequiredStockInRequest() {
        Long productId = register(30);

        modificationService.modifyProduct(userId, productId, request(30, "이름 변경"));

        assertStock(productId, 30);
        assertThat(productRepository.findById(productId).orElseThrow().getName()).isEqualTo("이름 변경");
    }

    @Test
    void zeroStockAndRestockingKeepSaleStatusConsistent() {
        Long productId = register(50);
        modificationService.modifyProduct(userId, productId, request(0, "품절 상품"));
        assertStock(productId, 0);

        modificationService.modifyProduct(userId, productId, request(10, "재입고 상품"));
        assertStock(productId, 10);
    }

    @Test
    void addingOptionsReplacesDirectStockWithVariantSum() {
        Long productId = register(50);
        addOptions(productId, 10, 20);

        assertStock(productId, 30);
        assertThat(variantRepository.findAllByProductIdAndActiveTrueOrderByIdAsc(productId)).hasSize(2);
    }

    @Test
    void modifyingOptionProductUsesActiveVariantSum() {
        Long productId = register(50);
        addOptions(productId, 1, 2);

        modificationService.modifyProduct(userId, productId, optionRequest(productId, 10, 20));

        assertStock(productId, 30);
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId))
                .extracting(ProductVariant::getStockQuantity).containsExactly(10, 20);
    }

    @Test
    void changingOneVariantStockSynchronizesTotal() {
        Long productId = register(50);
        addOptions(productId, 10, 20);

        variantService.updateProductVariants(userId, productId, variantRequest(productId, 5, 20));

        assertStock(productId, 25);
    }

    @Test
    void inactiveVariantStockIsExcludedFromTotal() {
        Long productId = register(50);
        addOptions(productId, 10, 20);
        List<ProductVariantRequest> variants = variantRequest(productId, 10, 20).variants();
        ProductVariantRequest second = variants.get(1);

        variantService.updateProductVariants(userId, productId, new ProductVariantUpdateRequest(List.of(
                variants.getFirst(), new ProductVariantRequest(second.id(), second.skuCode(),
                second.optionValueIds(), second.additionalPrice(), second.stockQuantity(), false)
        )));

        assertStock(productId, 10);
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId)).hasSize(2);
    }

    @Test
    void emptyVariantListWithoutOptionsPreservesDirectStock() {
        Long productId = register(30);

        variantService.updateProductVariants(userId, productId, new ProductVariantUpdateRequest(List.of()));

        assertStock(productId, 30);
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId)).isEmpty();
    }

    @Test
    void removingOptionsThroughApiPreservesVariantRowsAndRequestedDirectStock() {
        Long productId = register(50);
        addOptions(productId, 10, 20);
        List<Long> variantIds = variantRepository.findAllByProductIdOrderByIdAsc(productId).stream()
                .map(ProductVariant::getId).toList();

        modificationService.modifyProduct(userId, productId, request(20, "일반 상품 전환"));

        assertStock(productId, 20);
        assertThat(groupRepository.findAllByProductIdOrderBySortOrderAsc(productId)).isEmpty();
        assertThat(mappingRepository.findAllByVariantIdIn(variantIds)).isEmpty();
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId))
                .extracting(ProductVariant::getId).containsExactlyElementsOf(variantIds);
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId))
                .allMatch(variant -> !variant.isActive());
    }

    @Test
    void emptyVariantListWithOptionsDeactivatesRowsAndSynchronizesZero() {
        Long productId = register(50);
        addOptions(productId, 10, 20);

        variantService.updateProductVariants(userId, productId, new ProductVariantUpdateRequest(List.of()));

        assertStock(productId, 0);
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId))
                .hasSize(2).allMatch(variant -> !variant.isActive());
    }

    @Test
    void modificationWithOptionsRejectsEmptyVariantsAndRollsBackBasicUpdate() {
        Long productId = register(50);
        addOptions(productId, 10, 20);
        ProductModificationRequest valid = optionRequest(productId, 10, 20);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        assertThatThrownBy(() -> modificationService.modifyProduct(userId, productId,
                new ProductModificationRequest(valid.product(), valid.options(), List.of(), null)))
                .isInstanceOf(ProductException.class);

        TestTransaction.end();
        TestTransaction.start();
        assertStock(productId, 30);
        assertThat(productRepository.findById(productId).orElseThrow().getName()).isEqualTo("옵션 추가");
        // 커밋된 fixture는 이 테스트 전용 내장 DB에만 존재한다.
    }

    private Long register(int stock) {
        return registrationService.registerProduct(userId, new ProductRegistrationRequest(
                new ProductCreateRequest(categoryId, "재고 상품", null, null, "상품 설명", 10_000L,
                        stock, imageKey, List.of(), true, 0L, 3, 3_000L, 6_000L, false),
                new ProductOptionUpdateRequest(List.of()), List.of(), null
        )).productId();
    }

    private ProductModificationRequest request(int stock, String name) {
        return new ProductModificationRequest(
                new ProductUpdateRequest(categoryId, name, null, null, "수정 설명", 12_000L,
                        stock, imageKey, List.of(), true, 0L, 3, 3_000L, 6_000L),
                new ProductOptionUpdateRequest(List.of()), List.of(), null
        );
    }

    private void addOptions(Long productId, int firstStock, int secondStock) {
        modificationService.modifyProduct(userId, productId, new ProductModificationRequest(
                request(99, "옵션 추가").product(),
                new ProductOptionUpdateRequest(List.of(new ProductOptionGroupRequest(null, "색상", 0,
                        List.of(new ProductOptionValueRequest(null, "빨강", 0),
                                new ProductOptionValueRequest(null, "파랑", 1))))),
                List.of(modificationVariant(null, "STOCK-A-" + productId, 0, firstStock),
                        modificationVariant(null, "STOCK-B-" + productId, 1, secondStock)), null
        ));
    }

    private ProductModificationRequest optionRequest(Long productId, int firstStock, int secondStock) {
        ProductOptionGroup group = groupRepository.findAllByProductIdOrderBySortOrderAsc(productId).getFirst();
        List<ProductOptionValue> values = valueRepository.findAllByOptionGroupIdOrderBySortOrderAsc(group.getId());
        List<ProductVariant> variants = variantRepository.findAllByProductIdOrderByIdAsc(productId);
        return new ProductModificationRequest(request(99, "옵션 수정").product(),
                new ProductOptionUpdateRequest(List.of(new ProductOptionGroupRequest(group.getId(), "색상", 0,
                        values.stream().map(value -> new ProductOptionValueRequest(
                                value.getId(), value.getValue(), value.getSortOrder())).toList()))),
                List.of(modificationVariant(variants.get(0).getId(), variants.get(0).getSkuCode(), 0, firstStock),
                        modificationVariant(variants.get(1).getId(), variants.get(1).getSkuCode(), 1, secondStock)), null);
    }

    private ProductModificationVariantRequest modificationVariant(Long id, String sku, int sort, int stock) {
        return new ProductModificationVariantRequest(id, sku,
                List.of(new ProductOptionReferenceRequest(0, sort)), 0L, stock, true);
    }

    private ProductVariantUpdateRequest variantRequest(Long productId, int firstStock, int secondStock) {
        List<ProductVariant> variants = variantRepository.findAllByProductIdOrderByIdAsc(productId);
        return new ProductVariantUpdateRequest(variants.stream().map(variant -> new ProductVariantRequest(
                variant.getId(), variant.getSkuCode(),
                mappingRepository.findAllByVariantIdIn(List.of(variant.getId())).stream()
                        .map(mapping -> mapping.getOptionValue().getId()).toList(),
                0L, variant.getId().equals(variants.getFirst().getId()) ? firstStock : secondStock, true
        )).toList());
    }

    private void assertStock(Long productId, int expected) {
        entityManager.flush();
        entityManager.clear();
        Product saved = productRepository.findById(productId).orElseThrow();
        assertThat(saved.getStockQuantity()).isEqualTo(expected);
        assertThat(saved.getStatus()).isEqualTo(expected == 0 ? ProductStatus.SOLD_OUT : ProductStatus.ON_SALE);
    }
}
