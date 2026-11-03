package com.greenpulse.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "predictions")
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long taskId;

    private Double predictedCpu;

    private Double predictedEnergy;

    private Double predictedCarbon;

    public Prediction() {
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
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
}
