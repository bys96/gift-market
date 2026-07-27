package com.giftmarket.auth.oauth;

import java.util.Map;

public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getProviderId() {
        Object id = attributes.get("id");
        return id != null ? id.toString() : null;
    }

    @Override
    public String getEmail() {
        Map<String, Object> kakaoAccount = getKakaoAccount();

        if (kakaoAccount == null) {
            return null;
        }

        Object email = kakaoAccount.get("email");
        return email != null ? email.toString() : null;
    }

    @Override
    public String getName() {
        Map<String, Object> profile = getProfile();

        if (profile == null) {
            return null;
        }

        Object nickname = profile.get("nickname");
        return nickname != null ? nickname.toString() : null;
    }

    @Override
    public String getProfileImageUrl() {
        Map<String, Object> profile = getProfile();

        if (profile == null) {
            return null;
        }

        Object profileImageUrl = profile.get("profile_image_url");

        return profileImageUrl != null
                ? profileImageUrl.toString()
                : null;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getKakaoAccount() {
        Object kakaoAccount = attributes.get("kakao_account");

        if (!(kakaoAccount instanceof Map<?, ?>)) {
            return null;
        }

        return (Map<String, Object>) kakaoAccount;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getProfile() {
        Map<String, Object> kakaoAccount = getKakaoAccount();

        if (kakaoAccount == null) {
            return null;
        }

        Object profile = kakaoAccount.get("profile");

        if (!(profile instanceof Map<?, ?>)) {
            return null;
        }

        return (Map<String, Object>) profile;
    }
}