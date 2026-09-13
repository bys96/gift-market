package com.giftmarket.global.health.controller;

import com.giftmarket.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.giftmarket.auth.jwt.JwtTokenProvider;
import com.giftmarket.auth.service.CustomOidcUserService;
import com.giftmarket.global.config.SecurityConfig;
import com.giftmarket.global.exception.GlobalExceptionHandler;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = HealthController.class,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class HealthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean CustomOidcUserService customOidcUserService;
    @MockitoBean OAuth2AuthenticationSuccessHandler authenticationSuccessHandler;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void healthDoesNotRequireAuthenticationAndReturnsOk() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.data").value("UP"));
    }

    @Test
    void missingEndpointReturnsNotFoundApiResponse() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("요청한 리소스를 찾을 수 없습니다."));
    }
}
