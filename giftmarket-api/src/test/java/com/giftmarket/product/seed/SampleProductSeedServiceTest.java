package com.giftmarket.product.seed;

import com.giftmarket.global.storage.provider.StorageProvider;
import com.giftmarket.product.config.CategoryDataInitializer;
import com.giftmarket.product.draft.service.ProductDraftService;
import com.giftmarket.product.entity.GiftOccasion;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.repository.CategoryRepository;
import com.giftmarket.product.repository.GiftOccasionRepository;
import com.giftmarket.product.repository.ProductGiftOccasionRepository;
import com.giftmarket.product.repository.ProductImageRepository;
import com.giftmarket.product.repository.ProductOptionGroupRepository;
import com.giftmarket.product.repository.ProductOptionValueRepository;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.product.repository.ProductVariantOptionValueRepository;
import com.giftmarket.product.repository.ProductVariantRepository;
import com.giftmarket.product.service.GiftOccasionService;
import com.giftmarket.product.service.ProductDescriptionSanitizer;
import com.giftmarket.product.service.ProductOptionService;
import com.giftmarket.product.service.ProductRegistrationService;
import com.giftmarket.product.service.ProductService;
import com.giftmarket.product.service.ProductVariantService;
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
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:sample-product-seed;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
@Import({
        SampleProductSeedService.class,
        ProductService.class,
        ProductRegistrationService.class,
        ProductOptionService.class,
        ProductVariantService.class,
        ProductDescriptionSanitizer.class,
        GiftOccasionService.class
})
class SampleProductSeedServiceTest {

    @Autowired SampleProductSeedService seedService;
    @Autowired UserRepository userRepository;
    @Autowired SellerRepository sellerRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired GiftOccasionRepository giftOccasionRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductImageRepository productImageRepository;
    @Autowired ProductGiftOccasionRepository productGiftOccasionRepository;
    @Autowired ProductOptionGroupRepository optionGroupRepository;
    @Autowired ProductOptionValueRepository optionValueRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired ProductVariantOptionValueRepository variantOptionValueRepository;
    @Autowired EntityManager entityManager;
    @MockitoBean StorageProvider storageProvider;
    @MockitoBean ProductDraftService productDraftService;

    private Seller seller;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(User.createOAuthUser(
                UUID.randomUUID() + "@example.test",
                "샘플 판매자",
                null,
                AuthProvider.GOOGLE,
                UUID.randomUUID().toString()
        ));
        seller = sellerRepository.save(Seller.create(owner, "샘플 상점", "시드 테스트 상점"));

        new CategoryDataInitializer(categoryRepository)
                .run(new org.springframework.boot.DefaultApplicationArguments());

