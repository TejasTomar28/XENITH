package com.fraudshield.backend.domain.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "step_up_requests")
public class StepUpRequest {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "transaction_id") private CardTransaction transaction;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id") private Customer customer;
    @Column(name = "otp_hash", nullable = false, length = 100) private String otpHash;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private StepUpStatus status;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "resolved_at") private OffsetDateTime resolvedAt;
    @Version private long version;
    protected StepUpRequest() {}
    public UUID getId() { return id; }
}
