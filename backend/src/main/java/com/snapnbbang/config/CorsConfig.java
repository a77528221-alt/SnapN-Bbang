package com.snapnbbang.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String frontendOrigin;

    public CorsConfig(@Value("${app.frontend-origin}") String frontendOrigin) {
        if (!StringUtils.hasText(frontendOrigin) || frontendOrigin.contains("*")) {
            throw new IllegalArgumentException("FRONTEND_ORIGIN must be a single explicit origin without wildcards.");
        }
        this.frontendOrigin = frontendOrigin.trim();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .allowCredentials(false)
                .maxAge(3600);
    }
}

