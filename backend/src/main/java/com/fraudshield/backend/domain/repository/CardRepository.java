package com.fraudshield.backend.domain.repository;

import com.fraudshield.backend.domain.model.Card;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardRepository extends JpaRepository<Card, String> {
    List<Card> findByCustomerIdOrderById(String customerId);
    List<Card> findByCustomerUserIdOrderById(java.util.UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Card c where c.id = :cardId")
    Optional<Card> lockById(@Param("cardId") String cardId);
}
