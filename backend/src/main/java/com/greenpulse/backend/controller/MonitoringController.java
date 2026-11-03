package com.greenpulse.backend.controller;

import com.greenpulse.backend.service.MonitoringService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final MonitoringService monitoringService;

    public MonitoringController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/cpu")
    public String getCpuMetrics() {
        return monitoringService.getCpuMetrics();
    }

    @GetMapping("/memory/total")
    public String getMemoryTotal() {
        return monitoringService.getMemoryTotal();
    }

    @GetMapping("/memory/available")
    public String getMemoryAvailable() {
        return monitoringService.getMemoryAvailable();
    }

    @GetMapping("/cpu/usage")
    public Double getCpuUsage() {
        return monitoringService.getCpuUsage();
    }

    @GetMapping("/memory/usage")
    public Double getMemoryUsage() {
        return monitoringService.getMemoryUsage();
    }
}