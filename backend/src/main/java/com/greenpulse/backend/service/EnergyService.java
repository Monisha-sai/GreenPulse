package com.greenpulse.backend.service;

import com.greenpulse.backend.entity.EnergyLog;
import com.greenpulse.backend.repository.EnergyLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EnergyService {

    private final EnergyLogRepository energyLogRepository;

    public EnergyService(EnergyLogRepository energyLogRepository) {
        this.energyLogRepository = energyLogRepository;
    }

    public List<EnergyLog> getAllEnergyLogs() {
        return energyLogRepository.findAll();
    }

    public EnergyLog saveEnergyLog(EnergyLog energyLog) {
        return energyLogRepository.save(energyLog);
    }
}
