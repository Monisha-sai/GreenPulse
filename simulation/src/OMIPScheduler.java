import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Estimator-matched OMIP baseline for GreenPulse.
 * Uses chronological online arrivals and selects the feasible server with
 * minimum M10-compatible corrected marginal energy. Carbon is measurement only.
 *
 * Run from simulation/datasets:
 *   java OMIPScheduler 0.10 123
 *   java OMIPScheduler 0.20 321
 * Server order must be one of: 123, 132, 213, 231, 312, 321.
 */
public class OMIPScheduler {
    private static final String DATASET_FILE = "greenpulse_tasks.csv";
    private static final int END_EVENT = 0;
    private static final int START_EVENT = 1;
    private static final List<Server> servers = new ArrayList<>();

    private static class SimulationEvent {
        final double time;
        final int type;
        final Task task;

        SimulationEvent(double time, int type, Task task) {
            this.time = time;
            this.type = type;
            this.task = task;
        }
    }

    public static void main(String[] args) {
        double sleepRatio = 0.10;
        String serverOrder = "123";

        if (args.length > 2) {
            System.err.println("Usage: java OMIPScheduler [sleep-ratio] [server-order]");
            System.exit(2);
        }
        if (args.length >= 1) {
            try {
                sleepRatio = Double.parseDouble(args[0]);
            } catch (NumberFormatException ex) {
                System.err.println("Invalid sleep ratio. Use a number between 0.0 and 1.0.");
                System.exit(2);
            }
        }
        if (args.length == 2) {
            serverOrder = args[1];
        }
        if (!Double.isFinite(sleepRatio) || sleepRatio < 0.0 || sleepRatio > 1.0) {
            System.err.println("Sleep ratio must be between 0.0 and 1.0.");
            System.exit(2);
        }
        if (!isValidServerOrder(serverOrder)) {
            System.err.println("Server order must be one of: 123, 132, 213, 231, 312, 321.");
            System.exit(2);
        }

        SleepPowerConfig.SLEEP_POWER_RATIO = sleepRatio;
        initializeServers(serverOrder);

        List<Task> tasks = loadTasks(DATASET_FILE);
        if (tasks.isEmpty()) {
            System.err.println("No tasks loaded from " + DATASET_FILE);
            System.exit(1);
        }

        List<SimulationEvent> events = new ArrayList<>();
        for (Task task : tasks) {
            events.add(new SimulationEvent(task.startTime, START_EVENT, task));
            events.add(new SimulationEvent(task.endTime, END_EVENT, task));
        }

        // Same event ordering as Scheduler: END before START at equal timestamps.
        events.sort(
            Comparator.comparingDouble((SimulationEvent event) -> event.time)
                      .thenComparingInt(event -> event.type)
        );

        double firstTime = events.get(0).time;
        for (Server server : servers) {
            server.initializeEnergyClock(firstTime);
        }

        int scheduledTasks = 0;
        int unscheduledTasks = 0;
        int cpuFailures = 0;
        int memoryFailures = 0;
        int cpuMemoryFailures = 0;
        int otherFailures = 0;

        for (SimulationEvent event : events) {
            double currentTime = event.time;

            for (Server server : servers) {
                server.accountEnergyUntil(currentTime);
            }

            if (event.type == END_EVENT) {
                for (Server server : servers) {
                    server.removeFinishedTasks(currentTime);
                }
                continue;
            }

            Task task = event.task;
            for (Server server : servers) {
                server.removeFinishedTasks(currentTime);
            }

            Server selectedServer = selectOmipServer(task);
            if (selectedServer == null) {
                unscheduledTasks++;

                boolean cpuPossible = false;
                boolean memoryPossible = false;
                for (Server server : servers) {
                    if (server.cpuUsage + task.cpuRequired <= server.cpuCapacity) {
                        cpuPossible = true;
                    }
                    if (server.memoryUsage + task.memoryRequired <= server.memoryCapacity) {
                        memoryPossible = true;
                    }
                }

                if (!cpuPossible && !memoryPossible) {
                    cpuMemoryFailures++;
                } else if (!cpuPossible) {
                    cpuFailures++;
                } else if (!memoryPossible) {
                    memoryFailures++;
                } else {
                    otherFailures++;
                }
                continue;
            }

            selectedServer.addTask(task);
            scheduledTasks++;
        }

        double finalTime = events.get(events.size() - 1).time;
        for (Server server : servers) {
            server.accountEnergyUntil(finalTime);
            server.removeFinishedTasks(finalTime);
        }

        printResults(
            tasks.size(), scheduledTasks, unscheduledTasks,
            cpuFailures, memoryFailures, cpuMemoryFailures, otherFailures,
            sleepRatio, serverOrder
        );
    }

    private static boolean isValidServerOrder(String order) {
        return "123".equals(order)
            || "132".equals(order)
            || "213".equals(order)
            || "231".equals(order)
            || "312".equals(order)
            || "321".equals(order);
    }

