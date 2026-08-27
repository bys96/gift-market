package com.giftmarket.user.service;

import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.user.dto.UpdateMyProfileRequest;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceProfileOwnershipTest {
    @Mock UserRepository users;
    @Mock StorageService storage;
    @Mock User user;
    UserService service;

    @BeforeEach void setUp() {
        service = new UserService(users, storage);
        lenient().when(users.findById(7L)).thenReturn(Optional.of(user));
        lenient().when(user.getId()).thenReturn(7L);
        lenient().when(user.getName()).thenReturn("사용자");
        lenient().when(user.getProvider()).thenReturn(AuthProvider.GOOGLE);
        lenient().when(user.getRole()).thenReturn(UserRole.USER);
    }

    @AfterEach void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test void savesOwnUserScopedProfileKey() {
        when(user.getProfileImageUrl()).thenReturn(null);
        beginSynchronization();

        service.updateMyProfile(7L,
                new UpdateMyProfileRequest("새 이름", "profiles/7/avatar.png"));

        verify(user).updateProfileImage("profiles/7/avatar.png");
    }

    @Test void rejectsAnotherUsersProfileKey() {
        assertThatThrownBy(() -> service.updateMyProfile(7L,
                new UpdateMyProfileRequest("사용자", "profiles/8/avatar.png")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본인이 업로드한");
        verify(user, never()).updateProfileImage(anyString());
    }

    @Test void rejectsBlankAndMalformedProfileKeys() {
        assertThatThrownBy(() -> service.updateMyProfile(7L,
                new UpdateMyProfileRequest("사용자", "   ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateMyProfile(7L,
                new UpdateMyProfileRequest("사용자", "profile/avatar.png")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateMyProfile(7L,
                new UpdateMyProfileRequest("사용자", "profiles/7/../8/avatar.png")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void committedReplacementDeletesOnlyOwnedPreviousObject() {
        when(user.getProfileImageUrl()).thenReturn("profiles/7/old.png");
        beginSynchronization();
        service.updateMyProfile(7L,
                new UpdateMyProfileRequest("사용자", "profiles/7/new.png"));

        complete(TransactionSynchronization.STATUS_COMMITTED);

        verify(storage).deleteObject("profiles/7/old.png");
        verify(storage, never()).deleteObject("profiles/7/new.png");
    }

    @Test void neverDeletesPreviousObjectOwnedByAnotherUser() {
        when(user.getProfileImageUrl()).thenReturn("profiles/8/old.png");
        beginSynchronization();
        service.updateMyProfile(7L,
                new UpdateMyProfileRequest("사용자", "profiles/7/new.png"));

        complete(TransactionSynchronization.STATUS_COMMITTED);

        verify(storage, never()).deleteObject(anyString());
    }

    @Test void legacyProfileKeyRemainsReadableAndIsNotDeleted() {
        when(user.getProfileImageUrl()).thenReturn("profile/legacy.png");

        var unchanged = service.updateMyProfile(7L,
                new UpdateMyProfileRequest("이름만 수정", null));

        assertThat(unchanged.profileImageUrl()).isEqualTo("profile/legacy.png");
        verify(user, never()).updateProfileImage(anyString());

        beginSynchronization();
        service.updateMyProfile(7L,
                new UpdateMyProfileRequest("사용자", "profiles/7/new.png"));
        complete(TransactionSynchronization.STATUS_COMMITTED);
        verify(storage, never()).deleteObject("profile/legacy.png");
    }

    @Test void rollbackDeletesOnlyNewOwnedObject() {
        when(user.getProfileImageUrl()).thenReturn("profiles/7/old.png");
        beginSynchronization();
        service.updateMyProfile(7L,
                new UpdateMyProfileRequest("사용자", "profiles/7/new.png"));

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).deleteObject("profiles/7/new.png");
        verify(storage, never()).deleteObject("profiles/7/old.png");
    }

    @Test void profileImageAbsentKeepsExistingFlow() {
        when(user.getProfileImageUrl()).thenReturn(null);

        var response = service.updateMyProfile(7L,
                new UpdateMyProfileRequest("이름 수정", null));

        assertThat(response.profileImageUrl()).isNull();
        verify(user).updateName("이름 수정");
        verify(user, never()).updateProfileImage(anyString());
        verifyNoInteractions(storage);
    }

    private void beginSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private void complete(int status) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
    }
}
