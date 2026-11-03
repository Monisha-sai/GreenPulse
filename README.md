# GreenPulse 🌱

### Energy- and Carbon-Aware Workload Scheduling for Cloud Data Centers

GreenPulse is a research project focused on reducing estimated energy consumption and carbon emissions through intelligent workload placement in cloud data-center environments.

## Project Objective

To investigate whether combining resource-aware scheduling, incremental energy estimation, regional carbon intensity and server-state awareness can reduce energy consumption and carbon emissions without significantly affecting workload scheduling performance.

## Core Features

- CPU and memory-aware workload placement
- Incremental energy estimation
- Carbon-aware server selection
- Server activation and consolidation awareness
- Prometheus-based infrastructure monitoring
- Workload trace-driven simulation
- Experimental comparison of scheduling configurations

## Technology Stack

| Component | Technology |
|---|---|
| Backend | Java 21, Spring Boot |
| Database | MySQL |
| Monitoring | Prometheus, Node Exporter |
| Infrastructure | VMware, Ubuntu Server |
| Frontend | React, Vite (subject to verification) |
| Dataset | Alibaba Cluster Trace 2018 |

## Architecture

```text
VMware
   |
Ubuntu Server
   |
Node Exporter
   |
Prometheus
   |
Spring Boot Backend
   |
Monitoring and Scheduling
```

The simulation environment separately evaluates workload scheduling algorithms using trace-driven experiments.

## Experimental Results

For 5,000 tasks at 10% assumed sleep power:

| Metric | Baseline | GreenPulse |
|---|---:|---:|
| Energy (kWh) | 16.781187 | 12.833293 |
| Carbon (kg CO2) | 13.538258 | 10.379790 |
| Scheduling success | 94.44% | 94.46% |

Reported reductions:
- Energy: 23.53%
- Carbon emissions: 23.33%

These results come from simulation experiments and are not measurements from physical data centers.

## Repository Structure

The repository will contain:

- `backend/` - Spring Boot backend
- `frontend/` - Frontend application, subject to source verification
- `simulation/` - Scheduling simulation
- `datasets/` - Workload datasets
- `experiments/` - Experimental outputs and summaries
- `research/` - Research notes and literature review
- `docs/` - Technical documentation
- `infrastructure/` - Monitoring and VM configuration
- `testing/` - Test cases

## Research Status

GreenPulse is under active development. The simulation, backend and experimental implementations are being consolidated and audited.

## Project Instructions

See [`CLAUDE.md`](CLAUDE.md) for project-specific instructions when using Claude.

## Research Disclaimer

All reported energy and carbon values depend on the simulation's power model, regional carbon factors, workload trace and sleep-power assumptions. They should be interpreted within those experimental conditions.
