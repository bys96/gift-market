package com.giftmarket.seller.service;

import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.seller.dto.request.SellerStoreUpdateRequest;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.seller.repository.SellerStoreRepository;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:seller-store;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SellerStoreService.class)
class SellerStorePersistenceIntegrationTest {

    @Autowired SellerStoreService service;
    @Autowired UserRepository userRepository;
    @Autowired SellerRepository sellerRepository;
    @Autowired SellerStoreRepository storeRepository;
    @Autowired EntityManager entityManager;
    @MockitoBean StorageService storageService;

    @Test
    void updatingStoreNamePersistsTheSameNameToSellerAndSellerStore() {
        Seller seller = seller("original-store");

        service.update(seller.getUser().getId(), request("renamed-store"));

        entityManager.flush();
        entityManager.clear();

        assertThat(sellerRepository.findById(seller.getId()).orElseThrow().getStoreName())
                .isEqualTo("renamed-store");
        assertThat(storeRepository.findBySellerId(seller.getId()).orElseThrow().getStoreName())
                .isEqualTo("renamed-store");
    }

    @Test
    void updatingOneSellerStoreDoesNotChangeAnotherSellersStoreName() {
        Seller owner = seller("owner-store");
        Seller otherSeller = seller("other-store");

        service.update(owner.getUser().getId(), request("owner-renamed"));

        entityManager.flush();
        entityManager.clear();

        assertThat(sellerRepository.findById(otherSeller.getId()).orElseThrow().getStoreName())
                .isEqualTo("other-store");
        assertThat(storeRepository.findBySellerId(otherSeller.getId())).isEmpty();
    }

    private Seller seller(String storeName) {
        User user = userRepository.save(User.createOAuthUser(
                UUID.randomUUID() + "@example.test",
                "seller",
                null,
                AuthProvider.GOOGLE,
                UUID.randomUUID().toString()
        ));
        return sellerRepository.save(Seller.create(user, storeName, "introduction"));
    }

    private SellerStoreUpdateRequest request(String storeName) {
        return new SellerStoreUpdateRequest(
                storeName, null, null, null, null, null, null, null, null, null
        );
    }
}
