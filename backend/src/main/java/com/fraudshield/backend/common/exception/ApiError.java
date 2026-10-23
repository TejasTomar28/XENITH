package com.fraudshield.backend.common.exception;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        String code,
        String message,
        @JsonProperty("field_errors") List<FieldViolation> fieldErrors,
        @JsonProperty("request_id") String requestId,
        String path) {
}
