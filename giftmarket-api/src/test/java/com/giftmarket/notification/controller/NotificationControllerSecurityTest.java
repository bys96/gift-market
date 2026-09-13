package com.giftmarket.notification.controller;

import com.giftmarket.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.giftmarket.auth.jwt.JwtTokenProvider;
import com.giftmarket.auth.service.CustomOidcUserService;
import com.giftmarket.global.config.SecurityConfig;
import com.giftmarket.global.exception.GlobalExceptionHandler;
import com.giftmarket.notification.dto.response.NotificationPageResponse;
import com.giftmarket.notification.entity.NotificationContext;
import com.giftmarket.notification.service.NotificationService;
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
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                NotificationController.class,
                SellerNotificationController.class,
                AdminNotificationController.class
        },
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class NotificationControllerSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean NotificationService notificationService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean CustomOidcUserService customOidcUserService;
    @MockitoBean OAuth2AuthenticationSuccessHandler authenticationSuccessHandler;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void anonymousCannotAccessBuyerNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void authenticatedUserUsesBuyerContext() throws Exception {
        given(notificationService.getNotifications(
                11L, NotificationContext.BUYER, 0, 20
        )).willReturn(emptyPage());

        mockMvc.perform(get("/api/notifications")
                        .with(authentication(userAuthentication(11L))))
                .andExpect(status().isOk());

        verify(notificationService).getNotifications(
                11L, NotificationContext.BUYER, 0, 20
        );
    }

    @Test
    void authenticatedUserReachesSellerPolicyWithSellerContext() throws Exception {
        given(notificationService.getNotifications(
                12L, NotificationContext.SELLER, 0, 20
        )).willReturn(emptyPage());

        mockMvc.perform(get("/api/seller/notifications")
                        .with(authentication(userAuthentication(12L))))
                .andExpect(status().isOk());

        verify(notificationService).getNotifications(
                12L, NotificationContext.SELLER, 0, 20
        );
    }

    @Test
    void nonAdminCannotAccessAdminNotifications() throws Exception {
        mockMvc.perform(get("/api/admin/notifications")
                        .with(authentication(userAuthentication(13L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUsesAdminContext() throws Exception {
        given(notificationService.getNotifications(
                14L, NotificationContext.ADMIN, 0, 20
        )).willReturn(emptyPage());

        mockMvc.perform(get("/api/admin/notifications")
                        .with(authentication(adminAuthentication(14L))))
                .andExpect(status().isOk());

        verify(notificationService).getNotifications(
                14L, NotificationContext.ADMIN, 0, 20
        );
    }

    private NotificationPageResponse emptyPage() {
        return new NotificationPageResponse(
                List.of(),
                0,
                20,
                0,
                0,
                true,
                true
        );
    }

    private UsernamePasswordAuthenticationToken userAuthentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    private UsernamePasswordAuthenticationToken adminAuthentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
