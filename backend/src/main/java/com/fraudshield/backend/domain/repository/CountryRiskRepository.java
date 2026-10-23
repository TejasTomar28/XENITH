package com.fraudshield.backend.domain.repository;

import com.fraudshield.backend.domain.model.CountryRisk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRiskRepository extends JpaRepository<CountryRisk, String> {}
