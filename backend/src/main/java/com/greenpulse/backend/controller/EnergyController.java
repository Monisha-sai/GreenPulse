package com.greenpulse.backend.controller;

import com.greenpulse.backend.entity.EnergyLog;
import com.greenpulse.backend.service.EnergyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/energy")
public class EnergyController {

    private final EnergyService energyService;

    public EnergyController(EnergyService energyService) {
        this.energyService = energyService;
    }

    @GetMapping
    public List<EnergyLog> getAllEnergyLogs() {
        return energyService.getAllEnergyLogs();
    }

    @PostMapping
    public EnergyLog createEnergyLog(@RequestBody EnergyLog energyLog) {
        return energyService.saveEnergyLog(energyLog);
    }
}