package com.giftmarket.auth.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthTestController {

    @GetMapping("/login-success")
    public Map<String, Object> loginSuccess(
            @AuthenticationPrincipal OAuth2User oauth2User
    ) {
        return Map.of(
                "email", oauth2User.getAttribute("email"),
                "name", oauth2User.getAttribute("name"),
                "picture", oauth2User.getAttribute("picture")
        );
    }
}