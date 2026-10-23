package com.fraudshield.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "fraudshield.security")
public record SecurityProperties(@Valid Jwt jwt, @Valid Gateway gateway) {

    public record Jwt(
            @NotBlank @Size(min = 32) String secret,
            @NotBlank String issuer,
            @Min(60) @Max(86_400) long expirationSeconds) {
    }

    public record Gateway(@NotBlank @Size(min = 24) String apiKey) {
    }
}
