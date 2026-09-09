package com.giftmarket.seller.controller;

import com.giftmarket.global.exception.GlobalExceptionHandler;
import com.giftmarket.seller.dto.request.SellerStoreUpdateRequest;
import com.giftmarket.seller.dto.response.SellerStoreResponse;
import com.giftmarket.seller.service.SellerStoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SellerStoreControllerTest {
    private SellerStoreService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(SellerStoreService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SellerStoreController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(1L, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAndPatchRetainRoutesEnvelopeAndRemainingResponseFields() throws Exception {
        var response = SellerStoreResponse.builder().id(20L).storeName("스토어")
                .introduction("소개").logoImageKey("stores/10/logo/abc.jpg")
                .bannerImageKey("stores/10/banner/def.webp")
                .customerServicePhone("02-123-4567").customerServiceEmail("store@example.com")
                .customerServiceHours("09:00~18:00").build();
        given(service.get(1L)).willReturn(response);
        given(service.update(eq(1L), any(SellerStoreUpdateRequest.class))).willReturn(response);

        mvc.perform(get("/api/seller/store"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keys()", containsInAnyOrder(
                        "id", "storeName", "introduction", "logoImageKey", "bannerImageKey",
                        "customerServicePhone", "customerServiceEmail", "customerServiceHours")));

        mvc.perform(patch("/api/seller/store").contentType(MediaType.APPLICATION_JSON).content("""
                {"storeName":"스토어","introduction":"소개","logoImageKey":"stores/10/logo/abc.jpg",
                 "bannerImageKey":"stores/10/banner/def.webp","customerServicePhone":"02-123-4567",
                 "customerServiceEmail":"store@example.com","customerServiceHours":"09:00~18:00"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storeName").value("스토어"))
                .andExpect(jsonPath("$.data.keys()", containsInAnyOrder(
                        "id", "storeName", "introduction", "logoImageKey", "bannerImageKey",
                        "customerServicePhone", "customerServiceEmail", "customerServiceHours")));

        verify(service).update(1L, new SellerStoreUpdateRequest("스토어", "소개",
                "stores/10/logo/abc.jpg", "stores/10/banner/def.webp",
                "02-123-4567", "store@example.com", "09:00~18:00"));
    }

    @Test
    void patchRetainsNameAndEmailValidation() throws Exception {
        mvc.perform(patch("/api/seller/store").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeName\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/seller/store").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeName\":\"스토어\",\"customerServiceEmail\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
