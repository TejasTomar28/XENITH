package com.fraudshield.backend.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "country_risk")
public class CountryRisk {
    @Id @Column(name = "country_code", length = 2) private String countryCode;
    @Column(name = "country_name", nullable = false, length = 100) private String countryName;
    @Column(name = "risk_level", nullable = false, length = 8) private String riskLevel;
    protected CountryRisk() {}
    public String getCountryCode() { return countryCode; }
    public String getCountryName() { return countryName; }
    public String getRiskLevel() { return riskLevel; }
}
