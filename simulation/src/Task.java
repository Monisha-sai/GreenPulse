public class Task {

    String jobName;
    String taskName;

    double startTime;
    double endTime;
    double duration;

    double cpuRequired;
    double memoryRequired;

    public Task(
            String jobName,
            String taskName,
            double startTime,
            double endTime,
            double duration,
            double cpuRequired,
            double memoryRequired) {

        this.jobName = jobName;
        this.taskName = taskName;

        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = duration;

        this.cpuRequired = cpuRequired;
        this.memoryRequired = memoryRequired;
    }
}