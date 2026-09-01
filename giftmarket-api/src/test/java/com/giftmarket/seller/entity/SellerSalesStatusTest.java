package com.giftmarket.seller.entity;

import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SellerSalesStatusTest {

    @Test
    void salesSuspensionAndReactivationUseDedicatedStatus() {
        Seller seller = Seller.create(mock(User.class), "선물 상점", null);

        seller.suspendSales();
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.SALES_SUSPENDED);

        seller.reactivateSales();
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.ACTIVE);
    }

    @Test
    void strongSuspensionAndWithdrawalRemainDistinct() {
        Seller suspended = Seller.create(mock(User.class), "정지 상점", null);
        suspended.suspend();
        assertThat(suspended.getStatus()).isEqualTo(SellerStatus.SUSPENDED);
        assertThatThrownBy(suspended::suspendSales).isInstanceOf(IllegalStateException.class);

        Seller withdrawn = Seller.create(mock(User.class), "탈퇴 상점", null);
        withdrawn.withdraw();
        assertThat(withdrawn.getStatus()).isEqualTo(SellerStatus.WITHDRAWN);
        assertThatThrownBy(withdrawn::reactivateSales).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void duplicateSalesTransitionsFail() {
        Seller seller = Seller.create(mock(User.class), "선물 상점", null);
        assertThatThrownBy(seller::reactivateSales).isInstanceOf(IllegalStateException.class);
        seller.suspendSales();
        assertThatThrownBy(seller::suspendSales).isInstanceOf(IllegalStateException.class);
    }
}
