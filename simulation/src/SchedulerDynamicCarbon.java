import java.io.BufferedReader;

import java.io.FileReader;

import java.util.ArrayList;

import java.util.Comparator;

import java.util.List;
import java.util.HashMap;
import java.util.Map;



public class SchedulerDynamicCarbon {



    // =========================================================

    // CONFIGURATION

    // =========================================================



    static final String DATASET_FILE =

            "greenpulse_tasks.csv";





    // =========================================================

    // WEIGHTS

    // =========================================================



    /*

     * Resource:

     * CPU      = 20%

     * Memory   = 20%

     *

     * Environment:

     * Energy   = 25%

     * Carbon   = 25%

     *

     * Consolidation:

     * Active-server penalty = 10%

     *

     * Total = 100%

     */



    static double CPU_WEIGHT = 0.20;



    static double MEMORY_WEIGHT = 0.20;



    static double ENERGY_WEIGHT = 0.25;



    static double CARBON_WEIGHT = 0.25;



    static double CONSOLIDATION_WEIGHT = 0.10;

    static int MODEL = 5;

    static String MODEL_NAME = "Model 5 - Full GreenPulse + Consolidation";





    // =========================================================

    // SERVER LIST

    // =========================================================



    static final List<Server> servers =

            new ArrayList<>();

    // Carbon accumulated interval-by-interval using the dynamic intensity model.
    static final Map<String, Double> dynamicCarbonByServer = new HashMap<>();





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

    // SERVER CANDIDATE

    // =========================================================



    static class ServerCandidate {



        Server server;



        double cpuUtilization;



        double memoryUtilization;



        double currentPower;



        double predictedPower;



        double incrementalPower;



        double incrementalEnergy;



        double carbonIntensity;



        double incrementalCarbon;



        double energyScore;



        double carbonScore;



        double consolidationScore;



        double finalScore;





        ServerCandidate(

                Server server) {



            this.server = server;

        }

    }





    // =========================================================

    // MAIN

    // =========================================================



