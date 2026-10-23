package com.fraudshield.backend.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transactions")
public class CardTransaction {
    @Id private UUID id;
    @Column(name = "txn_id", nullable = false, unique = true, length = 10) private String transactionId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "card_id") private Card card;
    @Column(name = "txn_ts", nullable = false) private OffsetDateTime transactionTimestamp;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 3, columnDefinition = "char(3)") private String currency;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "merchant_id") private Merchant merchant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mcc") private MerchantCategoryCode mcc;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private TransactionChannel channel;
    @Column(name = "txn_city", nullable = false, length = 100) private String transactionCity;
    @Column(name = "txn_country", nullable = false, length = 2) private String transactionCountry;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private TransactionStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "original_decision", length = 8) private TransactionDecision originalDecision;
    @Enumerated(EnumType.STRING) @Column(name = "original_status", nullable = false, length = 24) private TransactionStatus originalStatus;
    @Enumerated(EnumType.STRING) @Column(name = "transaction_source", nullable = false, length = 16) private TransactionSource source;
    @Column(nullable = false) private boolean resettable;
    @Column(name = "processed_at", nullable = false) private OffsetDateTime processedAt;
    @Version private long version;
    protected CardTransaction() {}
    public UUID getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public OffsetDateTime getTransactionTimestamp() { return transactionTimestamp; }
    public TransactionStatus getStatus() { return status; }
    public String getTransactionCity() { return transactionCity; }
    public String getTransactionCountry() { return transactionCountry; }
    public TransactionChannel getChannel() { return channel; }
}
