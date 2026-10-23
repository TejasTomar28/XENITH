package com.fraudshield.backend.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "rule_configuration_versions")
public class RuleConfigurationVersion {
    @Id private UUID id;
    @Column(name = "source_file", nullable = false, length = 255) private String sourceFile;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "source_sha256", nullable = false, unique = true, length = 64, columnDefinition = "char(64)") private String sourceSha256;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private JsonNode configuration;
    @Column(nullable = false) private boolean active;
    @Column(name = "imported_at", nullable = false) private OffsetDateTime importedAt;
    @Column(name = "imported_by", nullable = false, length = 100) private String importedBy;
    protected RuleConfigurationVersion() {}
}
