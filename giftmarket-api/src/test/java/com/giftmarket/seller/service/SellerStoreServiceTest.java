package com.giftmarket.seller.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.seller.dto.request.SellerStoreUpdateRequest;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStore;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.seller.repository.SellerStoreRepository;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SellerStoreServiceTest {
    @Mock SellerRepository sellerRepository;
    @Mock SellerStoreRepository storeRepository;
    @Mock UserRepository userRepository;

    private SellerStoreService service;
    private Seller seller;

    @BeforeEach
    void setUp() {
        service = new SellerStoreService(sellerRepository, storeRepository, userRepository);
        seller = Seller.create(null, "판매자명", "판매자 소개");
        ReflectionTestUtils.setField(seller, "id", 10L);
    }

    @Test
    void firstGetInitializesFromSellerAndExistingGetPreservesStore() {
        given(sellerRepository.findByUserId(1L)).willReturn(Optional.of(seller));
        given(sellerRepository.findByIdForUpdate(10L)).willReturn(Optional.of(seller));
        given(storeRepository.findBySellerIdForUpdate(10L)).willReturn(Optional.empty());
        given(storeRepository.save(any(SellerStore.class))).willAnswer(call -> call.getArgument(0));

        var initial = service.get(1L);
        assertThat(initial.getStoreName()).isEqualTo("판매자명");
        assertThat(initial.getIntroduction()).isEqualTo("판매자 소개");
        assertThat(initial.getLogoImageKey()).isNull();
        assertThat(initial.getBannerImageKey()).isNull();
        assertThat(initial.getCustomerServicePhone()).isNull();
        assertThat(initial.getCustomerServiceEmail()).isNull();
        assertThat(initial.getCustomerServiceOpenTime()).isNull();
        assertThat(initial.getCustomerServiceCloseTime()).isNull();
        assertThat(initial.getCustomerServiceClosedDays()).isNull();
        assertThat(initial.getCustomerServiceNote()).isNull();

        var existing = SellerStore.create(seller, "스토어명", "스토어 소개");
        given(storeRepository.findBySellerIdForUpdate(10L)).willReturn(Optional.of(existing));
        assertThat(service.get(1L).getStoreName()).isEqualTo("스토어명");
    }

    @Test
    void patchUpdatesAllRemainingFieldsWithoutChangingSeller() {
        given(sellerRepository.findByUserId(1L)).willReturn(Optional.of(seller));
        var store = SellerStore.create(seller, "기존 스토어", null);
        ReflectionTestUtils.setField(store, "id", 20L);
        given(storeRepository.findBySellerId(10L)).willReturn(Optional.of(store));

        var response = service.update(1L, request("stores/10/logo/abc.jpg", "stores/10/banner/def.webp"));

        assertThat(response.getId()).isEqualTo(20L);
        assertThat(response.getStoreName()).isEqualTo("새 스토어");
        assertThat(response.getIntroduction()).isEqualTo("새 소개");
        assertThat(response.getLogoImageKey()).isEqualTo("stores/10/logo/abc.jpg");
        assertThat(response.getBannerImageKey()).isEqualTo("stores/10/banner/def.webp");
        assertThat(response.getCustomerServicePhone()).isEqualTo("02-123-4567");
        assertThat(response.getCustomerServiceEmail()).isEqualTo("store@example.com");
        assertThat(response.getCustomerServiceOpenTime()).isEqualTo("09:00");
        assertThat(response.getCustomerServiceCloseTime()).isEqualTo("18:00");
        assertThat(response.getCustomerServiceClosedDays()).isEqualTo("weekends");
        assertThat(response.getCustomerServiceNote()).isEqualTo("lunch 12:00~13:00");
        assertThat(seller.getStoreName()).isEqualTo("판매자명");
        assertThat(seller.getIntroduction()).isEqualTo("판매자 소개");
        verify(storeRepository).existsByStoreNameAndIdNot("새 스토어", 20L);
        verify(storeRepository, never()).save(any());
    }

    @Test
    void patchCanInitializeStoreAndClearOptionalFields() {
        given(sellerRepository.findByUserId(1L)).willReturn(Optional.of(seller));
        given(storeRepository.findBySellerId(10L)).willReturn(Optional.empty());
        given(storeRepository.save(any(SellerStore.class))).willAnswer(call -> call.getArgument(0));

        var response = service.update(1L, new SellerStoreUpdateRequest("스토어", " ", null, "", null, "", "", null, " ", " "));

        assertThat(response.getStoreName()).isEqualTo("스토어");
        assertThat(response.getIntroduction()).isNull();
        assertThat(response.getLogoImageKey()).isNull();
        assertThat(response.getBannerImageKey()).isNull();
        assertThat(response.getCustomerServicePhone()).isNull();
        assertThat(response.getCustomerServiceEmail()).isNull();
        assertThat(response.getCustomerServiceOpenTime()).isNull();
        assertThat(response.getCustomerServiceCloseTime()).isNull();
        assertThat(response.getCustomerServiceClosedDays()).isNull();
        assertThat(response.getCustomerServiceNote()).isNull();
    }

    @Test
    void rejectsDuplicateNameForNewAndExistingStores() {
        given(sellerRepository.findByUserId(1L)).willReturn(Optional.of(seller));
        given(storeRepository.findBySellerId(10L)).willReturn(Optional.empty());
        given(storeRepository.existsByStoreName("새 스토어")).willReturn(true);
        assertThatThrownBy(() -> service.update(1L, request(null, null)))
                .isInstanceOf(SellerException.class).hasMessageContaining("이미 사용 중");

        var existing = SellerStore.create(seller, "기존", null);
        ReflectionTestUtils.setField(existing, "id", 20L);
        given(storeRepository.findBySellerId(10L)).willReturn(Optional.of(existing));
        given(storeRepository.existsByStoreNameAndIdNot("새 스토어", 20L)).willReturn(true);
        assertThatThrownBy(() -> service.update(1L, request(null, null)))
                .isInstanceOf(SellerException.class).hasMessageContaining("이미 사용 중");
        assertThat(existing.getStoreName()).isEqualTo("기존");
        verify(storeRepository, never()).save(any());
    }

    @Test
    void rejectsForeignOrWrongFolderImageKeysBeforeCreatingStore() {
        given(sellerRepository.findByUserId(1L)).willReturn(Optional.of(seller));
        given(storeRepository.findBySellerId(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, request("stores/11/logo/abc.jpg", null)))
                .isInstanceOf(SellerException.class);
        assertThatThrownBy(() -> service.update(1L, request(null, "stores/11/banner/abc.png")))
                .isInstanceOf(SellerException.class);
        assertThatThrownBy(() -> service.update(1L, request("stores/10/banner/abc.jpg", null)))
                .isInstanceOf(SellerException.class);
        assertThatThrownBy(() -> service.update(1L, request(null, "stores/10/logo/abc.png")))
                .isInstanceOf(SellerException.class);
        verify(storeRepository, never()).save(any());
    }

    @Test
    void retainsAuthenticationAndSellerStatusChecks() {
        assertThatThrownBy(() -> service.get(null)).isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> service.update(null, request(null, null))).isInstanceOf(AuthenticationException.class);
        given(sellerRepository.findByUserId(1L)).willReturn(Optional.of(seller));
        seller.suspend();
        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(SellerException.class);
        assertThatThrownBy(() -> service.update(1L, request(null, null))).isInstanceOf(SellerException.class);
    }

    private SellerStoreUpdateRequest request(String logo, String banner) {
        return new SellerStoreUpdateRequest(" 새 스토어 ", " 새 소개 ", logo, banner,
                " 02-123-4567 ", "store@example.com", "09:00", "18:00", " weekends ", " lunch 12:00~13:00 ");
    }
}