    public static void main(String[] args) {

        if (args.length > 0) {
            try {
                MODEL = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                MODEL = 5;
            }
        }

        configureModel();



        System.out.println(

                "\n=========================================="

        );



        System.out.println(

                "       GREENPULSE CONSOLIDATION MODEL"

        );



        System.out.println(

                "=========================================="

        );





        // =====================================================

        // CREATE SERVERS

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

        // LOAD DATASET

        // =====================================================



        List<Task> tasks =

                loadTasks(DATASET_FILE);





        int totalTasks =

                tasks.size();





        if (totalTasks == 0) {



            System.out.println(

                    "No tasks found."

            );



            return;

        }





        System.out.println(

                "Total Tasks Loaded : "

                        + totalTasks

        );





        // =====================================================

        // CREATE EVENTS

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
            server.initializeEnergyClock(firstTime);
            dynamicCarbonByServer.put(server.name, 0.0);
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
        // SCHEDULING PERFORMANCE METRICS
        // =====================================================

        int schedulingDecisions = 0;
        long totalSchedulingTimeNanos = 0L;
        double activeServerTime = 0.0;
        int peakActiveServers = 0;
        double lastMetricTime = firstTime;






        // =====================================================

        // EVENT-DRIVEN SIMULATION

        // =====================================================



        for (SimulationEvent event : events) {



            double currentTime =

                    event.time;

            // Track active-server time across the simulation timeline.
            int currentActiveServers = 0;

            for (Server server : servers) {
                if (server.active) {
                    currentActiveServers++;
                }
            }

            activeServerTime +=
                    currentActiveServers
                            * (currentTime - lastMetricTime);

            if (currentActiveServers > peakActiveServers) {
                peakActiveServers = currentActiveServers;
            }

            lastMetricTime = currentTime;






            // -------------------------------------------------

            // ACCOUNT ENERGY

            // -------------------------------------------------



            for (Server server : servers) {



                accountEnergyAndCarbon(server, currentTime);

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

            // Every START event is one scheduler decision opportunity.
            schedulingDecisions++;






            /*

             * Remove tasks that finished at this

             * exact timestamp.

             */



            for (Server server : servers) {



                server.removeFinishedTasks(

                        currentTime

                );

            }





            // -------------------------------------------------

            // SELECT SERVER

            // -------------------------------------------------



            long schedulingStartNanos =
                    System.nanoTime();

            ServerCandidate selectedCandidate =
                    selectGreenPulseServer(task);

            totalSchedulingTimeNanos +=
                    System.nanoTime() - schedulingStartNanos;





            // -------------------------------------------------

            // NO SERVER AVAILABLE

            // -------------------------------------------------



            if (selectedCandidate == null) {



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

            // RECORD SCORE

            // -------------------------------------------------



            totalScore +=

                    selectedCandidate.finalScore;





            // -------------------------------------------------

            // ASSIGN TASK

            // -------------------------------------------------



            selectedCandidate.server.addTask(task);



            scheduledTasks++;

        }





        // =====================================================

        // FINAL ENERGY ACCOUNTING

        // =====================================================



        double finalTime =

                events.get(

                        events.size() - 1

                ).time;

        int finalActiveServers = 0;

        for (Server server : servers) {
            if (server.active) {
                finalActiveServers++;
            }
        }

        activeServerTime +=
                finalActiveServers
                        * (finalTime - lastMetricTime);

        if (finalActiveServers > peakActiveServers) {
            peakActiveServers = finalActiveServers;
        }






        for (Server server : servers) {



            accountEnergyAndCarbon(server, finalTime);





            server.removeFinishedTasks(

                    finalTime

            );

        }





        // =====================================================

        // FINAL METRICS

        // =====================================================



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
            totalEnergy += server.totalEnergy;
            totalCarbon += dynamicCarbonByServer.getOrDefault(server.name, 0.0);
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

        double simulationDuration =
                finalTime - firstTime;

        double averageActiveServers =
                simulationDuration > 0.0
                        ? activeServerTime / simulationDuration
                        : 0.0;

        double averageSleepingServers =
                servers.size() - averageActiveServers;

        double totalSchedulingTimeMillis =
                totalSchedulingTimeNanos / 1_000_000.0;

        double averageSchedulingLatencyMillis =
                schedulingDecisions > 0
                        ? totalSchedulingTimeMillis / schedulingDecisions
                        : 0.0;

        int finalActiveServerCount = 0;

        for (Server server : servers) {
            if (server.active) {
                finalActiveServerCount++;
            }
        }

        // RESULTS

        // =====================================================



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

                unscheduledTasks

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

        System.out.printf(
                "Scheduling Decisions : %d%n",
                schedulingDecisions
        );

        System.out.printf(
                "Total Scheduling Time : %.6f ms%n",
                totalSchedulingTimeMillis
        );

        System.out.printf(
                "Average Scheduling Latency : %.6f ms%n",
                averageSchedulingLatencyMillis
        );

        System.out.printf(
                "Peak Active Servers : %d%n",
                peakActiveServers
        );

        System.out.printf(
                "Average Active Servers : %.4f%n",
                averageActiveServers
        );

        System.out.printf(
                "Average Sleeping Servers : %.4f%n",
                averageSleepingServers
        );

        System.out.printf(
                "Final Active Servers : %d%n",
                finalActiveServerCount
        );

        System.out.printf(
                "Final Sleeping Servers : %d%n",
                servers.size() - finalActiveServerCount
        );

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
                    TimeVaryingCarbonIntensityModel.getCarbonIntensity(
                            server.name, finalTime);

            double carbon = dynamicCarbonByServer.getOrDefault(server.name, 0.0);





            String region =
                    TimeVaryingCarbonIntensityModel.getRegion(server.name);






            System.out.printf(

                    "%s : %d tasks%n",

                    server.name,

                    server.totalTasksAssigned

            );





            System.out.printf(

                    "   Active       : %s%n",

                    server.active

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

                    "   Sleep Power  : %.2f W%n",

                    server.sleepPower

            );





            System.out.printf(

                    "   Peak Power   : %.2f W%n",

                    server.peakPower

            );

        }





        // =====================================================

        // UNSCHEDULED TASK ANALYSIS

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

        // FINAL SERVER UTILIZATION

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

        // MODEL DESCRIPTION

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

                "Placement Model  : " + MODEL_NAME

        );

        System.out.printf("CPU Weight       : %.0f%%%n", CPU_WEIGHT * 100);
        System.out.printf("Memory Weight    : %.0f%%%n", MEMORY_WEIGHT * 100);
        System.out.printf("Energy Weight    : %.0f%%%n", ENERGY_WEIGHT * 100);
        System.out.printf("Carbon Weight    : %.0f%%%n", CARBON_WEIGHT * 100);
        System.out.printf("Consolidation    : %.0f%%%n", CONSOLIDATION_WEIGHT * 100);





        System.out.println(

                "Power Model      : Linear idle-to-peak"

        );





        System.out.println(

                "Sleep Model      : 10% of idle power"

        );





        System.out.println(

                "Energy Model     : Event-driven time integration"

        );





        System.out.println(

                "Task Energy      : Incremental energy estimate"

        );





        System.out.println(

                "Carbon Model     : Time-varying synthetic regional profiles"

        );





        System.out.println(

                "Server Locations : Simulated regional model"

        );





        System.out.println(

                "Energy Unit      : kWh"

        );





        System.out.println(

                "Carbon Unit      : kg CO2"

        );





        System.out.println(

                "\nGreenPulse consolidation scheduling completed."

        );

    }





