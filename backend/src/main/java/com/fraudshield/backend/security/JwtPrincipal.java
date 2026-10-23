package com.fraudshield.backend.security;

import com.fraudshield.backend.auth.ApplicationRole;

public record JwtPrincipal(String username, ApplicationRole role) {
}
