package com.greenpulse.backend.service;

import com.greenpulse.backend.entity.Prediction;
import com.greenpulse.backend.repository.PredictionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PredictionService {

    private final PredictionRepository predictionRepository;

    public PredictionService(PredictionRepository predictionRepository) {
        this.predictionRepository = predictionRepository;
    }

    public List<Prediction> getAllPredictions() {
        return predictionRepository.findAll();
    }

    public Prediction getPredictionByTaskId(Long taskId) {
        return predictionRepository
                .findByTaskId(taskId)
                .orElse(null);
    }

    public Prediction savePrediction(Prediction prediction) {
        return predictionRepository.save(prediction);
    }
}