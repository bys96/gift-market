package com.giftmarket.product.seed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SampleProductSeedRunnerTest {

    @Test
    void productionProfileIsBlockedBeforeDatabaseAccess() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        SampleProductSeedService seedService = mock(SampleProductSeedService.class);
        SampleProductImageManifestLoader manifestLoader = mock(SampleProductImageManifestLoader.class);
        SampleProductSeedRunner runner = new SampleProductSeedRunner(
                environment,
                seedService,
                manifestLoader
        );

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(SampleProductSeedException.class)
                .hasMessageContaining("blocked");
        verifyNoInteractions(seedService);
        verifyNoInteractions(manifestLoader);
    }
}
