package com.giftmarket.auth.oauth;

import java.util.Map;

public class GoogleOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getProviderId() {
        return getStringAttribute("sub");
    }

    @Override
    public String getEmail() {
        return getStringAttribute("email");
    }

    @Override
    public String getName() {
        return getStringAttribute("name");
    }

    @Override
    public String getProfileImageUrl() {
        return getStringAttribute("picture");
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    private String getStringAttribute(String key) {
        Object value = attributes.get(key);
        return value != null ? value.toString() : null;
    }
}