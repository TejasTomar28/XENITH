package com.fraudshield.backend.domain.repository;

import com.fraudshield.backend.domain.model.ApprovalStatus;
import com.fraudshield.backend.domain.model.ManagerApprovalProposal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagerApprovalProposalRepository extends JpaRepository<ManagerApprovalProposal, UUID> {
    List<ManagerApprovalProposal> findByStatusOrderByProposedAt(ApprovalStatus status);
}
