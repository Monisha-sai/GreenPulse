package com.greenpulse.backend.scheduler;

public class SchedulerResult {

    private Long taskId;

    private Long selectedServerId;

    private Double score;

    private Double estimatedEnergy;

    private Double estimatedCarbon;

    private String reason;

    public SchedulerResult() {
    }

    public SchedulerResult(
            Long taskId,
            Long selectedServerId,
            Double score,
            Double estimatedEnergy,
            Double estimatedCarbon,
            String reason) {

        this.taskId = taskId;
        this.selectedServerId = selectedServerId;
        this.score = score;
        this.estimatedEnergy = estimatedEnergy;
        this.estimatedCarbon = estimatedCarbon;
        this.reason = reason;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getSelectedServerId() {
        return selectedServerId;
    }

    public void setSelectedServerId(Long selectedServerId) {
        this.selectedServerId = selectedServerId;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Double getEstimatedEnergy() {
        return estimatedEnergy;
    }

    public void setEstimatedEnergy(Double estimatedEnergy) {
        this.estimatedEnergy = estimatedEnergy;
    }

    public Double getEstimatedCarbon() {
        return estimatedCarbon;
    }

    public void setEstimatedCarbon(Double estimatedCarbon) {
        this.estimatedCarbon = estimatedCarbon;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