        giftOccasionRepository.saveAll(List.of(
                GiftOccasion.create("생일", 0),
                GiftOccasion.create("감사", 1),
                GiftOccasion.create("축하", 2),
                GiftOccasion.create("응원", 3),
                GiftOccasion.create("집들이", 4),
                GiftOccasion.create("기념일", 5),
                GiftOccasion.create("출산·육아", 6),
                GiftOccasion.create("가벼운 선물", 7)
        ));
        entityManager.flush();
        given(storageProvider.objectExists(anyString())).willReturn(true);
    }

    @Test
    void dryRunDoesNotChangeDatabase() {
        long productCount = productRepository.count();

        SampleProductSeedResult result = seedService.seed(seller.getId(), true, completeImageManifest());
        entityManager.flush();

        assertThat(result.dryRun()).isTrue();
        assertThat(result.plannedCount()).isEqualTo(50);
        assertThat(result.createdCount()).isZero();
        assertThat(productRepository.count()).isEqualTo(productCount);
        assertThat(variantRepository.findAllBySkuCodeStartingWithOrderBySkuCodeAsc(
                SampleProductSeedService.SEED_ID + "-"
        )).isEmpty();
    }

    @Test
    void dryRunWithImagesValidatesManifestWithoutChangingDatabase() {
        SampleProductSeedResult result = seedService.seed(
                seller.getId(),
                true,
                completeImageManifest()
        );
        entityManager.flush();

        assertThat(result.imagesAttached()).isEqualTo(50);
        assertThat(productRepository.count()).isZero();
        assertThat(productImageRepository.count()).isZero();
    }

    @Test
    void firstRunCreatesFiftyOnSaleProductsWithPositiveStock() {
        SampleProductSeedResult result = seedService.seed(seller.getId(), false, completeImageManifest());
        entityManager.flush();
        entityManager.clear();

        List<Product> products = productRepository.findAll();
        assertThat(result.createdCount()).isEqualTo(50);
        assertThat(products).hasSize(50)
                .allMatch(product -> product.getStatus() == ProductStatus.ON_SALE)
                .allMatch(product -> product.getStockQuantity() > 0)
                .allMatch(product -> product.getRepresentativeImageKey() != null)
                .allMatch(product -> product.getSeller().getId().equals(seller.getId()));
        List<ProductVariant> variants = variantRepository.findAllBySkuCodeStartingWithOrderBySkuCodeAsc(
                SampleProductSeedService.SEED_ID + "-"
        );
        assertThat(variants).hasSize(100)
                .allMatch(variant -> variant.isActive() && variant.getStockQuantity() > 0);
        Map<Long, Integer> variantStockByProduct = variants.stream().collect(Collectors.groupingBy(
                variant -> variant.getProduct().getId(),
                Collectors.summingInt(ProductVariant::getStockQuantity)
        ));
        assertThat(products).allMatch(
                product -> product.getStockQuantity() == variantStockByProduct.get(product.getId())
        );
        assertThat(optionGroupRepository.count()).isEqualTo(50);
        assertThat(optionValueRepository.count()).isEqualTo(100);
        assertThat(variantOptionValueRepository.count()).isEqualTo(100);
        assertThat(productGiftOccasionRepository.count()).isBetween(50L, 150L);

        Map<Long, Long> categoryCounts = products.stream().collect(Collectors.groupingBy(
                product -> product.getCategory().getId(),
                Collectors.counting()
        ));
        assertThat(categoryCounts).hasSizeGreaterThan(10);
        assertThat(products).anyMatch(Product::isFreeShipping)
                .anyMatch(product -> !product.isFreeShipping())
                .anyMatch(product -> product.getBrandName() == null)
                .anyMatch(product -> product.getBrandName() != null);
        Product lipBalm = products.stream()
                .filter(product -> product.getName().equals("데일리 모이스처 립밤 듀오"))
                .findFirst()
                .orElseThrow();
        assertThat(productGiftOccasionRepository.findActiveByProductId(lipBalm.getId()))
                .extracting(link -> link.getGiftOccasion().getName())
                .containsExactly("생일", "가벼운 선물");
        assertThat(lipBalm.getCategory().getParent().getName()).isEqualTo("뷰티");
        assertThat(lipBalm.getCategory().getName()).isEqualTo("스킨케어");
    }

    @Test
    void missingImageManifestIsRejected() {
        assertThatThrownBy(() -> seedService.seed(seller.getId(), true))
                .isInstanceOf(SampleProductSeedException.class)
                .hasMessageContaining("Image manifest is required");
        assertThat(productRepository.count()).isZero();
    }

    @Test
    void missingStorageObjectAbortsBeforeProductCreation() {
        given(storageProvider.objectExists(anyString())).willReturn(false);

        assertThatThrownBy(() -> seedService.seed(
                seller.getId(),
                false,
                completeImageManifest()
        ))
                .isInstanceOf(SampleProductSeedException.class)
                .hasMessageContaining("does not exist");
        assertThat(productRepository.count()).isZero();
    }

    @Test
    void rerunSkipsTheExistingSeedWithoutDuplicates() {
        seedService.seed(seller.getId(), false, completeImageManifest());
        entityManager.flush();

        SampleProductSeedResult rerun = seedService.seed(seller.getId(), false, completeImageManifest());
        entityManager.flush();

        assertThat(rerun.createdCount()).isZero();
        assertThat(rerun.skippedCount()).isEqualTo(50);
        assertThat(productRepository.count()).isEqualTo(50);
        assertThat(variantRepository.findAllBySkuCodeStartingWithOrderBySkuCodeAsc(
                SampleProductSeedService.SEED_ID + "-"
        )).hasSize(100);
    }

    @Test
    void successfulSeedDoesNotModifyAnExistingProduct() {
        Product existing = saveExistingProduct();

        seedService.seed(seller.getId(), false, completeImageManifest());
        entityManager.flush();
        entityManager.clear();

        Product unchanged = productRepository.findById(existing.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("기존 상품");
        assertThat(unchanged.getBrandName()).isEqualTo("기존 브랜드");
        assertThat(unchanged.getPrice()).isEqualTo(10_000L);
        assertThat(unchanged.getStockQuantity()).isEqualTo(3);
        assertThat(unchanged.getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(productRepository.count()).isEqualTo(51);
    }

    @Test
    void invalidPrerequisiteFailsBeforeExistingProductsAreChanged() {
        Product existing = saveExistingProduct();
        giftOccasionRepository.deleteAll();
        entityManager.flush();

        assertThatThrownBy(() -> seedService.seed(seller.getId(), false, completeImageManifest()))
                .isInstanceOf(SampleProductSeedException.class)
                .hasMessageContaining("gift occasion");
        entityManager.clear();

        Product unchanged = productRepository.findById(existing.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("기존 상품");
        assertThat(unchanged.getStockQuantity()).isEqualTo(3);
        assertThat(productRepository.count()).isEqualTo(1);
    }

    private Product saveExistingProduct() {
        return productRepository.save(Product.createDraft(
                seller,
                categoryRepository.findAll().getFirst(),
                "기존 상품",
                "기존 브랜드",
                "기존 요약",
                "기존 설명",
                10_000L,
                3,
                null,
                true,
                0L,
                3,
                3_000L,
                6_000L
        ));
    }

    private SampleProductImageManifest completeImageManifest() {
        Map<String, SampleProductImageManifest.ImageEntry> images = new LinkedHashMap<>();
        for (int index = 1; index <= 50; index++) {
            String code = SampleProductSeedService.SEED_ID + "-%03d".formatted(index);
            images.put(code, new SampleProductImageManifest.ImageEntry(
                    "products/" + seller.getId() + "/representative/" + code + ".webp",
                    List.of("products/" + seller.getId() + "/gallery/" + code + ".webp")
            ));
        }
        return new SampleProductImageManifest(images);
    }
}
