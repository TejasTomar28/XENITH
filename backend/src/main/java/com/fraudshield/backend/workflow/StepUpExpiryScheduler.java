package com.fraudshield.backend.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StepUpExpiryScheduler {
    private static final Logger LOG=LoggerFactory.getLogger(StepUpExpiryScheduler.class);
    private final StepUpVerificationService service;
    public StepUpExpiryScheduler(StepUpVerificationService service){this.service=service;}
    @Scheduled(fixedDelayString="${fraudshield.step-up.expiry-scan-ms:60000}")
    public void expirePending(){
        for(var id:service.expiredIds()){
            try{service.expireOne(id);}catch(Exception exception){LOG.error("Failed to expire step_up_id={}; it will be retried",id,exception);}
        }
    }
}
