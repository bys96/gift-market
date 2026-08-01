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
            String previousProfileImageKey =
                    user.getProfileImageUrl();

            user.updateProfileImage(newProfileImageKey);

            if (!newProfileImageKey.equals(previousProfileImageKey)) {
                registerProfileImageCleanup(
                        previousProfileImageKey,
                        newProfileImageKey
                );
            }
        }

        return LoginUserResponse.from(user);
    }

    private void registerProfileImageCleanup(
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
                                    previousProfileImageKey
                            );
                            return;
                        }

                        log.info(
                                "DB 롤백: 새 프로필 이미지 삭제. objectKey={}",
                                newProfileImageKey
                        );

                        deleteManagedProfileObject(
                                newProfileImageKey
                        );
                    }
                }
        );
    }

    private void deleteManagedProfileObject(String objectKey) {
        if (objectKey == null
                || objectKey.isBlank()
                || !objectKey.startsWith("profile/")) {
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
}