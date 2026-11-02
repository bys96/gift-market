package com.giftmarket.product.search;

import com.giftmarket.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.giftmarket.auth.jwt.JwtTokenProvider;
import com.giftmarket.auth.service.CustomOidcUserService;
import com.giftmarket.global.config.SecurityConfig;
import com.giftmarket.global.exception.GlobalExceptionHandler;
import com.giftmarket.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminProductEmbeddingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminProductEmbeddingSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductEmbeddingIndexer indexer;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean CustomOidcUserService customOidcUserService;
    @MockitoBean OAuth2AuthenticationSuccessHandler authenticationSuccessHandler;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void onlyAdministratorsCanReindex() throws Exception {
        when(indexer.reindex(0, 1)).thenReturn(new ProductEmbeddingIndexer.Result(0, 0, 0, null, "DISABLED"));
        mvc.perform(post("/api/admin/product-search/reindex").with(authentication(auth("ROLE_ADMIN")))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/product-search/reindex").with(authentication(auth("ROLE_SUPER_ADMIN")))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/product-search/reindex").with(authentication(auth("ROLE_USER")))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/product-search/reindex").with(authentication(auth("ROLE_SELLER")))).andExpect(status().isForbidden());
        verify(indexer, times(2)).reindex(0, 1);
    }

    private static UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority(role)));
    }
}
