package com.giftmarket.admin.controller;

import com.giftmarket.admin.service.AdministratorService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AdministratorController.class,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdministratorControllerSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AdministratorService administratorService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean CustomOidcUserService customOidcUserService;
    @MockitoBean OAuth2AuthenticationSuccessHandler authenticationSuccessHandler;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void regularAdminCannotAccessAdministratorManagementApi() throws Exception {
        mockMvc.perform(get("/api/admin/administrators")
                        .with(authentication(auth(1L, "ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        verify(administratorService, never()).getAdministrators(1L);
    }

    @Test
    void regularUserCannotGrantAdministratorRole() throws Exception {
        mockMvc.perform(patch("/api/admin/administrators/10/grant")
                        .with(authentication(auth(2L, "ROLE_USER"))))
                .andExpect(status().isForbidden());

        verify(administratorService, never()).grantAdministrator(2L, 10L);
    }

    @Test
    void superAdminCanAccessAdministratorManagementApi() throws Exception {
        given(administratorService.getAdministrators(3L)).willReturn(List.of());

        mockMvc.perform(get("/api/admin/administrators")
                        .with(authentication(auth(3L, "ROLE_SUPER_ADMIN"))))
                .andExpect(status().isOk());

        verify(administratorService).getAdministrators(3L);
    }

    private UsernamePasswordAuthenticationToken auth(Long userId, String authority) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority(authority))
        );
    }
}
