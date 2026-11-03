# GreenPulse System Architecture

## 1. Overview

GreenPulse is a research project exploring energy- and carbon-aware workload scheduling in cloud data-center environments.

The project currently consists of two main technical implementations:

1. A trace-driven scheduling simulation.
2. A Spring Boot backend integrated with Prometheus-based infrastructure monitoring.

These implementations are related but are not the same execution environment.

## 2. Simulation Architecture

The simulation evaluates workload placement using:

- CPU resource feasibility
- Memory resource feasibility
- Incremental energy estimation
- Regional carbon intensity
- Server activation and consolidation awareness

### Main Source Files

| File | Responsibility |
|---|---|
| Scheduler.java | Main scheduling implementation |
| BaselineScheduler.java | CPU and memory baseline |
| AblationScheduler.java | Experimental model configurations |
| AblationScheduler_ConsolidationTest.java | Consolidation experiment |
| SchedulerDynamicCarbon.java | Dynamic carbon scheduling implementation |
| CarbonIntensityModel.java | Regional carbon-intensity values |
| TimeVaryingCarbonIntensityModel.java | Time-varying carbon model |
| Server.java | Server resource and energy simulation |
| Task.java | Workload task representation |
| SleepPowerConfig.java | Sleep-power configuration |
| FirstFitScheduler.java | First Fit comparison |
| BestFitScheduler.java | Best Fit comparison |
| MBFDScheduler.java | MBFD comparison |
| PrometheusClient.java | Prometheus client implementation |

The exact relationship and execution entry points of these files must be verified from source code.

## 3. Backend Architecture

The backend is implemented using Java 21 and Spring Boot.

Main packages:

- controller: REST API endpoints
- entity: database entities
- repository: database access
- scheduler: backend scheduling logic
- service: application services

The backend uses MySQL for persistence and Prometheus for infrastructure monitoring.

### Backend Scheduler

GreenPulseSchedulerService.java implements a backend scheduling workflow.

It considers CPU and memory feasibility, estimated incremental energy, carbon intensity and server activation state.

This is a separate implementation from the trace-driven simulation scheduler.

## 4. Monitoring Architecture

The monitoring environment uses:

VMware
  |
Ubuntu Server 22.04 LTS
  |
Node Exporter
  |
Prometheus
  |
Spring Boot Backend

### Ports

| Service | Port |
|---|---:|
| Node Exporter | 9100 |
| Prometheus | 9090 |
| Spring Boot | 8080 |

Node Exporter collects system metrics. Prometheus scrapes and stores those metrics. The backend queries Prometheus and exposes monitoring functionality through APIs.

## 5. Dataset

The simulation uses workload data from Alibaba Cluster Trace 2018.

The processed dataset is stored in:

simulation/datasets/greenpulse_tasks.csv

Preprocessing is performed using:

simulation/preprocess.py

The original batch_task.csv is not included in this repository because of its large size. Its source and acquisition instructions should be documented separately.

## 6. Experiment Organization

Experimental outputs are stored in:

- experiments/outputs/
- experiments/summaries/
- experiments/sensitivity/

The current results compare CPU and memory baseline scheduling against energy-aware, carbon-aware and combined scheduling configurations.

## 7. Current Limitations

- The simulation and backend scheduler are separate implementations.
- Regional carbon-intensity values in the main simulation are fixed inputs.
- The simulation uses an assumed server sleep-power ratio.
- Prometheus monitoring currently has different utilization data sources for Server-1 versus Server-2 and Server-3.
- The backend changes application power state in its database; this does not physically wake a server.
- No implemented frontend, AI module or standalone database scripts have been identified in the inspected original repository.

## 8. Development Guidelines

Any future integration between the simulation and backend must preserve their existing functionality and clearly define their interfaces.

Do not claim that simulation results are live infrastructure measurements.

Do not claim that a planned component is implemented unless its source code and functionality have been verified.
