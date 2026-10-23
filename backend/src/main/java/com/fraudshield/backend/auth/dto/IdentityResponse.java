package com.fraudshield.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fraudshield.backend.auth.UserAccount;
import java.util.List;
import java.util.UUID;

public record IdentityResponse(
        @JsonProperty("user_id") UUID userId,
        String username,
        List<String> roles) {

    public static IdentityResponse from(UserAccount user) {
        return new IdentityResponse(user.getId(), user.getUsername(), List.of(user.getRole().name()));
    }
}
