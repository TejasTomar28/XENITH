package com.fraudshield.backend.fraud;

public class MissingReferenceDataException extends RuntimeException {
    public MissingReferenceDataException(String message) { super(message); }
}