    // =========================================================

    // GREENPULSE SERVER SELECTION

    // =========================================================



    static void configureModel() {

        switch (MODEL) {

            case 1:
                CPU_WEIGHT = 0.50;
                MEMORY_WEIGHT = 0.50;
                ENERGY_WEIGHT = 0.00;
                CARBON_WEIGHT = 0.00;
                CONSOLIDATION_WEIGHT = 0.00;
                MODEL_NAME = "Model 1 - CPU + Memory Baseline";
                break;

            case 2:
                CPU_WEIGHT = 0.25;
                MEMORY_WEIGHT = 0.25;
                ENERGY_WEIGHT = 0.50;
                CARBON_WEIGHT = 0.00;
                CONSOLIDATION_WEIGHT = 0.00;
                MODEL_NAME = "Model 2 - CPU + Memory + Energy";
                break;

            case 3:
                CPU_WEIGHT = 0.25;
                MEMORY_WEIGHT = 0.25;
                ENERGY_WEIGHT = 0.00;
                CARBON_WEIGHT = 0.50;
                CONSOLIDATION_WEIGHT = 0.00;
                MODEL_NAME = "Model 3 - CPU + Memory + Carbon";
                break;

            case 4:
                CPU_WEIGHT = 0.20;
                MEMORY_WEIGHT = 0.20;
                ENERGY_WEIGHT = 0.30;
                CARBON_WEIGHT = 0.30;
                CONSOLIDATION_WEIGHT = 0.00;
                MODEL_NAME = "Model 4 - CPU + Memory + Energy + Carbon";
                break;

            case 5:
                CPU_WEIGHT = 0.20;
                MEMORY_WEIGHT = 0.20;
                ENERGY_WEIGHT = 0.25;
                CARBON_WEIGHT = 0.25;
                CONSOLIDATION_WEIGHT = 0.10;
                MODEL_NAME = "Model 5 - Full GreenPulse + Consolidation";
                break;

            default:
                System.out.println("Invalid model number. Use 1, 2, 3, 4, or 5.");
                System.exit(1);
        }
    }

