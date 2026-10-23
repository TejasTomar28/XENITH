package com.fraudshield.backend.security;

public class InvalidJwtException extends RuntimeException {

    public InvalidJwtException() {
        super("The access token is invalid or expired");
    }
}
