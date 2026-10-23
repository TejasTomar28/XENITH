package com.fraudshield.backend.domain.repository;

import com.fraudshield.backend.domain.model.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, String> {}
