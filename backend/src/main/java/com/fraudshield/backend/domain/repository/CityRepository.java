package com.fraudshield.backend.domain.repository;

import com.fraudshield.backend.domain.model.City;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, String> {}
