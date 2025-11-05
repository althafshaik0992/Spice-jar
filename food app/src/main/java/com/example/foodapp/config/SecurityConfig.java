//package com.example.foodapp.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.annotation.Order;
//import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.web.SecurityFilterChain;
//import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
//
//@Configuration
//public class SecurityConfig {
//
//    @Bean
//    public PasswordEncoder passwordEncoder() {
//        return new BCryptPasswordEncoder();
//    }
//
//    // Only define this if you actually inject AuthenticationManager somewhere
//    @Bean
//    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
//        return config.getAuthenticationManager();
//    }
//
//    /**
//     * Chain 1: ADMIN area only
//     * - Applies to /admin/**
//     * - Requires ROLE_ADMIN
//     * - Uses custom admin login page at /admin/login
//     */
//    @Bean
//    @Order(1)
//    public SecurityFilterChain adminChain(HttpSecurity http) throws Exception {
//        http
//                .securityMatcher("/admin/**")                   // only admin paths
//                .authorizeHttpRequests(auth -> auth
//                        .anyRequest().hasRole("ADMIN")
//                )
//                .formLogin(form -> form
//                        .loginPage("/admin/login")                  // your admin login view
//                        .loginProcessingUrl("/admin/login")         // form POST goes here
//                        .defaultSuccessUrl("/admin", true)
//                        .permitAll()
//                )
//                .logout(logout -> logout
//                        .logoutUrl("/admin/logout")
//                        .logoutSuccessUrl("/admin/login?logout")
//                );
//        // If you have a public POST endpoint like /api/chat, ignore CSRF there (admin chain won't match it,
//        // but keeping the pattern shows how to set this up if needed for admin AJAX).
//        // .csrf(csrf -> csrf.ignoringRequestMatchers("/api/chat"));
//
//        return http.build();
//    }
//
//    /**
//     * Chain 2: Public / user site (default)
//     * - Permit all typical user pages and static assets
//     * - No login page here, so it won't redirect to /admin/login
//     */
//    @Bean
//    @Order(2)
//    public SecurityFilterChain appChain(HttpSecurity http) throws Exception {
//        http
//                .authorizeHttpRequests(auth -> auth
//                        .requestMatchers(
//                                "/", "/about", "/about**",
//                                "/products/**", "/category/**",
//                                "/cart/**", "/add-to-cart", "/order/**",
//                                "/orders/**",
//                                "/api/chat",                   // your chatbot endpoint
//                                "/register", "/login",         // user auth pages if you add them later
//                                "/css/**", "/js/**", "/images/**","/uploads/**","/profile/**","/profile/","/webjars/**","/menu", "/menu/**"
//                        ).permitAll()
//                        .anyRequest().permitAll()         // everything else is public by default
//                )
//                // Keep your OAuth2 login (uses your existing success handler bean)
//                .csrf(csrf -> csrf
//                        .ignoringRequestMatchers(new AntPathRequestMatcher("/api/chat"))
//                )
//                .logout(logout -> logout.permitAll());
//        //.csrf(csrf -> csrf.ignoringRequestMatchers("/api/chat")); // allow chatbot POST without CSRF token if you want
//
//        // No formLogin() here => no redirect to /admin/login for public pages
//        return http.build();
//    }
//}


// src/main/java/com/example/foodapp/config/SecurityConfig.java
package com.example.foodapp.config;

import com.example.foodapp.service.CustomOAuth2UserService;
import com.example.foodapp.service.LoginSuccessHandler;
import com.example.foodapp.service.OAuth2LoginSuccessHandler;
import com.example.foodapp.service.UserService;
import com.example.foodapp.web.SyncSessionUserFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

    private final CustomOAuth2UserService oAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final LoginSuccessHandler loginSuccessHandler;
    private final UserService userService; // <-- needed for SyncSessionUserFilter

    public SecurityConfig(CustomOAuth2UserService oAuth2UserService,
                          OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
                          LoginSuccessHandler loginSuccessHandler,
                          UserService userService) {
        this.oAuth2UserService = oAuth2UserService;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.loginSuccessHandler = loginSuccessHandler;
        this.userService = userService;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /** Admin area */
    @Bean
    @Order(1)
    public SecurityFilterChain adminChain(HttpSecurity http) throws Exception {
        http
                // Only applies to the admin area
                .securityMatcher("/admin/**", "/webjars/**")
                .authorizeHttpRequests(auth -> auth
                        // allow the admin login page + its static assets
                        .requestMatchers(
                                "/admin/login",            // GET login page
                                "/admin/login/**",         // (safety)
                                "/admin/css/**",
                                "/admin/js/**",
                                "/admin/images/**",
                                "/webjars/**"
                        ).permitAll()

                        // everything else in /admin must be ADMIN
                        .requestMatchers("/admin/**").hasRole("ADMIN").anyRequest().permitAll()
                )

                // Admin form login
                .formLogin(form -> form
                        .loginPage("/admin/login")        // your Thymeleaf login page
                        .loginProcessingUrl("/admin/login") // POST target
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/admin", true)
                        .failureUrl("/admin/login?error")
                        .permitAll()
                )

                // Logout
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )

                // Redirect unauthenticated users trying to hit /admin/** to the login page
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> res.sendRedirect("/admin/login"))
                        .accessDeniedHandler((req, res, e) -> res.sendRedirect("/admin/login?denied"))
                )

        // (optional) CSRF: keep it enabled; the login POST is already handled.
        // If you have admin-only AJAX endpoints, you can ignore them here:
        //.csrf(csrf -> csrf.ignoringRequestMatchers("/admin/api/**"))
        ;

        return http.build();
    }

    /** Public/user site */
    @Bean
    @Order(2)
    public SecurityFilterChain appChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/about", "/about**",
                                "/products/**", "/category/**",
                                "/cart/**", "/add-to-cart",
                                "/order/**", "/orders/**",
                                "/payment/**",
                                "/auth/**", "/oauth2/**",
                                "/login", "/logout", "/register",
                                "/forgotPassword", "/forgot-password",
                                "/reset-password", "/reset-password/**",
                                "/api/chat",
                                // ✅ allow the coupon API
                                "/api/cart/coupon/**",
                                "/css/**", "/js/**", "/images/**", "/uploads/**", "/webjars/**",
                                "/menu", "/menu/**"
                        ).permitAll()
                        .anyRequest().permitAll()
                )
                // Regular form-login
                .formLogin(f -> f
                        .loginPage("/login")
                        .successHandler(loginSuccessHandler)
                        .permitAll()
                )
                // Google OAuth2 login
                .oauth2Login(o -> o
                        .loginPage("/login")
                        .userInfoEndpoint(u -> u.userService(oAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                )
                // ✅ Expose CSRF token via cookie so your JS can send it back.
                //    Keep ignoring only /api/chat as you had before.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(new AntPathRequestMatcher("/api/chat"))
                )
                .logout(l -> l.logoutSuccessUrl("/").permitAll());

        // Keep session "USER" in sync after any successful auth
        http.addFilterAfter(new SyncSessionUserFilter(userService), SecurityContextHolderFilter.class);

        return http.build();
    }
}
