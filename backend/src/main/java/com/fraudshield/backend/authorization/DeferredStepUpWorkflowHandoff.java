package com.fraudshield.backend.authorization;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DeferredStepUpWorkflowHandoff implements StepUpWorkflowHandoff {
    @Override
    public void transactionPending(UUID transactionId, String externalTransactionId) {
        // Milestone 05 will create the simulated OTP request through this boundary.
    }
}
