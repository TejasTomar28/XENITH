package com.fraudshield.backend.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transaction_features")
public class TransactionFeature {
    @Id @Column(name = "transaction_id") private UUID transactionId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId @JoinColumn(name = "transaction_id") private CardTransaction transaction;
    @Column(name = "velocity_10m") private Integer velocity10Minutes;
    @Column(name = "velocity_24h") private Integer velocity24Hours;
    @Column(name = "approved_today", precision = 16, scale = 2) private BigDecimal approvedToday;
    @Column(name = "avg_amount_30d", precision = 16, scale = 6) private BigDecimal averageAmount30Days;
    @Column(name = "speed_kmph") private Double speedKilometresPerHour;
    @Column(name = "new_merchant") private Boolean newMerchant;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "feature_snapshot", nullable = false, columnDefinition = "jsonb") private JsonNode featureSnapshot;
    @Column(name = "configuration_version", length = 128) private String configurationVersion;
    protected TransactionFeature() {}
}
