package com.giftmarket.product.repository;

import com.giftmarket.product.entity.Product;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProductSpecificationsTest {

    @Test
    @SuppressWarnings("unchecked")
    void buyerQueryRequiresAdminHiddenFalse() {
        Root<Product> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path<Boolean> adminHidden = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        given(root.<Boolean>get("adminHidden")).willReturn(adminHidden);
        given(criteriaBuilder.isFalse(adminHidden)).willReturn(predicate);

        Predicate result = ProductSpecifications.notAdminHidden()
                .toPredicate(root, query, criteriaBuilder);

        assertThat(result).isSameAs(predicate);
        verify(criteriaBuilder).isFalse(adminHidden);
    }
}
