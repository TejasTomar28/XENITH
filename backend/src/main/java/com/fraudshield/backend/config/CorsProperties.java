package com.fraudshield.backend.config;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "fraudshield.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
    public CorsProperties {
        allowedOrigins = List.copyOf(allowedOrigins);
    }
}
