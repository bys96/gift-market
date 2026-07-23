package com.giftmarket.auth.oauth;

import java.util.Map;

public interface OAuth2UserInfo {

    String getProviderId();

    String getEmail();

    String getName();

    String getProfileImageUrl();

    Map<String, Object> getAttributes();
}