package com.fraudshield.backend.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "merchants")
public class Merchant {
    @Id @Column(name = "merchant_id", length = 40) private String id;
    @Column(name = "merchant_name", nullable = false, length = 255) private String name;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mcc") private MerchantCategoryCode mcc;
    @Column(name = "mcc_description", nullable = false, length = 255) private String mccDescription;
    @Column(nullable = false, length = 100) private String city;
    @Column(nullable = false, length = 2) private String country;
    @Column(name = "merchant_risk", nullable = false, length = 16) private String risk;
    protected Merchant() {}
    public String getId() { return id; }
    public String getName() { return name; }
    public MerchantCategoryCode getMcc() { return mcc; }
    public String getRisk() { return risk; }
}
