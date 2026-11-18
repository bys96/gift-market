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
        ProductOptionService.class, ProductVariantService.class, ProductInventoryPolicy.class, ProductDescriptionSanitizer.class})
class ProductStockIntegrationTest {

    @Autowired ProductRegistrationService registrationService;
    @Autowired ProductService productService;
    @Autowired ProductModificationService modificationService;
    @Autowired ProductVariantService variantService;
    @Autowired ProductOptionService optionService;
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
    @MockitoBean GiftOccasionService giftOccasionService;

    private Long userId;
    private Long categoryId;
    private String imageKey;

    @Test
    void conversionRetainsDefaultIdentityAndStockWithoutAllocation() {
        Long id = register(50);
        ProductVariant original = variantRepository.findDefault(id).orElseThrow();
        Long variantId = original.getId();
        String sku = original.getSkuCode();
        var options = new ProductOptionUpdateRequest(List.of(new ProductOptionGroupRequest(null, "색상", 0,
                List.of(new ProductOptionValueRequest(null, "빨강", 0)))), true);
        var request = new ProductModificationRequest(request(999, "옵션 전환").product(), options,
                List.of(modificationVariant(null, "RED-NEW", 0, 7)), null);
        modificationService.modifyProduct(userId, id, request);
        entityManager.flush();
        entityManager.clear();
        ProductVariant retained = variantRepository.findDefault(id).orElseThrow();
        assertThat(retained.getId()).isEqualTo(variantId);
        assertThat(retained.getSkuCode()).isEqualTo(sku);
        assertThat(retained.getStockQuantity()).isEqualTo(50);
        assertThat(retained.isActive()).isFalse();
        assertStock(id, 7);
        assertThat(variantService.getProductVariants(userId, id).getVariants()).hasSize(1);
        assertThat(productService.getProduct(id).getVariants()).hasSize(1);
        assertThat(productService.getProduct(id).isHasOptions()).isTrue();
    }

