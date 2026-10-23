package com.fraudshield.backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiErrorFactory {

    private ApiErrorFactory() {
    }

    public static ApiError create(
            HttpServletRequest request,
            String code,
            String message,
            List<FieldViolation> fieldErrors) {
        return new ApiError(
                Instant.now(),
                code,
                message,
                List.copyOf(fieldErrors),
                UUID.randomUUID().toString(),
                request.getRequestURI());
    }
}
