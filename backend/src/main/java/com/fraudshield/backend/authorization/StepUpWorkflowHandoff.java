package com.fraudshield.backend.authorization;

import java.util.UUID;

public interface StepUpWorkflowHandoff {
    void transactionPending(UUID transactionId, String externalTransactionId);
}