    private static void initializeServers(String order) {
        servers.clear();

        Server server1 = new Server("Server-1", 6.0, 100.0, 150.0, 250.0);
        Server server2 = new Server("Server-2", 6.0, 100.0, 120.0, 220.0);
        Server server3 = new Server("Server-3", 6.0, 100.0, 180.0, 300.0);

        Server[] serversById = {null, server1, server2, server3};
        for (char digit : order.toCharArray()) {
            servers.add(serversById[digit - '0']);
        }
    }

    /**
     * Minimum corrected marginal-energy selection.
     * Formula matches the Model 10 estimator exactly.
     */
    private static Server selectOmipServer(Task task) {
        Server bestServer = null;
        double minimumMarginalEnergy = Double.POSITIVE_INFINITY;

        for (Server server : servers) {
            if (!server.canRun(task)) {
                continue;
            }

            double busyHorizon = server.busyHorizon(task.startTime);
            double extensionSeconds = Math.max(0.0, task.duration - busyHorizon);
            double dynamicPowerW =
                (server.peakPower - server.idlePower)
                * (task.cpuRequired / server.cpuCapacity);

            double marginalEnergyKWh =
                (
                    dynamicPowerW * task.duration
                    + (server.idlePower - server.sleepPower) * extensionSeconds
                ) / 3600000.0;

            // Strict less-than retains deterministic server-order tie-breaking.
            if (marginalEnergyKWh < minimumMarginalEnergy) {
                minimumMarginalEnergy = marginalEnergyKWh;
                bestServer = server;
            }
        }
        return bestServer;
    }
       private static List<Task> loadTasks(String filename) {
        List<Task> tasks = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            reader.readLine(); // CSV header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length < 7) {
                    continue;
                }
                try {
                    tasks.add(new Task(
                        values[0], values[1],
                        Double.parseDouble(values[2]),
                        Double.parseDouble(values[3]),
                        Double.parseDouble(values[4]),
                        Double.parseDouble(values[5]),
                        Double.parseDouble(values[6])
                    ));
                } catch (NumberFormatException ignored) {
                    // Match the existing baseline's malformed-row handling.
                }
            }
        } catch (Exception ex) {
            System.err.println("Error loading dataset: " + ex.getMessage());
        }
        return tasks;
    }

    private static void printResults(
        int totalTasks, int scheduledTasks, int unscheduledTasks,
        int cpuFailures, int memoryFailures, int cpuMemoryFailures,
        int otherFailures, double sleepRatio, String serverOrder
    ) {
        double successRate = totalTasks == 0 ? 0.0 : scheduledTasks * 100.0 / totalTasks;
        double totalEnergy = 0.0;
        double totalCarbon = 0.0;

        for (Server server : servers) {
            totalEnergy += server.totalEnergy;
            totalCarbon += server.totalEnergy
                * CarbonIntensityModel.getCarbonIntensity(server.name);
        }

        System.out.println("\n==========================================");
        System.out.println("       GREENPULSE ESTIMATOR-MATCHED OMIP");
        System.out.println("==========================================");
        System.out.printf("Sleep Power Ratio : %.0f%%%n", sleepRatio * 100.0);
        System.out.println("Server Order      : " + serverOrder);
        System.out.println("Task Order        : Chronological online arrival");
        System.out.println("Placement Rule    : Minimum corrected marginal energy");
        System.out.println("Carbon in Choice  : No (measurement only)");
        System.out.println("Energy Estimator  : M10 idle-extension corrected estimator");

        System.out.println("\n--------------- RESULTS -----------------");
        System.out.printf("Total Tasks       : %d%n", totalTasks);
        System.out.printf("Scheduled Tasks   : %d%n", scheduledTasks);
        System.out.printf("Unscheduled Tasks : %d%n", unscheduledTasks);
        System.out.printf("Success Rate      : %.2f%%%n", successRate);
        System.out.printf("CPU Failures      : %d%n", cpuFailures);
        System.out.printf("Memory Failures   : %d%n", memoryFailures);
        System.out.printf("CPU + Memory Fail : %d%n", cpuMemoryFailures);
        System.out.printf("Other Failures    : %d%n", otherFailures);
        System.out.printf("Total Energy      : %.6f kWh%n", totalEnergy);
        System.out.printf("Total Carbon      : %.6f kg CO2%n", totalCarbon);

        System.out.println("\n-------- SERVER ENERGY DISTRIBUTION -----");
        for (Server server : servers) {
            double intensity = CarbonIntensityModel.getCarbonIntensity(server.name);
            double serverCarbon = server.totalEnergy * intensity;
            String region = CarbonIntensityModel.getRegion(server.name);

            System.out.printf("%s : %d tasks%n", server.name, server.totalTasksAssigned);
            System.out.printf("   Region       : %s%n", region);
            System.out.printf("   Carbon Int.  : %.4f kg CO2/kWh%n", intensity);
            System.out.printf("   Energy       : %.6f kWh%n", server.totalEnergy);
            System.out.printf("   Carbon       : %.6f kg CO2%n", serverCarbon);
            System.out.printf("   Idle Power   : %.2f W%n", server.idlePower);
            System.out.printf("   Peak Power   : %.2f W%n", server.peakPower);
        }

        System.out.println("\nEstimator-matched OMIP simulation completed.");
    }
}