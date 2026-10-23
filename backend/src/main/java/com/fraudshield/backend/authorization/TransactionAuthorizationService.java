package com.fraudshield.backend.authorization;

import com.fraudshield.backend.authorization.dto.AuthorizationRequest;
import com.fraudshield.backend.authorization.dto.AuthorizationResponse;
import com.fraudshield.backend.common.exception.ConflictException;
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

    private AuthorizationResponse replay(StoredAuthorization existing, AuthorizationRequest request) {
        if (!existing.matches(request)) {
            throw new ConflictException("Transaction ID is already associated with a different request");
        }
        return existing.response();
    }
}
