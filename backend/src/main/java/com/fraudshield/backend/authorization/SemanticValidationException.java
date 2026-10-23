package com.fraudshield.backend.authorization;

import com.fraudshield.backend.common.exception.FieldViolation;
import java.util.List;

public class SemanticValidationException extends RuntimeException {
    private final List<FieldViolation> violations;

    public SemanticValidationException(String field, String message) {
        super("Request validation failed");
        this.violations = List.of(new FieldViolation(field, message));
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
