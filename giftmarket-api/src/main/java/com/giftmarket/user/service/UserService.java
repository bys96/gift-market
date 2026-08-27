package com.giftmarket.user.service;

import com.giftmarket.auth.dto.LoginUserResponse;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.user.dto.UpdateMyProfileRequest;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.giftmarket.global.storage.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StorageService storageService;

    @Transactional
    public LoginUserResponse updateMyProfile(
            Long userId,
            UpdateMyProfileRequest request
    ) {
        if (userId == null) {
            throw new AuthenticationException(
                    "인증이 필요합니다."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException(
                        "사용자를 찾을 수 없습니다."
                ));

        user.updateName(request.trimmedName());

        String newProfileImageKey =
                request.trimmedProfileImageUrl();

        if (newProfileImageKey != null) {
            validateOwnedProfileImageKey(userId, newProfileImageKey);

            String previousProfileImageKey =
                    user.getProfileImageUrl();

            user.updateProfileImage(newProfileImageKey);

            if (!newProfileImageKey.equals(previousProfileImageKey)) {
                registerProfileImageCleanup(
                        userId,
                        previousProfileImageKey,
                        newProfileImageKey
                );
            }
        }

        return LoginUserResponse.from(user);
    }

    private void registerProfileImageCleanup(
            Long userId,
            String previousProfileImageKey,
            String newProfileImageKey
    ) {
        log.info(
                "프로필 이미지 정리 등록. previous={}, new={}",
                previousProfileImageKey,
                newProfileImageKey
        );

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {
                        log.info(
                                "프로필 이미지 트랜잭션 완료. status={}, previous={}, new={}",
                                status,
                                previousProfileImageKey,
                                newProfileImageKey
                        );

                        if (status == STATUS_COMMITTED) {
                            log.info(
                                    "DB 커밋 성공: 기존 프로필 이미지 삭제. objectKey={}",
                                    previousProfileImageKey
                            );

                            deleteManagedProfileObject(
                                    userId,
                                    previousProfileImageKey
                            );
                            return;
                        }

                        log.info(
                                "DB 롤백: 새 프로필 이미지 삭제. objectKey={}",
                                newProfileImageKey
                        );

                        deleteManagedProfileObject(
                                userId,
                                newProfileImageKey
                        );
                    }
                }
        );
    }

    private void deleteManagedProfileObject(
            Long userId,
            String objectKey
    ) {
        if (!isOwnedProfileImageKey(userId, objectKey)) {
            return;
        }

        try {
            storageService.deleteObject(objectKey);
        } catch (Exception exception) {
            // DB 트랜잭션 결과에는 영향을 주지 않고 로그만 남긴다.
            log.error(
                    "프로필 이미지 삭제 실패. objectKey={}",
                    objectKey,
                    exception
            );
        }
    }

    private void validateOwnedProfileImageKey(
            Long userId,
            String objectKey
    ) {
        if (!isOwnedProfileImageKey(userId, objectKey)) {
            throw new IllegalArgumentException(
                    "본인이 업로드한 프로필 이미지만 사용할 수 있습니다."
            );
        }
    }

    private boolean isOwnedProfileImageKey(
            Long userId,
            String objectKey
    ) {
        if (userId == null || objectKey == null || objectKey.isBlank()) {
            return false;
        }

        String expectedPrefix = "profiles/" + userId + "/";

        if (!objectKey.startsWith(expectedPrefix)) {
            return false;
        }

        String fileName = objectKey.substring(expectedPrefix.length());

        return !fileName.isBlank()
                && !fileName.contains("/")
                && !fileName.contains("\\")
                && !fileName.contains("..");
    }
}
