// src/main/java/com/example/foodapp/config/AppAuthBeans.java
package com.example.foodapp.config;

import com.example.foodapp.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppAuthBeans {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Expose these as beans so SecurityConfig can @Autowired them
    @Bean
    public CustomOAuth2UserService customOAuth2UserService(UserService userService) {
        return new CustomOAuth2UserService(userService);
    }

    @Bean
    public LoginSuccessHandler loginSuccessHandler(UserService userService) {
        return new LoginSuccessHandler(userService);
    }

    @Bean
    public OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler(UserService userService, EmailServiceWelcome emailServiceWelcome) {
        return new OAuth2LoginSuccessHandler(userService, emailServiceWelcome);
    }
}
