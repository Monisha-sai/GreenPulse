
package com.greenpulse.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String taskName;
    private String jobName;
    private Double cpuRequirement;
    private Double memoryRequirement;
    private Double duration;
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_server_id")
    private Server assignedServer;

    public Task() {
    }

    public Long getId() {
        return id;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public Double getCpuRequirement() {
        return cpuRequirement;
    }

    public void setCpuRequirement(Double cpuRequirement) {
        this.cpuRequirement = cpuRequirement;
    }

    public Double getMemoryRequirement() {
        return memoryRequirement;
    }

    public void setMemoryRequirement(Double memoryRequirement) {
        this.memoryRequirement = memoryRequirement;
    }

    public Double getDuration() {
        return duration;
    }

    public void setDuration(Double duration) {
        this.duration = duration;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Server getAssignedServer() {
        return assignedServer;
    }

    public void setAssignedServer(Server assignedServer) {
        this.assignedServer = assignedServer;
    }
}
