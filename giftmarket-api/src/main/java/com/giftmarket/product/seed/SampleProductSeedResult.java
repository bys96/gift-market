package com.giftmarket.product.seed;

public record SampleProductSeedResult(
        String seedId,
        boolean dryRun,
        int plannedCount,
        int createdCount,
        int skippedCount,
        int categoryCount,
        int giftOccasionCount,
        int imagesAttached
) {
}
