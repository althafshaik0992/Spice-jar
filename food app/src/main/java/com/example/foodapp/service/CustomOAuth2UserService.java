// src/main/java/com/example/foodapp/service/CustomOAuth2UserService.java
package com.example.foodapp.service;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.HashMap;
import java.util.Map;

public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    public CustomOAuth2UserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest req) throws OAuth2AuthenticationException {
        OAuth2User delegate = super.loadUser(req);

        // Make a mutable copy so we can enrich it with the provider id.
        Map<String, Object> attrs = new HashMap<>(delegate.getAttributes());
        attrs.put("registrationId", req.getClientRegistration().getRegistrationId()); // "google" | "facebook"

        String nameAttrKey = req.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName(); // "sub" for Google, "id" for Facebook (by default)

        return new DefaultOAuth2User(delegate.getAuthorities(), attrs, nameAttrKey);
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
