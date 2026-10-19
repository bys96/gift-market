package com.giftmarket.product.seed;

import com.giftmarket.GiftmarketApiApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class SampleProductSeedApplication {

    private SampleProductSeedApplication() {
    }

    public static void main(String[] args) {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(
                GiftmarketApiApplication.class
        )
                .web(WebApplicationType.NONE)
                .properties(
                        "app.seed.sample-products.enabled=true",
                        "app.seed.sample-products.dry-run=true"
                )
                .run(args)) {
            // The context is closed immediately after the one-time runner completes.
        }
    }
}
