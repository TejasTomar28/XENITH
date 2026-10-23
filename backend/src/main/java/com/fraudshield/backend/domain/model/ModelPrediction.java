package com.fraudshield.backend.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "model_predictions")
public class ModelPrediction {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "transaction_id") private CardTransaction transaction;
    @Column(name = "model_version", length = 128) private String modelVersion;
    @Column(name = "model_status", nullable = false, length = 24) private String modelStatus;
    @Column(name = "fraud_score", precision = 7, scale = 6) private BigDecimal fraudScore;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "top_factors", nullable = false, columnDefinition = "jsonb") private JsonNode topFactors;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "feature_snapshot", nullable = false, columnDefinition = "jsonb") private JsonNode featureSnapshot;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    protected ModelPrediction() {}
}
