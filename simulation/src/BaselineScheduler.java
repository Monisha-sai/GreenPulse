import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BaselineScheduler {

    // =========================================================
    // CONFIGURATION
    // =========================================================

    static final String DATASET_FILE =
            "greenpulse_tasks.csv";


    static final List<Server> servers =
            new ArrayList<>();


    // =========================================================
    // EVENT TYPES
    // =========================================================

    static final int END_EVENT = 0;

    static final int START_EVENT = 1;


    // =========================================================
    // SIMULATION EVENT
    // =========================================================

    static class SimulationEvent {

        double time;

        int type;

        Task task;


        SimulationEvent(
                double time,
                int type,
                Task task) {

            this.time = time;

            this.type = type;

            this.task = task;
        }
    }


    // =========================================================
    // MAIN
    // =========================================================

    public static void main(String[] args) {

        System.out.println(
                "\n=========================================="
        );

        System.out.println(
                "          GREENPULSE BASELINE"
        );

        System.out.println(
                "=========================================="
        );


        // =====================================================
        // CREATE SAME SERVERS
        // =====================================================

        servers.add(
                new Server(
                        "Server-1",
                        6.0,
                        100.0,
                        150.0,
                        250.0
                )
        );


        servers.add(
                new Server(
                        "Server-2",
                        6.0,
                        100.0,
                        120.0,
                        220.0
                )
        );


        servers.add(
                new Server(
                        "Server-3",
                        6.0,
                        100.0,
                        180.0,
                        300.0
                )
        );


        // =====================================================
        // LOAD TASKS
        // =====================================================

        List<Task> tasks =
                loadTasks(DATASET_FILE);


        int totalTasks =
                tasks.size();


        System.out.println(
                "\nTotal Tasks Loaded : "
                + totalTasks
        );


        if (totalTasks == 0) {

            System.out.println(
                    "No tasks found."
            );

            return;
        }


        // =====================================================
        // CREATE START + END EVENTS
        // =====================================================

        List<SimulationEvent> events =
                new ArrayList<>();


        for (Task task : tasks) {

            events.add(
                    new SimulationEvent(
                            task.startTime,
                            START_EVENT,
                            task
                    )
            );


            events.add(
                    new SimulationEvent(
                            task.endTime,
                            END_EVENT,
                            task
                    )
            );
        }


        // =====================================================
        // SORT EVENTS
        // =====================================================

        /*
         * Earlier events first.
         *
         * At the same timestamp:
         *
         * END before START.
         */

        events.sort(
                Comparator
                        .comparingDouble(
                                (SimulationEvent event)
                                        -> event.time
                        )
                        .thenComparingInt(
                                event -> event.type
                        )
        );


        // =====================================================
        // INITIALIZE ENERGY CLOCK
        // =====================================================

        double firstTime =
                events.get(0).time;


        for (Server server : servers) {

            server.initializeEnergyClock(
                    firstTime
            );
        }


        // =====================================================
        // STATISTICS
        // =====================================================

        int scheduledTasks = 0;

        int unscheduledTasks = 0;

        int cpuFailures = 0;

        int memoryFailures = 0;

        int cpuMemoryFailures = 0;

        int otherFailures = 0;


        double totalScore = 0.0;


        // =====================================================
        // EVENT-DRIVEN SIMULATION
        // =====================================================

        for (SimulationEvent event : events) {

            double currentTime =
                    event.time;


            // -------------------------------------------------
            // ACCOUNT ENERGY SINCE PREVIOUS EVENT
            // -------------------------------------------------

            for (Server server : servers) {

                server.accountEnergyUntil(
                        currentTime
                );
            }


            // -------------------------------------------------
            // END EVENT
            // -------------------------------------------------

            if (
                    event.type
                    ==
                    END_EVENT
            ) {

                for (Server server : servers) {

                    server.removeFinishedTasks(
                            currentTime
                    );
                }


                continue;
            }


            // -------------------------------------------------
            // START EVENT
            // -------------------------------------------------

            Task task =
                    event.task;


            // Remove tasks ending at this same time
            // before scheduling the new task.

            for (Server server : servers) {

                server.removeFinishedTasks(
                        currentTime
                );
            }


            // -------------------------------------------------
            // BASELINE SERVER SELECTION
            // -------------------------------------------------

            Server selectedServer =
                    selectBaselineServer(task);


            // -------------------------------------------------
            // NO SERVER AVAILABLE
            // -------------------------------------------------

            if (selectedServer == null) {

                unscheduledTasks++;


                boolean cpuPossible = false;

                boolean memoryPossible = false;


                for (Server server : servers) {

                    if (
                            server.cpuUsage
                            + task.cpuRequired
                            <= server.cpuCapacity
                    ) {

                        cpuPossible = true;
                    }


                    if (
                            server.memoryUsage
                            + task.memoryRequired
                            <= server.memoryCapacity
                    ) {

                        memoryPossible = true;
                    }
                }


                if (
                        !cpuPossible
                        &&
                        !memoryPossible
                ) {

                    cpuMemoryFailures++;

                }
                else if (!cpuPossible) {

                    cpuFailures++;

                }
                else if (!memoryPossible) {

                    memoryFailures++;

                }
                else {

                    otherFailures++;
                }


                continue;
            }


            // -------------------------------------------------
            // BASELINE SCORE
            // -------------------------------------------------

            double cpuUtilization =
                    (
                            selectedServer.cpuUsage
                            + task.cpuRequired
                    )
                    /
                    selectedServer.cpuCapacity;


            double memoryUtilization =
                    (
                            selectedServer.memoryUsage
                            + task.memoryRequired
                    )
                    /
                    selectedServer.memoryCapacity;


            double score =
                    (cpuUtilization * 0.50)
                    +
                    (memoryUtilization * 0.50);


            totalScore += score;


            // -------------------------------------------------
            // ASSIGN TASK
            // -------------------------------------------------

            selectedServer.addTask(task);

            scheduledTasks++;
        }


        // =====================================================
        // FINAL ENERGY ACCOUNTING
        // =====================================================

        double finalTime =
                events.get(
                        events.size() - 1
                ).time;


        for (Server server : servers) {

            server.accountEnergyUntil(
                    finalTime
            );


            server.removeFinishedTasks(
                    finalTime
            );
        }


        // =====================================================
        // CALCULATE RESULTS
        // =====================================================

        int unscheduled =
                totalTasks
                - scheduledTasks;


        double successRate =
                (
                        scheduledTasks
                        * 100.0
                )
                /
                totalTasks;


        double totalEnergy = 0.0;

        double totalCarbon = 0.0;


        for (Server server : servers) {

            totalEnergy +=
                    server.totalEnergy;


            /*
             * Carbon is calculated AFTER placement.
             *
             * It is NOT used by the baseline
             * scheduler.
             */

            double carbonIntensity =
                    CarbonIntensityModel
                            .getCarbonIntensity(
                                    server.name
                            );


            double carbon =
                    server.totalEnergy
                    *
                    carbonIntensity;


            totalCarbon += carbon;
        }


        double averageScore =
                scheduledTasks > 0
                        ?
                        totalScore
                        /
                        scheduledTasks
                        :
                        0.0;


        // =====================================================
        // RESULTS
        // =====================================================

        System.out.println(
                "\n=========================================="
        );

        System.out.println(
                "          BASELINE RESULTS"
        );

        System.out.println(
                "=========================================="
        );


        System.out.printf(
                "\nTotal Tasks      : %d%n",
                totalTasks
        );


        System.out.printf(
                "Scheduled Tasks  : %d%n",
                scheduledTasks
        );


        System.out.printf(
                "Unscheduled Tasks: %d%n",
                unscheduled
        );


        System.out.printf(
                "Success Rate     : %.2f%%%n",
                successRate
        );


        System.out.println();


        System.out.printf(
                "CPU Failures     : %d%n",
                cpuFailures
        );


        System.out.printf(
                "Memory Failures  : %d%n",
                memoryFailures
        );


        System.out.printf(
                "CPU + Memory Fail: %d%n",
                cpuMemoryFailures
        );


        System.out.printf(
                "Other Failures   : %d%n",
                otherFailures
        );


        System.out.printf(
                "\nTotal Energy     : %.6f kWh%n",
                totalEnergy
        );


        System.out.printf(
                "Total Carbon     : %.6f kg CO2%n",
                totalCarbon
        );


        System.out.printf(
                "Average Score    : %.6f%n",
                averageScore
        );


        // =====================================================
        // SERVER DISTRIBUTION
        // =====================================================

        System.out.println(
                "\n------------------------------------------"
        );

        System.out.println(
                "SERVER ENERGY DISTRIBUTION"
        );

        System.out.println(
                "------------------------------------------"
        );


        for (Server server : servers) {

            double carbonIntensity =
                    CarbonIntensityModel
                            .getCarbonIntensity(
                                    server.name
                            );


            String region =
                    CarbonIntensityModel
                            .getRegion(
                                    server.name
                            );


            double carbon =
                    server.totalEnergy
                    *
                    carbonIntensity;


            System.out.printf(
                    "%s : %d tasks%n",
                    server.name,
                    server.totalTasksAssigned
            );


            System.out.printf(
                    "   Region       : %s%n",
                    region
            );


            System.out.printf(
                    "   Carbon Int.  : %.4f kg CO2/kWh%n",
                    carbonIntensity
            );


            System.out.printf(
                    "   Energy       : %.6f kWh%n",
                    server.totalEnergy
            );


            System.out.printf(
                    "   Carbon       : %.6f kg CO2%n",
                    carbon
            );


            System.out.printf(
                    "   Idle Power   : %.2f W%n",
                    server.idlePower
            );


            System.out.printf(
                    "   Peak Power   : %.2f W%n",
                    server.peakPower
            );
        }


        // =====================================================
        // UNSCHEDULED ANALYSIS
        // =====================================================

        System.out.println(
                "\n=========================================="
        );

        System.out.println(
                "      UNSCHEDULED TASK ANALYSIS"
        );

        System.out.println(
                "=========================================="
        );


        System.out.printf(
                "CPU Capacity Failures: %d%n",
                cpuFailures
        );


        System.out.printf(
                "Memory Capacity Failures: %d%n",
                memoryFailures
        );


        System.out.printf(
                "CPU + Memory Failures: %d%n",
                cpuMemoryFailures
        );


        System.out.printf(
                "Other Failures: %d%n",
                otherFailures
        );


        // =====================================================
        // FINAL UTILIZATION
        // =====================================================

        System.out.println(
                "\n=========================================="
        );

        System.out.println(
                "        FINAL SERVER UTILIZATION"
        );

        System.out.println(
                "=========================================="
        );


        for (Server server : servers) {

            double cpuPercent =
                    server.getCpuUtilization()
                    * 100.0;


            double memoryPercent =
                    server.getMemoryUtilization()
                    * 100.0;


            System.out.printf(
                    "%s CPU: %.2f%% | Memory: %.2f%% | Running Tasks: %d%n",
                    server.name,
                    cpuPercent,
                    memoryPercent,
                    server.runningTasks.size()
            );
        }


        // =====================================================
        // ENVIRONMENTAL MODEL
        // =====================================================

        System.out.println(
                "\n=========================================="
        );

        System.out.println(
                "          ENVIRONMENTAL MODEL"
        );

        System.out.println(
                "=========================================="
        );


        System.out.println(
                "Placement Model  : CPU + Memory only"
        );


        System.out.println(
                "Power Model      : Linear idle-to-peak"
        );


        System.out.println(
                "Energy Model     : Event-driven time integration"
        );


        System.out.println(
                "Carbon Model     : Regional grid factors"
        );


        System.out.println(
                "Server Locations : Simulated regional model"
        );


        System.out.println(
                "Carbon Intensity : Measurement only"
        );


        System.out.println(
                "Energy Unit      : kWh"
        );


        System.out.println(
                "Carbon Unit      : kg CO2"
        );


        System.out.println(
                "\nEvent-driven baseline scheduling completed."
        );
    }


    // =========================================================
    // BASELINE SERVER SELECTION
    // =========================================================

    static Server selectBaselineServer(
            Task task) {

        Server bestServer = null;


        double bestScore =
                Double.MAX_VALUE;


        for (Server server : servers) {

            if (!server.canRun(task)) {

                continue;
            }


            // -------------------------------------------------
            // CPU
            // -------------------------------------------------

            double cpuUtilization =
                    (
                            server.cpuUsage
                            + task.cpuRequired
                    )
                    /
                    server.cpuCapacity;


            // -------------------------------------------------
            // MEMORY
            // -------------------------------------------------

            double memoryUtilization =
                    (
                            server.memoryUsage
                            + task.memoryRequired
                    )
                    /
                    server.memoryCapacity;


            // -------------------------------------------------
            // BASELINE SCORE
            // -------------------------------------------------

            /*
             * CPU    = 50%
             * Memory = 50%
             *
             * Energy and carbon are NOT considered.
             */

            double score =
                    (cpuUtilization * 0.50)
                    +
                    (memoryUtilization * 0.50);


            if (score < bestScore) {

                bestScore =
                        score;

                bestServer =
                        server;
            }
        }


        return bestServer;
    }


    // =========================================================
    // LOAD TASKS
    // =========================================================

    static List<Task> loadTasks(
            String filename) {

        List<Task> tasks =
                new ArrayList<>();


        try (
                BufferedReader reader =
                        new BufferedReader(
                                new FileReader(filename)
                        )
        ) {

            // Skip CSV header
            reader.readLine();


            String line;


            while (
                    (line = reader.readLine())
                            != null
            ) {

                String[] values =
                        line.split(",");


                if (values.length < 7) {

                    continue;
                }


                try {

                    String jobName =
                            values[0];


                    String taskName =
                            values[1];


                    double startTime =
                            Double.parseDouble(
                                    values[2]
                            );


                    double endTime =
                            Double.parseDouble(
                                    values[3]
                            );


                    double duration =
                            Double.parseDouble(
                                    values[4]
                            );


                    double cpuRequired =
                            Double.parseDouble(
                                    values[5]
                            );


                    double memoryRequired =
                            Double.parseDouble(
                                    values[6]
                            );


                    Task task =
                            new Task(
                                    jobName,
                                    taskName,
                                    startTime,
                                    endTime,
                                    duration,
                                    cpuRequired,
                                    memoryRequired
                            );


                    tasks.add(task);

                }
                catch (
                        NumberFormatException e
                ) {

                    // Ignore malformed rows
                }
            }

        }
        catch (Exception e) {

            System.out.println(
                    "Error loading dataset: "
                    + e.getMessage()
            );
        }


        return tasks;
    }
}