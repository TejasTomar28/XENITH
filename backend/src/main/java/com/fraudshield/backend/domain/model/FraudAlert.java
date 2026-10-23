package com.fraudshield.backend.domain.model;

import com.fraudshield.backend.auth.UserAccount;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fraud_alerts")
public class FraudAlert {
    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "transaction_id") private CardTransaction transaction;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private AlertPriority priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AlertStatus status;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assigned_analyst_id") private UserAccount assignedAnalyst;
    @Column(name = "sla_due_at", nullable = false) private OffsetDateTime slaDueAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "closed_at") private OffsetDateTime closedAt;
    @Column(name = "outcome_comment", length = 2000) private String outcomeComment;
    @Version private long version;
    protected FraudAlert() {}
    public UUID getId() { return id; }
}
