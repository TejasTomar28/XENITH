package com.fraudshield.backend.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "entity_type", nullable = false, length = 64) private String entityType;
    @Column(name = "entity_id", nullable = false, length = 128) private String entityId;
    @Column(name = "actor_id", nullable = false, length = 128) private String actorId;
    @Column(name = "actor_role", nullable = false, length = 32) private String actorRole;
    @Column(name = "occurred_at", nullable = false) private OffsetDateTime occurredAt;
    @Column(nullable = false, length = 100) private String action;
    @Column(name = "from_status", length = 64) private String fromStatus;
    @Column(name = "to_status", length = 64) private String toStatus;
    @Column(length = 2000) private String comment;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "before_value", columnDefinition = "jsonb") private JsonNode beforeValue;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "after_value", columnDefinition = "jsonb") private JsonNode afterValue;
    @Column(nullable = false) private boolean resettable;
    protected AuditEvent() {}
    public Long getId() { return id; }
}
