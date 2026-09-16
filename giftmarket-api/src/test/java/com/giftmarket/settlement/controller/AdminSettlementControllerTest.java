package com.giftmarket.settlement.controller;

import com.giftmarket.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.giftmarket.auth.jwt.JwtTokenProvider;
import com.giftmarket.auth.service.CustomOidcUserService;
import com.giftmarket.global.config.SecurityConfig;
import com.giftmarket.global.exception.GlobalExceptionHandler;
import com.giftmarket.settlement.dto.response.AdminSettlementGenerateResponse;
import com.giftmarket.settlement.dto.response.AdminSettlementPageResponse;
import com.giftmarket.settlement.entity.SettlementStatus;
import com.giftmarket.settlement.service.AdminSettlementService;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminSettlementController.class,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminSettlementControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AdminSettlementService service;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean CustomOidcUserService customOidcUserService;
    @MockitoBean OAuth2AuthenticationSuccessHandler authenticationSuccessHandler;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void adminListBindsFiltersAndPagination() throws Exception {
        var start = LocalDateTime.of(2026, 9, 1, 0, 0);
        var end = LocalDateTime.of(2026, 10, 1, 0, 0);
        given(service.getSettlements(1L, 20L, SettlementStatus.READY, start, end, 1, 10))
                .willReturn(new AdminSettlementPageResponse(List.of(), 1, 10, 0, 0, false, true));

        mockMvc.perform(get("/api/admin/settlements")
                        .with(authentication(auth(1L, "ROLE_ADMIN")))
                        .param("sellerId", "20")
                        .param("status", "READY")
                        .param("periodStart", "2026-09-01T00:00:00")
                        .param("periodEnd", "2026-10-01T00:00:00")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1));
        verify(service).getSettlements(1L, 20L, SettlementStatus.READY, start, end, 1, 10);
    }

    @Test
    void adminGenerateReturnsExplicitNoOp() throws Exception {
        var request = new com.giftmarket.settlement.dto.request.AdminSettlementGenerateRequest(
                20L, LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0));
        given(service.generate(1L, request)).willReturn(new AdminSettlementGenerateResponse(false, null));

        mockMvc.perform(post("/api/admin/settlements/generate")
                        .with(authentication(auth(1L, "ROLE_ADMIN")))
                        .contentType("application/json")
                        .content("""
                                {"sellerId":20,"periodStart":"2026-09-01T00:00:00",
                                 "periodEnd":"2026-10-01T00:00:00","cutoff":"2026-10-01T00:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.settlement").isEmpty());
        verify(service).generate(1L, request);
    }

    @Test
    void unauthenticatedAndNonAdminCannotAccessAnyAdminSettlementRoute() throws Exception {
        mockMvc.perform(get("/api/admin/settlements"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/admin/settlements")
                        .with(authentication(auth(2L, "ROLE_USER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/settlements/50/confirm")
                        .with(authentication(auth(3L, "ROLE_SELLER"))))
                .andExpect(status().isForbidden());
        verify(service, never()).confirm(50L, 3L);
    }

    @Test
    void invalidGenerateAndHoldBodyAreRejected() throws Exception {
        mockMvc.perform(post("/api/admin/settlements/generate")
                        .with(authentication(auth(1L, "ROLE_ADMIN")))
                        .contentType("application/json")
                        .content("{" + "\"sellerId\":0,\"periodStart\":null}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/settlements/50/hold")
                        .with(authentication(auth(1L, "ROLE_ADMIN")))
                        .contentType("application/json")
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken auth(Long id, String role) {
        return new UsernamePasswordAuthenticationToken(
                id, null, List.of(new SimpleGrantedAuthority(role))
        );
    }
}
