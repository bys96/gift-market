package com.giftmarket.auth.service;

import com.giftmarket.auth.oauth.GoogleOAuth2UserInfo;
import com.giftmarket.auth.oauth.OAuth2UserInfo;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest)
            throws OAuth2AuthenticationException {

        log.info("CustomOidcUserService 실행");

        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest
                .getClientRegistration()
                .getRegistrationId();

        OAuth2UserInfo userInfo = createOAuth2UserInfo(
                registrationId,
                oidcUser
        );

        validateUserInfo(userInfo);

        User user = findOrCreateUser(userInfo);

        log.info(
                "Google 로그인 사용자 처리 완료. userId={}, email={}",
                user.getId(),
                user.getEmail()
        );

        return oidcUser;
    }

    private OAuth2UserInfo createOAuth2UserInfo(
            String registrationId,
            OidcUser oidcUser
    ) {
        if ("google".equalsIgnoreCase(registrationId)) {
            return new GoogleOAuth2UserInfo(
                    oidcUser.getAttributes()
            );
        }

        throw new OAuth2AuthenticationException(
                "지원하지 않는 OIDC 제공자입니다: " + registrationId
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

    private User findOrCreateUser(OAuth2UserInfo userInfo) {
        return userRepository
                .findByProviderAndProviderId(
                        AuthProvider.GOOGLE,
                        userInfo.getProviderId()
                )
                .orElseGet(() -> createUser(userInfo));
    }

    private User createUser(OAuth2UserInfo userInfo) {
        User user = User.createOAuthUser(
                userInfo.getEmail(),
                userInfo.getName(),
                userInfo.getProfileImageUrl(),
                AuthProvider.GOOGLE,
                userInfo.getProviderId()
        );

        User savedUser = userRepository.save(user);

        log.info(
                "신규 Google 사용자 저장. userId={}, email={}",
                savedUser.getId(),
                savedUser.getEmail()
        );

        return savedUser;
    }
}