    static ServerCandidate selectGreenPulseServer(

            Task task) {





        List<ServerCandidate> candidates =

                new ArrayList<>();





        // =====================================================

        // BUILD FEASIBLE CANDIDATES

        // =====================================================



        for (Server server : servers) {



            if (!server.canRun(task)) {



                continue;

            }





            ServerCandidate candidate =

                    new ServerCandidate(server);





            // -------------------------------------------------

            // RESOURCE UTILIZATION

            // -------------------------------------------------



            candidate.cpuUtilization =

                    (

                            server.cpuUsage

                                    + task.cpuRequired

                    )

                            /

                            server.cpuCapacity;





            candidate.memoryUtilization =

                    (

                            server.memoryUsage

                                    + task.memoryRequired

                    )

                            /

                            server.memoryCapacity;





            // -------------------------------------------------

            // CURRENT POWER

            // -------------------------------------------------



            candidate.currentPower =

                    server.getPower();





            // -------------------------------------------------

            // PREDICTED POWER

            // -------------------------------------------------



            double utilization =

                    candidate.cpuUtilization;





            if (utilization < 0.0) {



                utilization = 0.0;

            }





            if (utilization > 1.0) {



                utilization = 1.0;

            }





            /*

             * If the server is sleeping, calculate the

             * power it would use after being activated.

             */



            double activePredictedPower =

                    server.idlePower

                            +

                            (

                                    server.peakPower

                                            - server.idlePower

                            )

                                    * utilization;





            candidate.predictedPower =

                    activePredictedPower;





            // -------------------------------------------------

            // INCREMENTAL POWER

            // -------------------------------------------------



            candidate.incrementalPower =

                    candidate.predictedPower

                            -

                            candidate.currentPower;





            if (candidate.incrementalPower < 0.0) {



                candidate.incrementalPower = 0.0;

            }





            // -------------------------------------------------

            // INCREMENTAL ENERGY

            // -------------------------------------------------



            candidate.incrementalEnergy =

                    candidate.incrementalPower

                            *

                            (

                                    task.duration

                                            / 3600.0

                            )

                            /

                            1000.0;





            // -------------------------------------------------

            // CARBON INTENSITY

            // -------------------------------------------------



            candidate.carbonIntensity =
                    TimeVaryingCarbonIntensityModel.getCarbonIntensity(
                            server.name, task.startTime);





            // -------------------------------------------------

            // INCREMENTAL CARBON

            // -------------------------------------------------



            candidate.incrementalCarbon =

                    candidate.incrementalEnergy

                            *

                            candidate.carbonIntensity;





            // -------------------------------------------------

            // CONSOLIDATION SCORE

            // -------------------------------------------------



            /*

             * Active server:

             *

             *     0 = preferred

             *

             * Sleeping server:

             *

             *     1 = requires activation

             *

             * This encourages workload consolidation.

             */



            candidate.consolidationScore =

                    server.active

                            ?

                            0.0

                            :

                            1.0;





            candidates.add(candidate);

        }





        // =====================================================

        // NO VALID SERVER

        // =====================================================



        if (candidates.isEmpty()) {



            return null;

        }





        // =====================================================

        // FIND ENVIRONMENTAL RANGES

        // =====================================================



        double minEnergy =

                Double.MAX_VALUE;





        double maxEnergy =

                -Double.MAX_VALUE;





        double minCarbon =

                Double.MAX_VALUE;





        double maxCarbon =

                -Double.MAX_VALUE;





        for (

                ServerCandidate candidate :

                candidates

        ) {



            if (

                    candidate.incrementalEnergy

                            < minEnergy

            ) {



                minEnergy =

                        candidate.incrementalEnergy;

            }





            if (

                    candidate.incrementalEnergy

                            > maxEnergy

            ) {



                maxEnergy =

                        candidate.incrementalEnergy;

            }





            if (

                    candidate.incrementalCarbon

                            < minCarbon

            ) {



                minCarbon =

                        candidate.incrementalCarbon;

            }





            if (

                    candidate.incrementalCarbon

                            > maxCarbon

            ) {



                maxCarbon =

                        candidate.incrementalCarbon;

            }

        }





        // =====================================================

        // NORMALIZE ENVIRONMENTAL COST

        // =====================================================



        for (

                ServerCandidate candidate :

                candidates

        ) {



            if (

                    Math.abs(

                            maxEnergy

                                    - minEnergy

                    )

                            < 1e-12

            ) {



                candidate.energyScore = 0.0;



            }

            else {



                candidate.energyScore =

                        (

                                candidate.incrementalEnergy

                                        - minEnergy

                        )

                                /

                                (

                                        maxEnergy

                                                - minEnergy

                                );

            }





            if (

                    Math.abs(

                            maxCarbon

                                    - minCarbon

                    )

                            < 1e-12

            ) {



                candidate.carbonScore = 0.0;



            }

            else {



                candidate.carbonScore =

                        (

                                candidate.incrementalCarbon

                                        - minCarbon

                        )

                                /

                                (

                                        maxCarbon

                                                - minCarbon

                                );

            }





            // -------------------------------------------------

            // FINAL SCORE

            // -------------------------------------------------



            candidate.finalScore =

                    (

                            candidate.cpuUtilization

                                    * CPU_WEIGHT

                    )

                            +

                            (

                                    candidate.memoryUtilization

                                            * MEMORY_WEIGHT

                            )

                            +

                            (

                                    candidate.energyScore

                                            * ENERGY_WEIGHT

                            )

                            +

                            (

                                    candidate.carbonScore

                                            * CARBON_WEIGHT

                            )

                            +

                            (

                                    candidate.consolidationScore

                                            * CONSOLIDATION_WEIGHT

                            );

        }





        // =====================================================

        // SELECT BEST SERVER

        // =====================================================



        ServerCandidate bestCandidate =

                null;





        double bestScore =

                Double.MAX_VALUE;





        for (

                ServerCandidate candidate :

                candidates

        ) {



            if (

                    candidate.finalScore

                            < bestScore

            ) {



                bestScore =

                        candidate.finalScore;





                bestCandidate =

                        candidate;

            }

        }





        return bestCandidate;

    }





