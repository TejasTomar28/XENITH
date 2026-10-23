package com.fraudshield.backend.workflow;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RestrictedDemoOtpLogger implements OtpNotification {
    private static final Logger LOG = LoggerFactory.getLogger("fraudshield.demo.otp.restricted");
    @Override public void send(UUID stepUpId, String transactionId, String otp) {
        LOG.info("RESTRICTED DEMO OTP step_up_id={} txn_id={} otp={}", stepUpId, transactionId, otp);
    }
}
