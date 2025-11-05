package com.example.foodapp.config;

import com.example.foodapp.service.AuthSessionSyncInterceptor;
import com.example.foodapp.service.UserService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {


    private final UserService userService;

    public WebConfig(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get("uploads");
        String uploadPath = uploadDir.toFile().getAbsolutePath();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }




    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthSessionSyncInterceptor(userService))
                .addPathPatterns("/**");
    }
}
