package com.fraudshield.backend.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id") private Customer customer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "transaction_id") private CardTransaction transaction;
    @Column(name = "notification_type", nullable = false, length = 64) private String notificationType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private JsonNode payload;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "delivered_at") private OffsetDateTime deliveredAt;
    protected NotificationOutbox() {}
}
