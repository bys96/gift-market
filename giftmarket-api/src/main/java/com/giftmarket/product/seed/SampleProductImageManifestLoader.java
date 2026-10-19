package com.giftmarket.product.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.seed.sample-products.enabled",
        havingValue = "true"
)
public class SampleProductImageManifestLoader {

    private final ObjectMapper objectMapper;

    public SampleProductImageManifest load(String manifestPath) {
        if (manifestPath == null || manifestPath.isBlank()) {
            return SampleProductImageManifest.empty();
        }

        Path path = Path.of(manifestPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new SampleProductSeedException(
                    "Image manifest file was not found: " + path
            );
        }

        try {
            return objectMapper.readValue(path.toFile(), SampleProductImageManifest.class);
        } catch (IOException exception) {
            throw new SampleProductSeedException(
                    "Image manifest could not be read: " + path,
                    exception
            );
        }
    }
}
