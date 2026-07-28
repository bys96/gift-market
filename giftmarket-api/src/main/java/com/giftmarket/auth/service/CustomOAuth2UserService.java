package com.giftmarket.auth.service;

import com.giftmarket.auth.oauth.KakaoOAuth2UserInfo;
import com.giftmarket.auth.oauth.OAuth2UserInfo;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {

        log.info("CustomOAuth2UserService 실행");

        OAuth2User oauth2User = super.loadUser(userRequest);

        String registrationId = userRequest
                .getClientRegistration()
                .getRegistrationId();

        OAuth2UserInfo userInfo = createOAuth2UserInfo(
                registrationId,
                oauth2User
        );

        validateUserInfo(userInfo);

        AuthProvider provider = resolveProvider(registrationId);

        User user = findOrCreateUser(provider, userInfo);

        log.info(
                "{} 로그인 사용자 처리 완료. userId={}, email={}",
                provider,
                user.getId(),
                user.getEmail()
        );

        return oauth2User;
    }

    private OAuth2UserInfo createOAuth2UserInfo(
            String registrationId,
            OAuth2User oauth2User
    ) {
        if ("kakao".equalsIgnoreCase(registrationId)) {
            return new KakaoOAuth2UserInfo(
                    oauth2User.getAttributes()
            );
        }

        throw new OAuth2AuthenticationException(
                "지원하지 않는 OAuth2 제공자입니다: " + registrationId
        );
    }

    private AuthProvider resolveProvider(String registrationId) {
        if ("kakao".equalsIgnoreCase(registrationId)) {
            return AuthProvider.KAKAO;
        }

        throw new OAuth2AuthenticationException(
                "지원하지 않는 OAuth2 제공자입니다: " + registrationId
        );
    }

    private void validateUserInfo(OAuth2UserInfo userInfo) {
        if (userInfo.getProviderId() == null
                || userInfo.getProviderId().isBlank()) {
            throw new OAuth2AuthenticationException(
                    "OAuth2 제공자 식별자가 없습니다."
            );
        }

        if (userInfo.getName() == null
                || userInfo.getName().isBlank()) {
            throw new OAuth2AuthenticationException(
                    "OAuth2 사용자 이름이 없습니다."
            );
        }
    }

    private User findOrCreateUser(
            AuthProvider provider,
            OAuth2UserInfo userInfo
    ) {
        return userRepository
                .findByProviderAndProviderId(
                        provider,
                        userInfo.getProviderId()
                )
                .orElseGet(() -> createUser(
                        provider,
                        userInfo
                ));
    }

    private User createUser(
            AuthProvider provider,
            OAuth2UserInfo userInfo
    ) {
        User user = User.createOAuthUser(
                userInfo.getEmail(),
                userInfo.getName(),
                userInfo.getProfileImageUrl(),
                provider,
                userInfo.getProviderId()
        );

        User savedUser = userRepository.save(user);

        log.info(
                "신규 {} 사용자 저장. userId={}, email={}",
                provider,
                savedUser.getId(),
                savedUser.getEmail()
        );

        return savedUser;
    }
}