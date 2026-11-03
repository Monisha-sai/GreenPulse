
package com.greenpulse.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
public class PrometheusService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String prometheusUrl;

    public PrometheusService(
            @Value("${prometheus.url:http://localhost:9090/api/v1/query}")
            String prometheusUrl) {
        this.prometheusUrl = prometheusUrl;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public String query(String metric) {
        URI uri = UriComponentsBuilder
                .fromUriString(prometheusUrl)
                .queryParam("query", metric)
                .build()
                .encode()
                .toUri();

        return restTemplate.getForObject(uri, String.class);
    }

    public Double queryValue(String prometheusQuery) {
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(prometheusUrl)
                    .queryParam("query", prometheusQuery)
                    .build()
                    .encode()
                    .toUri();

            String response =
                    restTemplate.getForObject(uri, String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("data").path("result");

            if (!result.isArray() || result.isEmpty()) {
                throw new RuntimeException(
                        "Prometheus returned no data for query: "
                                + prometheusQuery
                );
            }

            JsonNode value = result.get(0).path("value").get(1);

            if (value == null || !value.isNumber() && !value.isTextual()) {
                throw new RuntimeException(
                        "Invalid Prometheus response for query: "
                                + prometheusQuery
                );
            }

            return value.asDouble();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to get value from Prometheus", e
            );
        }
    }

    public Double getCpuUsage() {
        String query =
                "100 * (1 - avg by (instance) " +
                "(rate(node_cpu_seconds_total{mode=\"idle\"}[1m])))";

        return queryValue(query);
    }

    public Double getMemoryUsage() {
        String query =
                "100 * (1 - node_memory_MemAvailable_bytes " +
                "/ node_memory_MemTotal_bytes)";

        return queryValue(query);
    }
}
