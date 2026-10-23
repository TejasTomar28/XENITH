package com.fraudshield.backend.domain.repository;

import com.fraudshield.backend.domain.model.CardTransaction;
import com.fraudshield.backend.domain.model.TransactionChannel;
import com.fraudshield.backend.domain.model.TransactionStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, UUID> {
    Optional<CardTransaction> findByTransactionId(String transactionId);

    List<CardTransaction> findByCardCustomerUserIdOrderByTransactionTimestampDesc(UUID userId);

    @Query("""
            select count(t) from CardTransaction t
            where t.card.id = :cardId
              and t.transactionTimestamp >= :windowStart
              and t.transactionTimestamp < :currentTimestamp
            """)
    long countPriorTransactions(
            @Param("cardId") String cardId,
            @Param("windowStart") OffsetDateTime windowStart,
            @Param("currentTimestamp") OffsetDateTime currentTimestamp);

    @Query("""
            select coalesce(sum(t.amount), 0) from CardTransaction t
            where t.card.id = :cardId and t.status = :approved
              and t.transactionTimestamp >= :istDayStart
              and t.transactionTimestamp < :currentTimestamp
            """)
    BigDecimal sumApprovedBeforeOnIstDay(
            @Param("cardId") String cardId,
            @Param("approved") TransactionStatus approved,
            @Param("istDayStart") OffsetDateTime istDayStart,
            @Param("currentTimestamp") OffsetDateTime currentTimestamp);

    @Query("""
            select avg(t.amount) from CardTransaction t
            where t.card.id = :cardId and t.status = :approved
              and t.transactionTimestamp >= :windowStart
              and t.transactionTimestamp < :currentTimestamp
            """)
    Optional<BigDecimal> averageApprovedAmount(
            @Param("cardId") String cardId,
            @Param("approved") TransactionStatus approved,
            @Param("windowStart") OffsetDateTime windowStart,
            @Param("currentTimestamp") OffsetDateTime currentTimestamp);

    Optional<CardTransaction>
            findFirstByCardIdAndStatusAndChannelInAndTransactionTimestampGreaterThanEqualAndTransactionTimestampLessThanOrderByTransactionTimestampDescTransactionIdDesc(
                    String cardId,
                    TransactionStatus approved,
                    Set<TransactionChannel> physicalChannels,
                    OffsetDateTime windowStart,
                    OffsetDateTime currentTimestamp);

    @Query("""
            select (count(t) > 0) from CardTransaction t
            where t.card.id = :cardId and t.merchant.id = :merchantId
              and t.transactionTimestamp >= :windowStart
              and t.transactionTimestamp < :currentTimestamp
            """)
    boolean existsPriorMerchantTransaction(
            @Param("cardId") String cardId,
            @Param("merchantId") String merchantId,
            @Param("windowStart") OffsetDateTime windowStart,
            @Param("currentTimestamp") OffsetDateTime currentTimestamp);
}
