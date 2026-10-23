package com.fraudshield.backend.authorization.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fraudshield.ai")
public record AiClientProperties(
        @NotBlank String predictionUrl,
        String internalApiKey,
        @Min(1) @Max(150) int timeoutMs) {}
