package com.greenpulse.backend.service;

import org.springframework.stereotype.Service;

@Service
public class MonitoringService {

    private final PrometheusService prometheusService;

    public MonitoringService(PrometheusService prometheusService) {
        this.prometheusService = prometheusService;
    }

    public String getCpuMetrics() {
        return prometheusService.query("node_cpu_seconds_total");
    }

    public String getMemoryTotal() {
        return prometheusService.query("node_memory_MemTotal_bytes");
    }

    public String getMemoryAvailable() {
        return prometheusService.query("node_memory_MemAvailable_bytes");
    }

    public Double getCpuUsage() {
        return prometheusService.getCpuUsage();
    }

    public Double getMemoryUsage() {
        return prometheusService.getMemoryUsage();
    }
}