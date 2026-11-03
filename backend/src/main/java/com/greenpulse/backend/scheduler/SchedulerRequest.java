package com.greenpulse.backend.scheduler;

import com.greenpulse.backend.entity.Server;

import java.util.List;

public class SchedulerRequest {

    private Long taskId;

    private Double requiredCpu;

    private Double requiredMemory;

    private Integer priority;

    private Double predictedDuration;

    private Double predictedCpu;

    private Double predictedEnergy;

    private Double predictedCarbon;

    private List<Server> servers;

    public SchedulerRequest() {
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Double getRequiredCpu() {
        return requiredCpu;
    }

    public void setRequiredCpu(Double requiredCpu) {
        this.requiredCpu = requiredCpu;
    }

    public Double getRequiredMemory() {
        return requiredMemory;
    }

    public void setRequiredMemory(Double requiredMemory) {
        this.requiredMemory = requiredMemory;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Double getPredictedDuration() {
        return predictedDuration;
    }

    public void setPredictedDuration(Double predictedDuration) {
        this.predictedDuration = predictedDuration;
    }

    public Double getPredictedCpu() {
        return predictedCpu;
    }

    public void setPredictedCpu(Double predictedCpu) {
        this.predictedCpu = predictedCpu;
    }

    public Double getPredictedEnergy() {
        return predictedEnergy;
    }

    public void setPredictedEnergy(Double predictedEnergy) {
        this.predictedEnergy = predictedEnergy;
    }

    public Double getPredictedCarbon() {
        return predictedCarbon;
    }

    public void setPredictedCarbon(Double predictedCarbon) {
        this.predictedCarbon = predictedCarbon;
    }

    public List<Server> getServers() {
        return servers;
    }

    public void setServers(List<Server> servers) {
        this.servers = servers;
    }
}
