// src/main/java/com/example/foodapp/service/CustomOAuth2UserService.java
package com.example.foodapp.service;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    public CustomOAuth2UserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest req) {
        OAuth2User user = super.loadUser(req);
        // nothing to persist here; persistence happens in success handler
        return user;
    }

    // Small helper to read Google payloads safely if you need it elsewhere
    public static GoogleProfile toGoogleProfile(OAuth2User user) {
        Map<String, Object> a = user.getAttributes();
        String email = (String) a.get("email");
        String given = (String) a.get("given_name");
        String family = (String) a.get("family_name");
        String name = (String) a.get("name");
        return new GoogleProfile(email, given, family, name);
    }

    public record GoogleProfile(String email, String givenName, String familyName, String fullName) {}
}
