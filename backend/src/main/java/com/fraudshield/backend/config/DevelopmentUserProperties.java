package com.fraudshield.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fraudshield.dev-users")
public record DevelopmentUserProperties(
        boolean enabled,
        String cardholderUsername,
        String cardholderPassword,
        String analystUsername,
        String analystPassword,
        String managerUsername,
        String managerPassword) {
}
