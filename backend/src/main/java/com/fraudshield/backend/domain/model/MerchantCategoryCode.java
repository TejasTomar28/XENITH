package com.fraudshield.backend.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mcc_codes")
public class MerchantCategoryCode {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 4, columnDefinition = "char(4)") private String mcc;
    @Column(nullable = false, length = 255) private String description;
    @Column(nullable = false, length = 100) private String category;
    @Column(name = "high_risk", nullable = false) private boolean highRisk;
    protected MerchantCategoryCode() {}
    public String getMcc() { return mcc; }
    public String getCategory() { return category; }
    public boolean isHighRisk() { return highRisk; }
}
