package com.fraudshield.backend.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;

@Entity
@Table(name = "cities")
public class City {
    @Id @Column(length = 100) private String city;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "country_code") private CountryRisk country;
    @Column(nullable = false) private double latitude;
    @Column(nullable = false) private double longitude;
    @Column(name = "city_tier", nullable = false, length = 16) private String cityTier;
    protected City() {}
    public String getCity() { return city; }
    public CountryRisk getCountry() { return country; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getCityTier() { return cityTier; }
}
