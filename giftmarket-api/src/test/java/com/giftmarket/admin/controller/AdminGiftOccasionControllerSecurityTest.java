package com.giftmarket.admin.controller;

import com.giftmarket.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.giftmarket.auth.jwt.JwtTokenProvider;
import com.giftmarket.auth.service.CustomOidcUserService;
import com.giftmarket.global.config.SecurityConfig;
import com.giftmarket.global.exception.GlobalExceptionHandler;
import com.giftmarket.product.service.GiftOccasionService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminGiftOccasionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminGiftOccasionControllerSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean GiftOccasionService giftOccasionService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean CustomOidcUserService customOidcUserService;
    @MockitoBean OAuth2AuthenticationSuccessHandler authenticationSuccessHandler;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void adminCanAccess() throws Exception {
        given(giftOccasionService.getAllGiftOccasions()).willReturn(List.of());
        mockMvc.perform(get("/api/admin/gift-occasions").with(authentication(auth("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminCanAccess() throws Exception {
        given(giftOccasionService.getAllGiftOccasions()).willReturn(List.of());
        mockMvc.perform(get("/api/admin/gift-occasions").with(authentication(auth("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotAccess() throws Exception {
        mockMvc.perform(get("/api/admin/gift-occasions").with(authentication(auth("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void sellerCannotAccess() throws Exception {
        mockMvc.perform(get("/api/admin/gift-occasions").with(authentication(auth("ROLE_SELLER"))))
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
