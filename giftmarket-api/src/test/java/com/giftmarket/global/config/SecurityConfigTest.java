package com.giftmarket.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void corsUsesConfiguredFrontendOriginWithCredentials() {
        SecurityConfig securityConfig = new SecurityConfig(null, null, null);
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(
                "https://staging.example.com"
        );

        CorsConfiguration configuration = source.getCorsConfiguration(
                new MockHttpServletRequest("OPTIONS", "/api/products")
        );

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly("https://staging.example.com");
        assertThat(configuration.getAllowCredentials()).isTrue();
    }
}
