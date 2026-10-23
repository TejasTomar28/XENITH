package com.fraudshield.backend.workflow;
import java.util.UUID;
public interface OtpNotification { void send(UUID stepUpId, String transactionId, String otp); }
