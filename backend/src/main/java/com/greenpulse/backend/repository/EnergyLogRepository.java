package com.greenpulse.backend.repository;

import com.greenpulse.backend.entity.EnergyLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnergyLogRepository extends JpaRepository<EnergyLog, Long> {
}
