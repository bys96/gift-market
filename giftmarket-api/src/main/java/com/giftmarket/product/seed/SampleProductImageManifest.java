package com.giftmarket.product.seed;

import java.util.List;
import java.util.Map;

public record SampleProductImageManifest(
        Map<String, ImageEntry> products
) {
    public static SampleProductImageManifest empty() {
        return new SampleProductImageManifest(Map.of());
    }

    public Map<String, ImageEntry> normalizedProducts() {
        return products == null ? Map.of() : Map.copyOf(products);
    }

    public record ImageEntry(
            String representativeImageKey,
            List<String> galleryImageKeys
    ) {
        public List<String> normalizedGalleryImageKeys() {
            return galleryImageKeys == null ? List.of() : List.copyOf(galleryImageKeys);
        }
    }
}
