package com.fraudshield.backend.domain.model;

import com.fraudshield.backend.auth.UserAccount;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "manager_approval_proposals")
public class ManagerApprovalProposal {
    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "alert_id") private FraudAlert alert;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "proposed_by") private UserAccount proposedBy;
    @Column(name = "proposed_at", nullable = false) private OffsetDateTime proposedAt;
    @Column(name = "proposal_comment", nullable = false, length = 2000) private String proposalComment;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ApprovalStatus status;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by") private UserAccount reviewedBy;
    @Column(name = "reviewed_at") private OffsetDateTime reviewedAt;
    @Column(name = "review_comment", length = 2000) private String reviewComment;
    @Version private long version;
    protected ManagerApprovalProposal() {}
}
