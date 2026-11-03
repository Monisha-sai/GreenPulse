package com.greenpulse.backend.controller;

import com.greenpulse.backend.service.PrometheusService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prometheus")
public class PrometheusController {

    private final PrometheusService prometheusService;

    public PrometheusController(PrometheusService prometheusService) {
        this.prometheusService = prometheusService;
    }

    @GetMapping("/cpu")
    public String getCpuMetrics() {
        return prometheusService.query("node_cpu_seconds_total");
    }

    @GetMapping("/memory/total")
    public String getMemoryTotal() {
        return prometheusService.query("node_memory_MemTotal_bytes");
    }

    @GetMapping("/memory/available")
    public String getMemoryAvailable() {
        return prometheusService.query("node_memory_MemAvailable_bytes");
    }
}
