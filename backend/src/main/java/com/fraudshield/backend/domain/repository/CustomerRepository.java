package com.fraudshield.backend.domain.repository;

import com.fraudshield.backend.domain.model.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, String> {
    Optional<Customer> findByUserId(UUID userId);
    Optional<Customer> findByUsername(String username);
}
