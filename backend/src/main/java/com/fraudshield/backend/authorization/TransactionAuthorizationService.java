package com.fraudshield.backend.authorization;

import com.fraudshield.backend.authorization.dto.AuthorizationRequest;
import com.fraudshield.backend.authorization.dto.AuthorizationResponse;
import com.fraudshield.backend.common.exception.ConflictException;
import com.fraudshield.backend.domain.model.TransactionSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class TransactionAuthorizationService {
    private final AuthorizationRecordStore records;
    private final TransactionAuthorizationProcessor processor;

    public TransactionAuthorizationService(
            AuthorizationRecordStore records,
            TransactionAuthorizationProcessor processor) {
        this.records = records;
        this.processor = processor;
    }

    public AuthorizationResponse authorize(AuthorizationRequest request) {
        StoredAuthorization existing = records.find(request.transactionId()).orElse(null);
        if (existing != null) return replay(existing, request);
        try {
            return processor.process(request);
        } catch (DataIntegrityViolationException exception) {
            StoredAuthorization winner = records.find(request.transactionId()).orElseThrow(() -> exception);
            return replay(winner, request);
        }
    }

    public AuthorizationResponse authorizeBatch(AuthorizationRequest request,TransactionSource source) {
        StoredAuthorization existing=records.find(request.transactionId()).orElse(null);
        if(existing!=null)return replay(existing,request);
        try{return processor.processBatch(request,source);}catch(DataIntegrityViolationException exception){
            StoredAuthorization winner=records.find(request.transactionId()).orElseThrow(()->exception);return replay(winner,request);
        }
    }

    private AuthorizationResponse replay(StoredAuthorization existing, AuthorizationRequest request) {
        if (!existing.matches(request)) {
            throw new ConflictException("Transaction ID is already associated with a different request");
        }
        if (existing.response() == null) {
            throw new ConflictException("Transaction ID already exists without a replayable authorization response");
        }
        return existing.response();
    }
}