    // =========================================================

    // LOAD DATASET

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



    /**
     * Accounts energy and emissions for the interval since the server's previous
     * energy update. Server power is constant within an event interval in this
     * simulator, while carbon intensity is linearly interpolated hourly.
     */
    static void accountEnergyAndCarbon(Server server, double currentTime) {
        double intervalStart = server.lastEnergyUpdateTime;
        if (currentTime <= intervalStart) {
            server.accountEnergyUntil(currentTime);
            return;
        }

        double energyBefore = server.totalEnergy;
        server.accountEnergyUntil(currentTime);
        double intervalEnergy = server.totalEnergy - energyBefore;
        if (intervalEnergy > 0.0) {
            double averageIntensity = averageCarbonIntensity(
                    server.name, intervalStart, currentTime);
            double carbon = intervalEnergy * averageIntensity;
            dynamicCarbonByServer.merge(server.name, carbon, Double::sum);
        }
    }

    /** Integrates the piecewise-linear hourly intensity profile over seconds. */
    static double averageCarbonIntensity(
            String serverName, double startSeconds, double endSeconds) {
        if (endSeconds <= startSeconds) {
            return TimeVaryingCarbonIntensityModel.getCarbonIntensity(
                    serverName, startSeconds);
        }

        double cursor = startSeconds;
        double intensitySeconds = 0.0;
        while (cursor < endSeconds) {
            double nextHour = (Math.floor(cursor / 3600.0) + 1.0) * 3600.0;
            double segmentEnd = Math.min(endSeconds, nextHour);
            if (segmentEnd <= cursor) {
                segmentEnd = endSeconds;
            }
            double i0 = TimeVaryingCarbonIntensityModel.getCarbonIntensity(
                    serverName, cursor);
            double i1 = TimeVaryingCarbonIntensityModel.getCarbonIntensity(
                    serverName, segmentEnd);
            intensitySeconds += (i0 + i1) * 0.5 * (segmentEnd - cursor);
            cursor = segmentEnd;
        }
        return intensitySeconds / (endSeconds - startSeconds);
    }
}