package com.fraudshield.backend.domain.repository;

import com.fraudshield.backend.domain.model.AlertStatus;
import com.fraudshield.backend.domain.model.FraudAlert;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {
    List<FraudAlert> findByStatusInOrderByPriorityAscSlaDueAtAsc(Collection<AlertStatus> statuses);
    List<FraudAlert> findByAssignedAnalystIsNullAndStatusOrderByPriorityAscSlaDueAtAsc(AlertStatus status);
    List<FraudAlert> findByAssignedAnalystIdOrderByPriorityAscSlaDueAtAsc(UUID analystId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from FraudAlert a where a.id = :alertId")
    Optional<FraudAlert> lockById(@Param("alertId") UUID alertId);
}
