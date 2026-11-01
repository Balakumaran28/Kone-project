package com.liftcare.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${liftcare.frontend.origin:http://localhost:5173}")
    private String frontendOrigin;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = java.util.Arrays.stream(frontendOrigin.split(","))
                .map(String::trim)
                .toArray(String[]::new);

        boolean hasPattern = java.util.Arrays.stream(origins).anyMatch(o -> o.contains("*"));
        if (hasPattern) {
            registry.addMapping("/api/**")
                    .allowedOriginPatterns(origins)
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("Authorization", "Content-Type", "Accept", "X-Requested-With")
                    .exposedHeaders("Authorization")
                    .allowCredentials(true)
                    .maxAge(3600);
        } else {
            registry.addMapping("/api/**")
                    .allowedOrigins(origins)
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("Authorization", "Content-Type", "Accept", "X-Requested-With")
                    .exposedHeaders("Authorization")
                    .allowCredentials(true)
                    .maxAge(3600);
        }
    }
}
