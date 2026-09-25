package com.giftmarket.admin.service;

import com.giftmarket.admin.entity.AdminRoleChangeLog;
import com.giftmarket.admin.exception.AdminUserOperationException;
import com.giftmarket.admin.repository.AdminRoleChangeLogRepository;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdministratorServiceTest {

    private static final Long SUPER_ADMIN_ID = 1L;
    private static final Long TARGET_ID = 10L;

    @Mock UserRepository userRepository;
    @Mock SellerRepository sellerRepository;
    @Mock AdminRoleChangeLogRepository roleChangeLogRepository;
    @Mock User operator;
    @Mock User target;

    private AdministratorService service;

    @BeforeEach
    void setUp() {
        service = new AdministratorService(
                userRepository,
                sellerRepository,
                roleChangeLogRepository
        );
    }

    @Test
    void superAdminCanGrantUserAdministratorRoleAndStoresHistory() {
        givenSuperAdmin();
        givenTarget(UserRole.USER);
        given(sellerRepository.existsByUser(target)).willReturn(false);
        given(roleChangeLogRepository.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.grantAdministrator(SUPER_ADMIN_ID, TARGET_ID);

        verify(target).changeRole(UserRole.ADMIN);
        ArgumentCaptor<AdminRoleChangeLog> captor = ArgumentCaptor.forClass(AdminRoleChangeLog.class);
        verify(roleChangeLogRepository).save(captor.capture());
        assertThat(captor.getValue().getOperatorUserId()).isEqualTo(SUPER_ADMIN_ID);
        assertThat(captor.getValue().getTargetUserId()).isEqualTo(TARGET_ID);
        assertThat(captor.getValue().getPreviousRole()).isEqualTo(UserRole.USER);
        assertThat(captor.getValue().getChangedRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void grantingSellerKeepsSellerData() {
        givenSuperAdmin();
        givenTarget(UserRole.SELLER);
        given(sellerRepository.existsByUser(target)).willReturn(true);
        given(roleChangeLogRepository.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        var response = service.grantAdministrator(SUPER_ADMIN_ID, TARGET_ID);

        verify(target).changeRole(UserRole.ADMIN);
        assertThat(response.seller()).isTrue();
        verify(sellerRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void regularAdminCannotGrantAdministratorRole() {
        given(userRepository.findById(SUPER_ADMIN_ID)).willReturn(Optional.of(operator));
        given(operator.getRole()).willReturn(UserRole.ADMIN);

        assertThatThrownBy(() -> service.grantAdministrator(SUPER_ADMIN_ID, TARGET_ID))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("SUPER_ADMIN 권한이 필요합니다.");
        verify(userRepository, never()).findByIdForUpdate(TARGET_ID);
    }

    @Test
    void regularUserCannotGrantAdministratorRole() {
        given(userRepository.findById(SUPER_ADMIN_ID)).willReturn(Optional.of(operator));
        given(operator.getRole()).willReturn(UserRole.USER);

        assertThatThrownBy(() -> service.grantAdministrator(SUPER_ADMIN_ID, TARGET_ID))
                .isInstanceOf(AuthenticationException.class);
        verify(userRepository, never()).findByIdForUpdate(TARGET_ID);
    }

    @Test
    void superAdminRoleCannotBeChanged() {
        givenSuperAdmin();
        givenTarget(UserRole.SUPER_ADMIN);

        assertThatThrownBy(() -> service.revokeAdministrator(SUPER_ADMIN_ID, TARGET_ID))
                .isInstanceOf(AdminUserOperationException.class)
                .hasMessage("SUPER_ADMIN 권한은 API에서 변경할 수 없습니다.");
        verify(target, never()).changeRole(org.mockito.ArgumentMatchers.any());
        verify(roleChangeLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void superAdminCannotBeGrantedAdministratorRole() {
        givenSuperAdmin();
        givenTarget(UserRole.SUPER_ADMIN);

        assertThatThrownBy(() -> service.grantAdministrator(SUPER_ADMIN_ID, TARGET_ID))
                .isInstanceOf(AdminUserOperationException.class);
        verify(target, never()).changeRole(org.mockito.ArgumentMatchers.any());
        verify(roleChangeLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void revokingAdministratorWithSellerRestoresSellerRole() {
        givenSuperAdmin();
        givenTarget(UserRole.ADMIN);
        given(sellerRepository.existsByUser(target)).willReturn(true);
        given(roleChangeLogRepository.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        var response = service.revokeAdministrator(SUPER_ADMIN_ID, TARGET_ID);

        verify(target).changeRole(UserRole.SELLER);
        assertThat(response.seller()).isTrue();
        verify(sellerRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void revokingAdministratorWithoutSellerRestoresUserRoleAndStoresHistory() {
        givenSuperAdmin();
        givenTarget(UserRole.ADMIN);
        given(sellerRepository.existsByUser(target)).willReturn(false);
        given(roleChangeLogRepository.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.revokeAdministrator(SUPER_ADMIN_ID, TARGET_ID);

        verify(target).changeRole(UserRole.USER);
        ArgumentCaptor<AdminRoleChangeLog> captor = ArgumentCaptor.forClass(AdminRoleChangeLog.class);
        verify(roleChangeLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousRole()).isEqualTo(UserRole.ADMIN);
        assertThat(captor.getValue().getChangedRole()).isEqualTo(UserRole.USER);
    }

    private void givenSuperAdmin() {
        given(userRepository.findById(SUPER_ADMIN_ID)).willReturn(Optional.of(operator));
        given(operator.getRole()).willReturn(UserRole.SUPER_ADMIN);
    }

    private void givenTarget(UserRole role) {
        given(userRepository.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(target));
        given(target.getRole()).willReturn(role);
    }
}
