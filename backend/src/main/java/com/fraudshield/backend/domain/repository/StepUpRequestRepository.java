package com.fraudshield.backend.domain.repository;

import com.fraudshield.backend.domain.model.StepUpRequest;
import com.fraudshield.backend.domain.model.StepUpStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StepUpRequestRepository extends JpaRepository<StepUpRequest, UUID> {
    List<StepUpRequest> findByCustomerUserIdAndStatusOrderByCreatedAtDesc(UUID userId, StepUpStatus status);
    List<StepUpRequest> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAt(StepUpStatus status, OffsetDateTime cutoff);
}
