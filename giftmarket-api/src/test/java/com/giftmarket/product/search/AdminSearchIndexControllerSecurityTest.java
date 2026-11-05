package com.giftmarket.product.search;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminSearchIndexController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminSearchIndexControllerSecurityTest {
    @Test void settingsEndpointsKeepExistingAdminAuthorizationAndResponseEnvelope() throws Exception {
        given(index.settings(true)).willReturn(new MeiliIndexService.SettingsResult("APPLIED", true, 2, "hash", "hash"));
        given(index.settings(false)).willReturn(new MeiliIndexService.SettingsResult("VERIFIED", true, 2, "hash", "hash"));
        for (String role : List.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN")) {
            mockMvc.perform(post("/api/admin/product-search/settings").with(authentication(auth(role))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPLIED"));
            mockMvc.perform(get("/api/admin/product-search/settings").with(authentication(auth(role))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.matches").value(true));
        }
        for (String role : List.of("ROLE_USER", "ROLE_SELLER")) {
            mockMvc.perform(post("/api/admin/product-search/settings").with(authentication(auth(role))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/product-search/settings").with(authentication(auth(role))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/admin/product-search/settings")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/admin/product-search/settings")).andExpect(status().is3xxRedirection());
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean MeiliIndexService index;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean CustomOidcUserService customOidcUserService;
    @MockitoBean OAuth2AuthenticationSuccessHandler authenticationSuccessHandler;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void adminCanAccess() throws Exception {
        given(index.reindex(false)).willReturn(new MeiliIndexService.Result(0, "COMPLETE", 0));
        mockMvc.perform(post("/api/admin/product-search/reindex").with(authentication(auth("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminCanAccess() throws Exception {
        given(index.reindex(false)).willReturn(new MeiliIndexService.Result(0, "COMPLETE", 0));
        mockMvc.perform(post("/api/admin/product-search/reindex").with(authentication(auth("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotAccess() throws Exception {
        mockMvc.perform(post("/api/admin/product-search/reindex").with(authentication(auth("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void sellerCannotAccess() throws Exception {
        mockMvc.perform(post("/api/admin/product-search/reindex").with(authentication(auth("ROLE_SELLER"))))
                .andExpect(status().isForbidden());
    }

    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(
                1L,
                null,
                List.of(new SimpleGrantedAuthority(role))
        );
    }
}
