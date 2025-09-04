package com.example.foodapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Only define this if you actually inject AuthenticationManager somewhere
    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Chain 1: ADMIN area only
     * - Applies to /admin/**
     * - Requires ROLE_ADMIN
     * - Uses custom admin login page at /admin/login
     */
    @Bean
    @Order(1)
    public SecurityFilterChain adminChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/admin/**")                   // only admin paths
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasRole("ADMIN")
                )
                .formLogin(form -> form
                        .loginPage("/admin/login")                  // your admin login view
                        .loginProcessingUrl("/admin/login")         // form POST goes here
                        .defaultSuccessUrl("/admin", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout")
                );
                // If you have a public POST endpoint like /api/chat, ignore CSRF there (admin chain won't match it,
                // but keeping the pattern shows how to set this up if needed for admin AJAX).
               // .csrf(csrf -> csrf.ignoringRequestMatchers("/api/chat"));

        return http.build();
    }

    /**
     * Chain 2: Public / user site (default)
     * - Permit all typical user pages and static assets
     * - No login page here, so it won't redirect to /admin/login
     */
    @Bean
    @Order(2)
    public SecurityFilterChain appChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/about", "/about**",
                                "/products/**", "/category/**",
                                "/cart/**", "/add-to-cart", "/order/**",
                                "/orders/**",
                                "/api/chat",                   // your chatbot endpoint
                                "/register", "/login",         // user auth pages if you add them later
                                "/css/**", "/js/**", "/images/**","/uploads/**","/profile/**","/profile/","/webjars/**","/menu", "/menu/**"
                        ).permitAll()
                        .anyRequest().permitAll()         // everything else is public by default
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(new AntPathRequestMatcher("/api/chat"))
                )
                .logout(logout -> logout.permitAll());
                //.csrf(csrf -> csrf.ignoringRequestMatchers("/api/chat")); // allow chatbot POST without CSRF token if you want

        // No formLogin() here => no redirect to /admin/login for public pages
        return http.build();
    }
}
