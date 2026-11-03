# GreenPulse - Claude Project Guide

## Project Overview
GreenPulse is an energy- and carbon-aware workload scheduling system for cloud data centers.

It evaluates workload placement using:
- CPU and memory utilization
- Incremental energy consumption
- Regional carbon intensity
- Server activation and consolidation awareness

The goal is to reduce estimated energy consumption and carbon emissions while maintaining workload scheduling performance.

## Technology Stack
- Java 21
- Spring Boot
- Maven
- MySQL
- Prometheus
- Node Exporter
- VMware
- React and Vite (frontend, subject to source audit)
- Alibaba Cluster Trace 2018

## Project Components
1. Simulation: evaluates scheduling algorithms using workload traces and simulated servers.
2. Backend: Spring Boot application providing monitoring and scheduling APIs.
3. Monitoring: Prometheus and Node Exporter.
4. Frontend: user interface, subject to verification.
5. Research: methodology, experiments and publication documentation.

## Important Rules
- Inspect existing code before modifying it.
- Never invent implemented features or experimental results.
- Never overwrite working code without explaining the changes.
- Distinguish simulation estimates from physical measurements.
- Distinguish fixed simulated carbon factors from live carbon intensity.
- Preserve existing API contracts unless explicitly instructed otherwise.
- Run relevant tests after modifications.
- Explain changes and remaining limitations clearly.

## Verified Simulation Results

For 5,000 tasks at 10% sleep power:
- Baseline energy: 16.781187 kWh
- GreenPulse energy: 12.833293 kWh
- Energy reduction: 23.53%
- Baseline carbon: 13.538258 kg CO2
- GreenPulse carbon: 10.379790 kg CO2
- Carbon reduction: 23.33%

For 5,000 tasks at 20% sleep power:
- Baseline energy: 23.428118 kWh
- GreenPulse energy: 19.914943 kWh
- Energy reduction: 15.00%
- Baseline carbon: 18.978558 kg CO2
- GreenPulse carbon: 16.167791 kg CO2
- Carbon reduction: 14.81%

These are simulation results, not physical data-center measurements.

## Known Limitations
- Regional carbon intensity is currently modeled using fixed values.
- Sleep power is based on an assumed ratio.
- Backend and simulation schedulers are separate implementations.
- Live Prometheus monitoring does not currently provide independent live utilization for all three servers.
- Some ablation configurations produced identical results.
- Independent improvement from consolidation has not yet been demonstrated.

## Research Direction
The target is an IEEE conference paper.

Prioritize reproducibility, fair baseline comparisons, reliable calculations and evidence-backed claims. Do not claim state-of-the-art performance without supporting comparisons.
