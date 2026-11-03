
package com.greenpulse.backend.scheduler;

import com.greenpulse.backend.entity.Server;
import com.greenpulse.backend.entity.Task;
import com.greenpulse.backend.repository.ServerRepository;
import com.greenpulse.backend.repository.TaskRepository;
import com.greenpulse.backend.service.PrometheusService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GreenPulseSchedulerService implements SchedulerService {

    private static final double CPU_CAPACITY = 6.0;
    private static final double MEMORY_CAPACITY = 100.0;

    // Model 5 weights: total = 100%
    private static final double CPU_WEIGHT = 0.20;
    private static final double MEMORY_WEIGHT = 0.20;
    private static final double ENERGY_WEIGHT = 0.25;
    private static final double CARBON_WEIGHT = 0.25;
    private static final double CONSOLIDATION_WEIGHT = 0.10;

    // Sleeping server consumes 10% of idle power.
    private static final double SLEEP_POWER_RATIO = 0.10;

    private final ServerRepository serverRepository;
    private final TaskRepository taskRepository;
    private final PrometheusService prometheusService;

    public GreenPulseSchedulerService(
            ServerRepository serverRepository,
            TaskRepository taskRepository,
            PrometheusService prometheusService) {

        this.serverRepository = serverRepository;
        this.taskRepository = taskRepository;
        this.prometheusService = prometheusService;
    }

    @Override
    @Transactional
    public SchedulerResult schedule(SchedulerRequest request) {

        if (request == null) {
            return failure(null, "Scheduling request is missing.");
        }

        Long taskId = request.getTaskId();

        if (taskId == null) {
            return failure(null, "A saved task ID is required.");
        }

        Double cpuValue = request.getRequiredCpu() != null
                ? request.getRequiredCpu()
                : request.getPredictedCpu();

        Double memoryValue = request.getRequiredMemory();
        Double durationValue = request.getPredictedDuration();

        if (cpuValue == null
                || memoryValue == null
                || durationValue == null) {

            return failure(
                    taskId,
                    "CPU, memory and predicted duration are required."
            );
        }

        double requiredCpu = cpuValue;
        double requiredMemory = memoryValue;
        double durationSeconds = durationValue;

        if (!Double.isFinite(requiredCpu)
                || !Double.isFinite(requiredMemory)
                || !Double.isFinite(durationSeconds)
                || requiredCpu <= 0.0
                || requiredMemory < 0.0
                || durationSeconds <= 0.0) {

            return failure(
                    taskId,
                    "Task resource requirements or duration are invalid."
            );
        }

        if (requiredCpu > CPU_CAPACITY
                || requiredMemory > MEMORY_CAPACITY) {

            return failure(
                    taskId,
                    "Task exceeds the configured server capacity."
            );
        }

        Task task = taskRepository.findById(taskId).orElse(null);

        if (task == null) {
            return failure(
                    taskId,
                    "Task was not found. Create the task before scheduling."
            );
        }

        // Prevent an already assigned task from being scheduled again.
        String taskStatus = task.getStatus();

        if (task.getAssignedServer() != null
                || (taskStatus != null
                && ("SCHEDULED".equalsIgnoreCase(taskStatus)
                || "RUNNING".equalsIgnoreCase(taskStatus)
                || "COMPLETED".equalsIgnoreCase(taskStatus)))) {

            return failure(
                    taskId,
                    "Task is already assigned or has already been processed."
            );
        }

        // Read live monitoring metrics.
        double liveCpuUsage;
        double liveMemoryUsage;

        try {
            liveCpuUsage = prometheusService.getCpuUsage();
            liveMemoryUsage = prometheusService.getMemoryUsage();

            if (!validPercentage(liveCpuUsage)
                    || !validPercentage(liveMemoryUsage)) {

                return failure(
                        taskId,
                        "Invalid live monitoring metrics."
                );
            }

        } catch (Exception e) {
            return failure(
                    taskId,
                    "Live Prometheus metrics unavailable. Scheduling cancelled."
            );
        }

        List<Server> servers = serverRepository.findAll();
        List<Task> existingTasks = taskRepository.findAll();

        List<Candidate> candidates = new ArrayList<>();

        /*
         * Resource reservations:
         * [0] = reserved CPU cores
         * [1] = reserved memory units
         *
         * Currently, SCHEDULED tasks retain their reservations until
         * a future lifecycle implementation changes their status.
         */
        Map<Long, double[]> reservedByServer = new HashMap<>();

        for (Task existingTask : existingTasks) {

            if (existingTask == null
                    || existingTask.getId() == null
                    || existingTask.getAssignedServer() == null
                    || existingTask.getAssignedServer().getId() == null
                    || !"SCHEDULED".equalsIgnoreCase(
                            existingTask.getStatus())) {
                continue;
            }

            Long assignedServerId =
                    existingTask.getAssignedServer().getId();

            double[] reserved = reservedByServer.computeIfAbsent(
                    assignedServerId,
                    id -> new double[2]
            );

            reserved[0] += existingTask.getCpuRequirement();
            reserved[1] += existingTask.getMemoryRequirement();
        }

        // Evaluate active and sleeping servers.
        for (Server server : servers) {

            if (server == null
                    || server.getId() == null
                    || server.getHostname() == null
                    || !"UP".equalsIgnoreCase(server.getStatus())) {
                continue;
            }

            boolean active =
                    "ACTIVE".equalsIgnoreCase(server.getPowerState());

            boolean sleeping =
                    "SLEEPING".equalsIgnoreCase(server.getPowerState());

            if (!active && !sleeping) {
                continue;
            }

            double currentCpuPercent;
            double currentMemoryPercent;

            if (sleeping) {
                currentCpuPercent = 0.0;
                currentMemoryPercent = 0.0;

            } else if ("Server-1".equals(server.getHostname())) {
                currentCpuPercent = liveCpuUsage;
                currentMemoryPercent = liveMemoryUsage;

            } else {
                currentCpuPercent = server.getCpuUsage() != null
                        ? server.getCpuUsage()
                        : 0.0;

                currentMemoryPercent = server.getMemoryUsage() != null
                        ? server.getMemoryUsage()
                        : 0.0;
            }

            if (!validPercentage(currentCpuPercent)
                    || !validPercentage(currentMemoryPercent)) {
                continue;
            }

            /*
             * Add resources reserved by existing scheduled tasks.
             * These reservations are additional to measured utilization.
             */
            double[] reserved = reservedByServer.getOrDefault(
                    server.getId(),
                    new double[2]
            );

            double reservedCpuPercent =
                    (reserved[0] / CPU_CAPACITY) * 100.0;

            double reservedMemoryPercent =
                    (reserved[1] / MEMORY_CAPACITY) * 100.0;

            currentCpuPercent += reservedCpuPercent;
            currentMemoryPercent += reservedMemoryPercent;

            if (currentCpuPercent > 100.0
                    || currentMemoryPercent > 100.0) {
                continue;
            }

            // Project resource utilization after placing this task.
            double cpuAfter =
                    currentCpuPercent / 100.0
                    + requiredCpu / CPU_CAPACITY;

            double memoryAfter =
                    currentMemoryPercent / 100.0
                    + requiredMemory / MEMORY_CAPACITY;

            if (cpuAfter > 1.0 || memoryAfter > 1.0) {
                continue;
            }

            double idlePower;
            double peakPower;
            double carbonIntensity;

            switch (server.getHostname()) {

                case "Server-1":
                    idlePower = 150.0;
                    peakPower = 250.0;
                    carbonIntensity = 0.7335;
                    break;

                case "Server-2":
                    idlePower = 120.0;
                    peakPower = 220.0;
                    carbonIntensity = 0.8086;
                    break;

                case "Server-3":
                    idlePower = 180.0;
                    peakPower = 300.0;
                    carbonIntensity = 0.8900;
                    break;

                default:
                    continue;
            }

            // Current power: sleep power or active linear power.
            double currentCpuFraction =
                    currentCpuPercent / 100.0;

            double currentPower;

            if (sleeping) {
                currentPower = idlePower * SLEEP_POWER_RATIO;
            } else {
                currentPower = idlePower
                        + (peakPower - idlePower)
                        * currentCpuFraction;
            }

            // Predicted power after activation and task placement.
            double predictedPower = idlePower
                    + (peakPower - idlePower) * cpuAfter;

            double incrementalPower = Math.max(
                    0.0,
                    predictedPower - currentPower
            );

            // Convert watts and seconds to kWh.
            double incrementalEnergy = incrementalPower
                    * (durationSeconds / 3600.0)
                    / 1000.0;

            double incrementalCarbon =
                    incrementalEnergy * carbonIntensity;

            // Standalone Model 5 consolidation penalty.
            double consolidationScore = active ? 0.0 : 1.0;

            candidates.add(new Candidate(
                    server,
                    active,
                    sleeping,
                    cpuAfter,
                    memoryAfter,
                    currentPower,
                    predictedPower,
                    incrementalEnergy,
                    incrementalCarbon,
                    consolidationScore
            ));
        }

        if (candidates.isEmpty()) {
            return failure(
                    taskId,
                    "No eligible ACTIVE or SLEEPING server has sufficient capacity."
            );
        }

        // Determine energy and carbon normalization ranges.
        double minEnergy = candidates.stream()
                .mapToDouble(c -> c.energy)
                .min()
                .orElse(0.0);

        double maxEnergy = candidates.stream()
                .mapToDouble(c -> c.energy)
                .max()
                .orElse(0.0);

        double minCarbon = candidates.stream()
                .mapToDouble(c -> c.carbon)
                .min()
                .orElse(0.0);

        double maxCarbon = candidates.stream()
                .mapToDouble(c -> c.carbon)
                .max()
                .orElse(0.0);

        // Calculate Model 5 scores.
        for (Candidate candidate : candidates) {

            candidate.energyScore = normalize(
                    candidate.energy,
                    minEnergy,
                    maxEnergy
            );

            candidate.carbonScore = normalize(
                    candidate.carbon,
                    minCarbon,
                    maxCarbon
            );

            /*
             * CPU and memory use utilization fractions directly.
             * Energy and carbon are normalized across candidates.
             */
            candidate.score =
                    candidate.cpuAfter * CPU_WEIGHT
                    + candidate.memoryAfter * MEMORY_WEIGHT
                    + candidate.energyScore * ENERGY_WEIGHT
                    + candidate.carbonScore * CARBON_WEIGHT
                    + candidate.consolidation * CONSOLIDATION_WEIGHT;

            System.out.printf(
                    Locale.ROOT,
                    "MODEL 5 | Server=%s | State=%s"
                            + " | CPU=%.4f | Memory=%.4f"
                            + " | CurrentPower=%.2fW"
                            + " | PredictedPower=%.2fW"
                            + " | Energy=%.8f kWh"
                            + " | Carbon=%.8f kg CO2"
                            + " | Consolidation=%.2f"
                            + " | Score=%.6f%n",
                    candidate.server.getHostname(),
                    candidate.sleeping ? "SLEEPING" : "ACTIVE",
                    candidate.cpuAfter,
                    candidate.memoryAfter,
                    candidate.currentPower,
                    candidate.predictedPower,
                    candidate.energy,
                    candidate.carbon,
                    candidate.consolidation,
                    candidate.score
            );
        }

        Candidate best = candidates.stream()
                .min(Comparator.comparingDouble(c -> c.score))
                .orElseThrow();

        /*
         * Simulate activation in application state.
         * This does not physically wake a real machine.
         */
        if (best.sleeping) {
            best.server.setPowerState("ACTIVE");
            serverRepository.save(best.server);
        }

        // Persist the task assignment.
        task.setAssignedServer(best.server);
        task.setStatus("SCHEDULED");
        taskRepository.save(task);

        String reason = "Model 5 selected "
                + best.server.getHostname()
                + "; score=" + best.score
                + ", estimatedEnergy=" + best.energy + " kWh"
                + ", estimatedCarbon=" + best.carbon + " kg CO2"
                + ". Resource reservations include existing SCHEDULED tasks. "
                + "Energy and carbon are model estimates. "
                + "Sleeping-server activation is simulated in application state.";

        return new SchedulerResult(
                taskId,
                best.server.getId(),
                best.score,
                best.energy,
                best.carbon,
                reason
        );
    }

    private boolean validPercentage(double value) {
        return Double.isFinite(value)
                && value >= 0.0
                && value <= 100.0;
    }

    private double normalize(
            double value,
            double min,
            double max) {

        if (Math.abs(max - min) < 1e-12) {
            return 0.0;
        }

        return (value - min) / (max - min);
    }

    private SchedulerResult failure(Long taskId, String reason) {
        return new SchedulerResult(
                taskId,
                null,
                null,
                null,
                null,
                reason
        );
    }

    private static class Candidate {

        private final Server server;
        private final boolean active;
        private final boolean sleeping;

        private final double cpuAfter;
        private final double memoryAfter;

        private final double currentPower;
        private final double predictedPower;

        private final double energy;
        private final double carbon;
        private final double consolidation;

        private double energyScore;
        private double carbonScore;
        private double score;

        private Candidate(
                Server server,
                boolean active,
                boolean sleeping,
                double cpuAfter,
                double memoryAfter,
                double currentPower,
                double predictedPower,
                double energy,
                double carbon,
                double consolidation) {

            this.server = server;
            this.active = active;
            this.sleeping = sleeping;
            this.cpuAfter = cpuAfter;
            this.memoryAfter = memoryAfter;
            this.currentPower = currentPower;
            this.predictedPower = predictedPower;
            this.energy = energy;
            this.carbon = carbon;
            this.consolidation = consolidation;
        }
    }
}
