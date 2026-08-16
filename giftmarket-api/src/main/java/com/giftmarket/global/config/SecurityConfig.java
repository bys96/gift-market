package com.giftmarket.global.config;

import com.giftmarket.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.giftmarket.auth.jwt.JwtAuthenticationFilter;
import com.giftmarket.auth.service.CustomOidcUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOidcUserService customOidcUserService;
    private final OAuth2AuthenticationSuccessHandler authenticationSuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .cors(cors -> cors
                        .configurationSource(corsConfigurationSource())
                )

                // OAuth2 로그인 과정에서 임시 세션 필요
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.IF_REQUIRED
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/error"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/token",
                                "/api/auth/logout"
                        )
                        .permitAll()

                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        .requestMatchers("/api/seller/products/**")
                        .hasRole("SELLER")

                        .requestMatchers(
                                "/api/auth/me",
                                "/api/users/me",
                                "/api/storage/**",
                                "/api/seller-applications/**",
                                "/api/cart/**",
                                "/api/orders/**",
                                "/api/payments/**",
                                "/api/addresses/**"
                        )
                        .authenticated()

                        .anyRequest()
                        .permitAll()
                )

                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(customOidcUserService)
                        )
                        .successHandler(authenticationSuccessHandler)
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of("http://localhost:3000")
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type"
                )
        );

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}
