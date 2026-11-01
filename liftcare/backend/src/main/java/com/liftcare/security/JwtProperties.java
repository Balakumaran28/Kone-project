package com.liftcare.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class JwtProperties {

    private static final Logger log = LoggerFactory.getLogger(JwtProperties.class);

    @Value("${liftcare.jwt.secret:liftcare-development-secret-key-must-be-at-least-256-bits-long-32-bytes}")
    private String secret;

    @Value("${liftcare.jwt.expiration-ms:86400000}")
    private long expirationMs;

    @Value("${liftcare.environment:development}")
    private String environment;

    @PostConstruct
    public void validate() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            String errorMsg = "JWT signing key must be at least 256 bits (32 bytes). Current length: " + keyBytes.length;
            if ("production".equalsIgnoreCase(environment)) {
                throw new IllegalStateException("FATAL: Insecure JWT configuration in production: " + errorMsg);
            } else {
                log.warn("JWT secret key is shorter than recommended 256 bits. Suitable for dev only: {}", errorMsg);
            }
        }
    }

    public String getSecret() {
        return secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
