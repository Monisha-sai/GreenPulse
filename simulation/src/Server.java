import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Server {

    String name;

    double cpuCapacity;
    double memoryCapacity;

    double cpuUsage;
    double memoryUsage;

    // Power model
    double idlePower;
    double peakPower;

    // Sleep power
    double sleepPower;

    // Total accumulated energy in kWh
    double totalEnergy;

    // Last time energy was calculated
    double lastEnergyUpdateTime;

    // Whether server is currently powered on
    boolean active;

    // Tasks currently running
    List<RunningTask> runningTasks;

    // Historical number of tasks assigned
    int totalTasksAssigned;


    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public Server(
            String name,
            double cpuCapacity,
            double memoryCapacity,
            double idlePower,
            double peakPower) {

        this.name = name;

        this.cpuCapacity = cpuCapacity;
        this.memoryCapacity = memoryCapacity;

        this.cpuUsage = 0.0;
        this.memoryUsage = 0.0;

        this.idlePower = idlePower;
        this.peakPower = peakPower;

        /*
         * Sleep power is assumed to be 10% of idle power.
         *
         * This is a simulation assumption.
         * It should be clearly documented in the paper.
         */
        this.sleepPower =
                idlePower * SleepPowerConfig.SLEEP_POWER_RATIO;

        this.totalEnergy = 0.0;

        this.lastEnergyUpdateTime = 0.0;

        /*
         * Server-1 starts active.
         * Other servers can be activated when required.
         */
        this.active =
                name.equals("Server-1");

        this.runningTasks =
                new ArrayList<>();

        this.totalTasksAssigned = 0;
    }


    // =========================================================
    // INITIALIZE ENERGY CLOCK
    // =========================================================

    public void initializeEnergyClock(
            double startTime) {

        lastEnergyUpdateTime =
                startTime;
    }


    // =========================================================
    // ACTIVATE SERVER
    // =========================================================

    public void activate() {

        active = true;
    }


    // =========================================================
    // PUT SERVER TO SLEEP
    // =========================================================

    public void sleep() {

        /*
         * A server can only sleep when no tasks
         * are currently running.
         */
        if (runningTasks.isEmpty()) {

            active = false;

            cpuUsage = 0.0;

            memoryUsage = 0.0;
        }
    }


    // =========================================================
    // CURRENT POWER
    // =========================================================

    public double getPower() {

        /*
         * Sleeping server consumes only sleep power.
         */
        if (!active) {

            return sleepPower;
        }


        double utilization =
                cpuUsage / cpuCapacity;


        if (utilization < 0.0) {

            utilization = 0.0;
        }


        if (utilization > 1.0) {

            utilization = 1.0;
        }


        return
                idlePower
                        +
                        (
                                peakPower
                                        - idlePower
                        )
                                * utilization;
    }


    // =========================================================
    // ACCOUNT ENERGY UNTIL A GIVEN TIME
    // =========================================================

    public void accountEnergyUntil(
            double currentTime) {

        if (currentTime <= lastEnergyUpdateTime) {

            return;
        }


        double elapsedSeconds =
                currentTime
                        - lastEnergyUpdateTime;


        double power =
                getPower();


        /*
         * Energy:
         *
         * Power(W)
         * × Time(hours)
         * / 1000
         *
         * = kWh
         */

        double energy =
                power
                        *
                        (
                                elapsedSeconds
                                        / 3600.0
                        )
                        / 1000.0;


        totalEnergy +=
                energy;


        lastEnergyUpdateTime =
                currentTime;
    }


    // =========================================================
    // REMOVE FINISHED TASKS
    // =========================================================

    public void removeFinishedTasks(
            double currentTime) {

        Iterator<RunningTask> iterator =
                runningTasks.iterator();


        while (iterator.hasNext()) {

            RunningTask runningTask =
                    iterator.next();


            if (
                    runningTask.endTime
                            <= currentTime
            ) {

                cpuUsage -=
                        runningTask.task.cpuRequired;


                memoryUsage -=
                        runningTask.task.memoryRequired;


                iterator.remove();
            }
        }


        // Protect against floating-point errors

        if (Math.abs(cpuUsage) < 1e-10) {

            cpuUsage = 0.0;
        }


        if (Math.abs(memoryUsage) < 1e-10) {

            memoryUsage = 0.0;
        }


        if (cpuUsage < 0.0) {

            cpuUsage = 0.0;
        }


        if (memoryUsage < 0.0) {

            memoryUsage = 0.0;
        }


        /*
         * If no tasks remain, the server can sleep.
         */
        if (runningTasks.isEmpty()) {

            sleep();
        }
    }


    // =========================================================
    // ADD TASK
    // =========================================================

    public void addTask(Task task) {

        /*
         * Wake the server before assigning work.
         */
        activate();


        cpuUsage +=
                task.cpuRequired;


        memoryUsage +=
                task.memoryRequired;


        runningTasks.add(
                new RunningTask(
                        task,
                        task.endTime
                )
        );


        totalTasksAssigned++;
    }


    // =========================================================
    // CHECK WHETHER TASK CAN RUN
    // =========================================================

    public boolean canRun(Task task) {

        return
                (
                        cpuUsage
                                + task.cpuRequired
                                <= cpuCapacity
                )
                &&
                (
                        memoryUsage
                                + task.memoryRequired
                                <= memoryCapacity
                );
    }


    // =========================================================
    // CPU UTILIZATION
    // =========================================================

    public double getCpuUtilization() {

        if (cpuCapacity <= 0.0) {

            return 0.0;
        }


        return
                cpuUsage
                        /
                        cpuCapacity;
    }


    // =========================================================
    // MEMORY UTILIZATION
    // =========================================================

    public double getMemoryUtilization() {

        if (memoryCapacity <= 0.0) {

            return 0.0;
        }


        return
                memoryUsage
                        /
                        memoryCapacity;
    }
}


// =============================================================
// RUNNING TASK
// =============================================================

class RunningTask {

    Task task;

    double endTime;


    public RunningTask(
            Task task,
            double endTime) {

        this.task = task;

        this.endTime = endTime;
    }
}