    @Test
    void defaultStockUsesExistingVersionedEndpointAndIsHiddenFromOptionRows() {
        Long id = register(50);
        var before = productService.getMyProduct(userId, id);
        assertThat(before.isVariantManaged()).isFalse();
        assertThat(productService.getProduct(id).isHasOptions()).isFalse();
        assertThat(productService.getProduct(id).getVariants()).isEmpty();
        assertThat(variantService.getProductVariants(userId, id).getVariants()).isEmpty();
        productService.updateProductStock(userId, id, new ProductStockUpdateRequest(20, before.getVersion()));
        entityManager.flush();
        assertThat(variantRepository.findDefault(id).orElseThrow().getStockQuantity()).isEqualTo(20);
        assertThatThrownBy(() -> productService.updateProductStock(userId, id,
                new ProductStockUpdateRequest(20, before.getVersion())))
                .isInstanceOf(com.giftmarket.product.exception.ProductConflictException.class);
    }

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
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId)).singleElement()
                .satisfies(v -> { assertThat(v.isDefaultVariant()).isTrue();
                    assertThat(v.getStockQuantity()).isEqualTo(productRepository.findById(productId).orElseThrow().getStockQuantity()); });
    }

    @Test
    void sellerCodeChangesPreserveIdentitySystemSkuAndStockAcrossActivation() {
        Long productId = registerOptionProduct(7, 9);
        ProductVariant original = variantRepository.findAllByProductIdOrderByIdAsc(productId).getFirst();
        Long id = original.getId();
        String sku = original.getSkuCode();
        assertThat(sku).startsWith("GM-");
        UUID.fromString(sku.substring(3));
        List<Long> values = mappingRepository.findAllByVariantIdIn(List.of(id)).stream()
                .map(mapping -> mapping.getOptionValue().getId()).toList();
        variantService.updateProductVariants(userId, productId, new ProductVariantUpdateRequest(List.of(
                new ProductVariantRequest(id, " f1 ", values, 0L, 7, false))));
        entityManager.flush();
        entityManager.clear();
        ProductVariant inactive = variantRepository.findById(id).orElseThrow();
        assertThat(inactive.isActive()).isFalse();
        assertThat(inactive.getSellerSkuCode()).isEqualTo("F1");
        assertThat(inactive.getSkuCode()).isEqualTo(sku);
        variantService.updateProductVariants(userId, productId, new ProductVariantUpdateRequest(List.of(
                new ProductVariantRequest(id, "red", values, 0L, 7, true))));
        entityManager.flush();
        entityManager.clear();
        ProductVariant active = variantRepository.findById(id).orElseThrow();
        assertThat(active.isActive()).isTrue();
        assertThat(active.getSkuCode()).isEqualTo(sku);
        assertThat(active.getStockQuantity()).isEqualTo(7);
        assertThat(active.getSellerSkuCode()).isEqualTo("RED");
    }

    @Test
    void sameSellerCodeAcrossProductsGetsDifferentSystemSkus() {
        Long firstId = registerOptionProduct(3, 4);
        Long secondId = registerOptionProduct(5, 6);
        ProductVariant first = variantRepository.findAllByProductIdOrderByIdAsc(firstId).getFirst();
        ProductVariant second = variantRepository.findAllByProductIdOrderByIdAsc(secondId).getFirst();
        first.update(" f1 ", first.getCombinationKey(), 0L);
        second.update("F1", second.getCombinationKey(), 0L);
        entityManager.flush();
        assertThat(first.getSellerSkuCode()).isEqualTo(second.getSellerSkuCode());
        assertThat(first.getSkuCode()).isNotEqualTo(second.getSkuCode());
    }

    @Test
    void inactiveSellerCodeCannotBeAssignedToAnotherVariant() {
        Long productId = registerOptionProduct(3, 4);
        List<ProductVariant> variants = variantRepository.findAllByProductIdOrderByIdAsc(productId);
        ProductVariant first = variants.getFirst();
        ProductVariant second = variants.getLast();
        first.deactivate();
        entityManager.flush();
        List<Long> values = mappingRepository.findAllByVariantIdIn(List.of(second.getId())).stream()
                .map(mapping -> mapping.getOptionValue().getId()).toList();
        assertThatThrownBy(() -> variantService.updateProductVariants(userId, productId,
                new ProductVariantUpdateRequest(List.of(new ProductVariantRequest(second.getId(),
                        first.getSellerSkuCode().toLowerCase(), values, 0L, 4, true)))))
                .isInstanceOf(ProductException.class);
    }

    @Test
    void changingExistingOptionCombinationIsStillRejected() {
        Long productId = registerOptionProduct(3, 4);
        List<ProductVariant> variants = variantRepository.findAllByProductIdOrderByIdAsc(productId);
        ProductVariant first = variants.getFirst();
        List<Long> otherValues = mappingRepository.findAllByVariantIdIn(List.of(variants.getLast().getId()))
                .stream().map(mapping -> mapping.getOptionValue().getId()).toList();
        assertThatThrownBy(() -> variantService.updateProductVariants(userId, productId,
                new ProductVariantUpdateRequest(List.of(new ProductVariantRequest(first.getId(),
                        first.getSellerSkuCode(), otherValues, 0L, 3, true)))))
                .isInstanceOf(ProductException.class).hasMessageContaining("옵션 조합");
    }

    @Test
    void modifiesOptionlessStockFromFiftyToTwenty() {
        Long productId = register(50);

        var response = modificationService.modifyProduct(userId, productId, stockRequest(productId, 20, "수정 상품"));

        assertThat(response.getStockQuantity()).isEqualTo(20);
        assertStock(productId, 20);
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId)).singleElement()
                .satisfies(v -> { assertThat(v.isDefaultVariant()).isTrue();
                    assertThat(v.getStockQuantity()).isEqualTo(productRepository.findById(productId).orElseThrow().getStockQuantity()); });
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
        modificationService.modifyProduct(userId, productId, stockRequest(productId, 0, "품절 상품"));
        assertStock(productId, 0);

        modificationService.modifyProduct(userId, productId, stockRequest(productId, 10, "재입고 상품"));
        assertStock(productId, 10);
    }

    @Test
    void addingOptionsReplacesDirectStockWithVariantSum() {
        Long productId = registerOptionProduct(10, 20);

        assertStock(productId, 30);
        assertThat(variantRepository.findAllByProductIdAndActiveTrueOrderByIdAsc(productId)).hasSize(2);
    }

    @Test
    void modifyingOptionProductUsesActiveVariantSum() {
        Long productId = registerOptionProduct(1, 2);

        modificationService.modifyProduct(userId, productId, optionRequest(productId, 10, 20));

        assertStock(productId, 30);
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId))
                .extracting(ProductVariant::getStockQuantity).containsExactly(10, 20);
    }

    @Test
    void changingOneVariantStockSynchronizesTotal() {
        Long productId = registerOptionProduct(10, 20);

        variantService.updateProductVariants(userId, productId, variantRequest(productId, 5, 20));

        assertStock(productId, 25);
    }

    @Test
    void inactiveVariantStockIsExcludedFromTotal() {
        Long productId = registerOptionProduct(10, 20);
        List<ProductVariantRequest> variants = variantRequest(productId, 10, 20).variants();
        ProductVariantRequest second = variants.get(1);

        variantService.updateProductVariants(userId, productId, new ProductVariantUpdateRequest(List.of(
                variants.getFirst(), new ProductVariantRequest(second.id(), second.sellerSkuCode(),
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
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId)).singleElement()
                .satisfies(v -> { assertThat(v.isDefaultVariant()).isTrue();
                    assertThat(v.getStockQuantity()).isEqualTo(productRepository.findById(productId).orElseThrow().getStockQuantity()); });
    }

    @Test
    void removingOptionsThroughApiPreservesVariantRowsAndRequestedDirectStock() {
        Long productId = registerOptionProduct(10, 20);
        List<Long> variantIds = variantRepository.findAllByProductIdOrderByIdAsc(productId).stream()
                .map(ProductVariant::getId).toList();

        modificationService.modifyProduct(userId, productId, request(30, "옵션 전체 보관"));

        assertStock(productId, 0);
        assertThat(groupRepository.findAllByProductIdOrderBySortOrderAsc(productId)).isEmpty();
        assertThat(mappingRepository.findAllByVariantIdIn(variantIds)).hasSize(2);
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId))
                .extracting(ProductVariant::getId).containsExactlyElementsOf(variantIds);
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId))
                .allMatch(variant -> !variant.isActive());
    }

    @Test
    void emptyVariantListWithOptionsDeactivatesRowsAndSynchronizesZero() {
        Long productId = registerOptionProduct(10, 20);

        variantService.updateProductVariants(userId, productId, new ProductVariantUpdateRequest(List.of()));

        assertStock(productId, 0);
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(productId))
                .hasSize(2).allMatch(variant -> !variant.isActive());
    }

    @Test
    void modificationWithOptionsRejectsEmptyVariantsAndRollsBackBasicUpdate() {
        Long productId = registerOptionProduct(10, 20);
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

    private ProductModificationRequest stockRequest(Long productId, int stock, String name) {
        entityManager.flush();
        ProductUpdateRequest base = request(stock, name).product();
        ProductUpdateRequest update = new ProductUpdateRequest(base.categoryId(), base.name(), base.brandName(),
                base.summary(), base.description(), base.price(), base.stockQuantity(), base.representativeImageKey(),
                base.galleryImageKeys(), base.freeShipping(), base.shippingFee(), base.shippingPreparationDays(),
                base.returnShippingFee(), base.exchangeShippingFee(), productRepository.findById(productId).orElseThrow().getVersion());
        return new ProductModificationRequest(update, new ProductOptionUpdateRequest(List.of()), List.of(), null);
    }

    @Test
    void metadataChangesNeverOverwriteStockAfterOrderCancellationOrReservation() {
        for (int delta : List.of(-1, 1, -1)) {
            Long productId = registerOptionProduct(10, 0);
            ProductVariant variant = variantRepository.findAllByProductIdOrderByIdAsc(productId).getFirst();
            Long id = variant.getId();
            List<Long> values = mappingRepository.findAllByVariantIdIn(List.of(id)).stream()
                    .map(mapping -> mapping.getOptionValue().getId()).toList();
            if (delta < 0) variant.decreaseStock(1); else variant.increaseStock(1);
            entityManager.flush();
            entityManager.clear();
            variantService.updateProductVariants(userId, productId, new ProductVariantUpdateRequest(List.of(
                    new ProductVariantRequest(id, "EDITED", values, 0L, null, true))));
            entityManager.flush();
            assertThat(variantRepository.findById(id).orElseThrow().getStockQuantity()).isEqualTo(10 + delta);
        }
    }

    @Test
    void staleStockVersionIsRejectedEvenIfStockReturnsToOriginalQuantity() {
        Long productId = registerOptionProduct(10, 0);
        ProductVariant variant = variantRepository.findAllByProductIdOrderByIdAsc(productId).getFirst();
        Long oldVersion = variant.getVersion();
        Long id = variant.getId();
        List<Long> values = mappingRepository.findAllByVariantIdIn(List.of(id)).stream()
                .map(mapping -> mapping.getOptionValue().getId()).toList();
        variant.decreaseStock(1);
        entityManager.flush();
        variant.increaseStock(1);
        entityManager.flush();
        assertThatThrownBy(() -> variantService.updateProductVariants(userId, productId,
                new ProductVariantUpdateRequest(List.of(new ProductVariantRequest(id, "EDITED", values, 0L, 20, true, oldVersion)))))
                .isInstanceOf(com.giftmarket.product.exception.ProductConflictException.class);
        assertThat(variant.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void optionRemovalAndExplicitRestorationKeepMappingIdentitySkuAndStock() {
        Long productId = registerOptionProduct(7, 3);
        ProductModificationRequest original = optionRequest(productId, 7, 3);
        ProductVariant first = variantRepository.findAllByProductIdOrderByIdAsc(productId).getFirst();
        String sku = first.getSkuCode();
        Long id = first.getId();
        optionService.updateProductOptions(userId, productId, new ProductOptionUpdateRequest(List.of()));
        entityManager.flush();
        assertThat(mappingRepository.findAllByVariantIdIn(List.of(id))).hasSize(1);
        assertThat(first.isActive()).isFalse();
        assertThat(optionService.getProductOptions(userId, productId).getArchivedOptionGroups()).hasSize(1);
        optionService.updateProductOptions(userId, productId, original.options());
        variantService.updateProductVariants(userId, productId, new ProductVariantUpdateRequest(List.of(
                new ProductVariantRequest(id, first.getSellerSkuCode(), mappingRepository.findAllByVariantIdIn(List.of(id))
                        .stream().map(mapping -> mapping.getOptionValue().getId()).toList(), 0L, null, true))));
        entityManager.flush();
        entityManager.clear();
        ProductVariant restored = variantRepository.findById(id).orElseThrow();
        assertThat(restored.getSkuCode()).isEqualTo(sku);
        assertThat(restored.getStockQuantity()).isEqualTo(7);
        assertThat(restored.isActive()).isTrue();
    }

    @Test
    void connectedOptionCannotBeRenamedThroughSeparateApi() {
        Long productId = registerOptionProduct(0, 0);
        ProductOptionGroup group = groupRepository.findAllByProductIdOrderBySortOrderAsc(productId).getFirst();
        List<ProductOptionValue> values = valueRepository.findAllByOptionGroupIdOrderBySortOrderAsc(group.getId());
        assertThatThrownBy(() -> optionService.updateProductOptions(userId, productId,
                new ProductOptionUpdateRequest(List.of(new ProductOptionGroupRequest(group.getId(), group.getName(), 0,
                        List.of(new ProductOptionValueRequest(values.getFirst().getId(), "새 실물", 0),
                                new ProductOptionValueRequest(values.getLast().getId(), values.getLast().getValue(), 1)))))))
                .isInstanceOf(ProductException.class);
        assertThat(values.getFirst().getValue()).isEqualTo("빨강");
    }

    @Test
    void optionlessMetadataPreservesConcurrentStockAndVersionedEditConflicts() {
        Long productId = register(10);
        Product product = productRepository.findById(productId).orElseThrow();
        ProductModificationRequest staleStockEdit = stockRequest(productId, 20, "재고 수정");
        product.increaseStock(1);
        variantRepository.findDefault(productId).orElseThrow().increaseStock(1);
        entityManager.flush();
        entityManager.clear();
        modificationService.modifyProduct(userId, productId, request(10, "상품명 수정"));
        assertStock(productId, 11);
        assertThatThrownBy(() -> modificationService.modifyProduct(userId, productId, staleStockEdit))
                .isInstanceOf(com.giftmarket.product.exception.ProductConflictException.class);
    }

    @Test
    void optionNamesCanBeCorrectedOnlyBeforeAnyVariantHasUsedThem() {
        Long productId = productService.createProduct(userId, new ProductCreateRequest(categoryId, "등록 중 상품", null, null,
                "설명", 10_000L, 0, imageKey, List.of(), true, 0L, 3, 3_000L, 6_000L, false)).getId();
        var created = optionService.initializeProductOptions(userId, productId, new ProductOptionUpdateRequest(List.of(
                new ProductOptionGroupRequest(null, "색상", 0, List.of(new ProductOptionValueRequest(null, "빨갛", 0))))));
        var group = created.getOptionGroups().getFirst();
        var value = group.getValues().getFirst();
        var corrected = optionService.initializeProductOptions(userId, productId, new ProductOptionUpdateRequest(List.of(
                new ProductOptionGroupRequest(group.getId(), "색상", 0,
                        List.of(new ProductOptionValueRequest(value.getId(), "빨강", 0))))));
        assertThat(corrected.getOptionGroups().getFirst().getValues().getFirst().getValue()).isEqualTo("빨강");
    }

    @Test
    void physicalOptionChangeCreatesNewIdentityAndPreservesOldStock() {
        Long productId = registerOptionProduct(7, 3);
        ProductModificationRequest original = optionRequest(productId, 7, 3);
        var group = original.options().optionGroups().getFirst();
        ProductVariant old = variantRepository.findAllByProductIdOrderByIdAsc(productId).getFirst();
        Long oldId = old.getId();
        String oldSku = old.getSkuCode();
        var options = new ProductOptionUpdateRequest(List.of(new ProductOptionGroupRequest(group.id(), group.name(), 0,
                List.of(group.values().getLast(), new ProductOptionValueRequest(null, "초록", 0)))));
        modificationService.modifyProduct(userId, productId, new ProductModificationRequest(original.product(), options,
                List.of(modificationVariant(null, "GREEN-NEW", 0, 5)), null));
        entityManager.flush();
        entityManager.clear();
        ProductVariant retained = variantRepository.findById(oldId).orElseThrow();
        assertThat(retained.getSkuCode()).isEqualTo(oldSku);
        assertThat(retained.getStockQuantity()).isEqualTo(7);
        assertThat(retained.isActive()).isFalse();
        ProductVariant fresh = variantRepository.findByProductIdAndSellerSkuCode(productId, "GREEN-NEW").orElseThrow();
        assertThat(fresh.getId()).isNotEqualTo(oldId);
        assertThat(fresh.getSkuCode()).isNotEqualTo(oldSku);
        assertThat(mappingRepository.findAllByVariantIdIn(List.of(oldId))).hasSize(1);
    }

    @Test
    void existingPlainProductCannotAcquireOptionsEvenWithZeroStock() {
        Long id = register(0);
        assertThatThrownBy(() -> optionService.updateProductOptions(userId, id, new ProductOptionUpdateRequest(List.of(
                new ProductOptionGroupRequest(null, "색상", 0, List.of(new ProductOptionValueRequest(null, "빨강", 0)))))))
                .isInstanceOf(com.giftmarket.product.exception.ProductConflictException.class);
        assertThat(variantRepository.findDefault(id)).isPresent();
    }

    @Test
    void archivedVariantProductCannotChangeDirectStockOrItsInventoryOwner() {
        Long id = registerOptionProduct(0, 0);
        optionService.updateProductOptions(userId, id, new ProductOptionUpdateRequest(List.of()));
        assertThat(productService.getMyProduct(userId, id).isVariantManaged()).isTrue();
        assertThatThrownBy(() -> modificationService.modifyProduct(userId, id, stockRequest(id, 10, "방식 변경")))
                .isInstanceOf(com.giftmarket.product.exception.ProductConflictException.class);
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(id)).hasSize(2);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {2, 3})
    void multiGroupRemovalRestorationAndReorderReuseOriginalIdentity(int count) {
        Long id = registerMultiGroup(count);
        ProductVariant original = variantRepository.findAllByProductIdOrderByIdAsc(id).getFirst();
        Long variantId = original.getId();
        String sku = original.getSkuCode();
        String key = original.getCombinationKey();
        var groups = optionService.getProductOptions(userId, id).getOptionGroups();
        int removedIndex = count == 2 ? 0 : 1;
        var kept = java.util.stream.IntStream.range(0, count).filter(i -> i != removedIndex)
                .mapToObj(i -> groupRequest(groups.get(i), i)).toList();
        optionService.updateProductOptions(userId, id, new ProductOptionUpdateRequest(kept));
        assertThat(original.isActive()).isFalse();
        var reversed = java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> groupRequest(groups.get(count - 1 - i), i)).toList();
        optionService.updateProductOptions(userId, id, new ProductOptionUpdateRequest(reversed));
        var ids = mappingRepository.findAllByVariantIdIn(List.of(variantId)).stream()
                .map(m -> m.getOptionValue().getId()).sorted(java.util.Comparator.reverseOrder()).toList();
        variantService.updateProductVariants(userId, id, new ProductVariantUpdateRequest(List.of(
                new ProductVariantRequest(variantId, "MULTI", ids, 0L, null, true))));
        entityManager.flush();
        entityManager.clear();
        ProductVariant restored = variantRepository.findById(variantId).orElseThrow();
        assertThat(restored.getSkuCode()).isEqualTo(sku);
        assertThat(restored.getCombinationKey()).isEqualTo(key);
        assertThat(restored.getSellerSkuCode()).isEqualTo("MULTI");
        assertThat(restored.getStockQuantity()).isEqualTo(7);
        assertThat(variantRepository.findAllByProductIdOrderByIdAsc(id)).hasSize(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1})
    void missingMappingsIncludingPartialAndInactiveRowsBlockWrites(int remaining) {
        Long id = registerMultiGroup(2);
        ProductVariant variant = variantRepository.findAllByProductIdOrderByIdAsc(id).getFirst();
        variant.deactivate();
        var mappings = mappingRepository.findAllByVariantIdIn(List.of(variant.getId()));
        mappingRepository.deleteAll(mappings.subList(remaining, mappings.size()));
        entityManager.flush();
        entityManager.clear();
        assertThat(variantService.getProductVariants(userId, id).getVariants().getFirst().isMappingValid()).isFalse();
        assertThatThrownBy(() -> variantService.updateProductVariants(userId, id, new ProductVariantUpdateRequest(List.of())))
                .isInstanceOf(com.giftmarket.product.exception.ProductConflictException.class).hasMessageContaining("매핑");
        assertThat(mappingRepository.findAllByVariantIdIn(List.of(variant.getId()))).hasSize(remaining);
    }

    private ProductOptionGroupRequest groupRequest(com.giftmarket.product.dto.response.ProductOptionGroupResponse g, int sort) {
        return new ProductOptionGroupRequest(g.getId(), g.getName(), sort, g.getValues().stream()
                .map(v -> new ProductOptionValueRequest(v.getId(), v.getValue(), v.getSortOrder())).toList());
    }

    protected Long registerMultiGroup(int count) {
        return registrationService.registerProduct(userId, new ProductRegistrationRequest(
                new ProductCreateRequest(categoryId, "다중 옵션", null, null, "설명", 10_000L, 0,
                        imageKey, List.of(), true, 0L, 3, 3_000L, 6_000L, false),
                new ProductOptionUpdateRequest(java.util.stream.IntStream.range(0, count)
                        .mapToObj(i -> new ProductOptionGroupRequest(null, "그룹" + i, i,
                                List.of(new ProductOptionValueRequest(null, "값" + i, 0)))).toList()),
                List.of(new ProductRegistrationVariantRequest("MULTI", java.util.stream.IntStream.range(0, count)
                        .mapToObj(i -> new ProductOptionReferenceRequest(i, 0)).toList(), 0L, 7, true)), null)).productId();
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

    private Long registerOptionProduct(int firstStock, int secondStock) {
        return registrationService.registerProduct(userId, new ProductRegistrationRequest(
                new ProductCreateRequest(categoryId, "옵션 추가", null, null, "상품 설명", 10_000L,
                        0, imageKey, List.of(), true, 0L, 3, 3_000L, 6_000L, false),
                new ProductOptionUpdateRequest(List.of(new ProductOptionGroupRequest(null, "색상", 0,
                        List.of(new ProductOptionValueRequest(null, "빨강", 0), new ProductOptionValueRequest(null, "파랑", 1))))),
                List.of(new ProductRegistrationVariantRequest("STOCK-A", List.of(new ProductOptionReferenceRequest(0, 0)), 0L, firstStock, true),
                        new ProductRegistrationVariantRequest("STOCK-B", List.of(new ProductOptionReferenceRequest(0, 1)), 0L, secondStock, true)), null)).productId();
    }

    private ProductModificationRequest optionRequest(Long productId, int firstStock, int secondStock) {
        ProductOptionGroup group = groupRepository.findAllByProductIdOrderBySortOrderAsc(productId).getFirst();
        List<ProductOptionValue> values = valueRepository.findAllByOptionGroupIdOrderBySortOrderAsc(group.getId());
        List<ProductVariant> variants = variantRepository.findAllByProductIdOrderByIdAsc(productId);
        return new ProductModificationRequest(request(99, "옵션 수정").product(),
                new ProductOptionUpdateRequest(List.of(new ProductOptionGroupRequest(group.getId(), "색상", 0,
                        values.stream().map(value -> new ProductOptionValueRequest(
                                value.getId(), value.getValue(), value.getSortOrder())).toList()))),
                List.of(modificationVariant(variants.get(0).getId(), variants.get(0).getSellerSkuCode(), 0, firstStock),
                        modificationVariant(variants.get(1).getId(), variants.get(1).getSellerSkuCode(), 1, secondStock)), null);
    }

    private ProductModificationVariantRequest modificationVariant(Long id, String sku, int sort, int stock) {
        return new ProductModificationVariantRequest(id, sku,
                List.of(new ProductOptionReferenceRequest(0, sort)), 0L, stock, true,
                id == null ? null : variantRepository.findById(id).orElseThrow().getVersion());
    }

    private ProductVariantUpdateRequest variantRequest(Long productId, int firstStock, int secondStock) {
        List<ProductVariant> variants = variantRepository.findAllByProductIdOrderByIdAsc(productId);
        return new ProductVariantUpdateRequest(variants.stream().map(variant -> new ProductVariantRequest(
                variant.getId(), variant.getSellerSkuCode(),
                mappingRepository.findAllByVariantIdIn(List.of(variant.getId())).stream()
                        .map(mapping -> mapping.getOptionValue().getId()).toList(),
                0L, variant.getId().equals(variants.getFirst().getId()) ? firstStock : secondStock, true, variant.getVersion()
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
