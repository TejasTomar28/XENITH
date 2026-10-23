package com.fraudshield.backend.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "cards")
public class Card {
    @Id @Column(name = "card_id", length = 40) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id") private Customer customer;
    @Column(name = "pan_hash", nullable = false, unique = true, length = 100) private String panHash;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "last_four", nullable = false, length = 4, columnDefinition = "char(4)") private String lastFour;
    @Enumerated(EnumType.STRING) @Column(name = "card_type", nullable = false, length = 8) private CardType cardType;
    @Enumerated(EnumType.STRING) @Column(name = "card_status", nullable = false, length = 8) private CardStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "seed_card_status", nullable = false, length = 8) private CardStatus seedStatus;
    @Column(name = "issue_date", nullable = false) private LocalDate issueDate;
    @Column(name = "expiry_date", nullable = false) private LocalDate expiryDate;
    @Column(name = "home_city", nullable = false, length = 100) private String homeCity;
    @Column(name = "home_country", nullable = false, length = 2) private String homeCountry;
    @Column(name = "daily_limit", nullable = false, precision = 14, scale = 2) private BigDecimal dailyLimit;
    @Column(name = "international_enabled", nullable = false) private boolean internationalEnabled;
    @Version private long version;
    protected Card() {}
    public String getId() { return id; }
    public Customer getCustomer() { return customer; }
    public CardStatus getStatus() { return status; }
    public CardType getCardType() { return cardType; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public String getHomeCity() { return homeCity; }
    public String getHomeCountry() { return homeCountry; }
    public BigDecimal getDailyLimit() { return dailyLimit; }
    public boolean isInternationalEnabled() { return internationalEnabled; }
}
