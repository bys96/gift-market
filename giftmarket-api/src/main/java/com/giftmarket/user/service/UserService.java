package com.giftmarket.user.service;

import com.giftmarket.auth.dto.LoginUserResponse;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.user.dto.UpdateMyProfileRequest;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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

        if (request.profileImageUrl() != null) {
            user.updateProfileImage(request.trimmedProfileImageUrl());
        }

        return LoginUserResponse.from(user);
    }
}