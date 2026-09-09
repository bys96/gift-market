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
                .customerServiceOpenTime("09:00").customerServiceCloseTime("18:00")
                .customerServiceClosedDays("weekends").customerServiceNote("lunch 12:00~13:00").build();
        given(service.get(1L)).willReturn(response);
        given(service.update(eq(1L), any(SellerStoreUpdateRequest.class))).willReturn(response);

        mvc.perform(get("/api/seller/store"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keys()", containsInAnyOrder(
                        "id", "storeName", "introduction", "logoImageKey", "bannerImageKey",
                        "customerServicePhone", "customerServiceEmail", "customerServiceOpenTime", "customerServiceCloseTime",
                        "customerServiceClosedDays", "customerServiceNote")));

        mvc.perform(patch("/api/seller/store").contentType(MediaType.APPLICATION_JSON).content("""
                {"storeName":"스토어","introduction":"소개","logoImageKey":"stores/10/logo/abc.jpg",
                 "bannerImageKey":"stores/10/banner/def.webp","customerServicePhone":"02-123-4567",
                 "customerServiceEmail":"store@example.com","customerServiceOpenTime":"09:00","customerServiceCloseTime":"18:00",
                 "customerServiceClosedDays":"weekends","customerServiceNote":"lunch 12:00~13:00"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storeName").value("스토어"))
                .andExpect(jsonPath("$.data.keys()", containsInAnyOrder(
                        "id", "storeName", "introduction", "logoImageKey", "bannerImageKey",
                        "customerServicePhone", "customerServiceEmail", "customerServiceOpenTime", "customerServiceCloseTime",
                        "customerServiceClosedDays", "customerServiceNote")));

        verify(service).update(1L, new SellerStoreUpdateRequest("스토어", "소개",
                "stores/10/logo/abc.jpg", "stores/10/banner/def.webp",
                "02-123-4567", "store@example.com", "09:00", "18:00", "weekends", "lunch 12:00~13:00"));
    }

    @Test
    void patchRejectsInvalidTimesAndOversizedContactText() throws Exception {
        for (String field : new String[]{"customerServiceOpenTime", "customerServiceCloseTime"}) {
            for (String time : new String[]{"24:00", "09:60", "9:00", "09:00:00"}) {
                mvc.perform(patch("/api/seller/store").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"storeName\":\"스토어\",\"" + field + "\":\"" + time + "\"}"))
                        .andExpect(status().isBadRequest());
            }
        }
        mvc.perform(patch("/api/seller/store").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeName\":\"스토어\",\"customerServiceClosedDays\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/seller/store").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeName\":\"스토어\",\"customerServiceNote\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void patchAcceptsOptionalAndBoundaryTimes() throws Exception {
        for (String time : new String[]{"", "00:00", "23:59"}) {
            mvc.perform(patch("/api/seller/store").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"storeName\":\"스토어\",\"customerServiceOpenTime\":\"" + time
                                    + "\",\"customerServiceCloseTime\":\"" + time + "\"}"))
                    .andExpect(status().isOk());
            verify(service).update(1L, new SellerStoreUpdateRequest("스토어", null, null, null,
                    null, null, time, time, null, null));
        }
        mvc.perform(patch("/api/seller/store").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeName\":\"스토어\"}"))
                .andExpect(status().isOk());
        verify(service).update(1L, new SellerStoreUpdateRequest("스토어", null, null, null,
                null, null, null, null, null, null));
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
