package com.fraudshield.backend.fraud;

public class MissingRuleConfigurationException extends RuntimeException {
    public MissingRuleConfigurationException(String message) { super(message); }
    public MissingRuleConfigurationException(String message, Throwable cause) { super(message, cause); }
}
