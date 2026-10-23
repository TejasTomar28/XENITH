package com.fraudshield.backend.domain.repository;

import com.fraudshield.backend.domain.model.AuditEvent;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface AuditEventRepository extends Repository<AuditEvent, Long> {
    List<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(String entityType, String entityId);
}
