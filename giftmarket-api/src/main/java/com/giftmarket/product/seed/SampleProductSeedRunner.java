package com.giftmarket.product.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.seed.sample-products.enabled",
        havingValue = "true"
)
public class SampleProductSeedRunner implements ApplicationRunner {

    private final Environment environment;
    private final SampleProductSeedService seedService;
    private final SampleProductImageManifestLoader imageManifestLoader;

    @Override
    public void run(ApplicationArguments args) {
        blockProductionProfile();

        Long sellerId = environment.getProperty(
                "app.seed.sample-products.seller-id",
                Long.class
        );
        boolean dryRun = environment.getProperty(
                "app.seed.sample-products.dry-run",
                Boolean.class,
                true
        );
        String imageManifestPath = environment.getProperty(
                "app.seed.sample-products.image-manifest"
        );

        if (sellerId == null || sellerId <= 0) {
            throw new SampleProductSeedException(
                    "app.seed.sample-products.seller-id must be a positive existing seller ID."
            );
        }

        log.info(
                "Sample product seed started: seedId={}, sellerId={}, dryRun={}, imageManifestProvided={}",
                SampleProductSeedService.SEED_ID,
                sellerId,
                dryRun,
                imageManifestPath != null && !imageManifestPath.isBlank()
        );

        SampleProductSeedResult result;
        try {
            SampleProductImageManifest imageManifest = imageManifestLoader.load(imageManifestPath);
            result = seedService.seed(sellerId, dryRun, imageManifest);
        } catch (RuntimeException exception) {
            log.error(
                    "Sample product seed failed: seedId={}, sellerId={}, dryRun={}, reason={}",
                    SampleProductSeedService.SEED_ID,
                    sellerId,
                    dryRun,
                    exception.getMessage()
            );
            throw exception;
        }

        log.info(
                "Sample product seed completed: seedId={}, dryRun={}, planned={}, created={}, skipped={}, categories={}, giftOccasions={}, imagesAttached={}",
                result.seedId(),
                result.dryRun(),
                result.plannedCount(),
                result.createdCount(),
                result.skippedCount(),
                result.categoryCount(),
                result.giftOccasionCount(),
                result.imagesAttached()
        );
    }

    private void blockProductionProfile() {
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));

        if (production) {
            throw new SampleProductSeedException(
                    "Sample product seed is blocked when the prod or production profile is active."
            );
        }
    }
}
