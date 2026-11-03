package com.greenpulse.backend.controller;

import com.greenpulse.backend.entity.Prediction;
import com.greenpulse.backend.service.PredictionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping
    public List<Prediction> getAllPredictions() {
        return predictionService.getAllPredictions();
    }

    @GetMapping("/task/{taskId}")
    public Prediction getPredictionByTaskId(@PathVariable Long taskId) {
        return predictionService.getPredictionByTaskId(taskId);
    }

    @PostMapping
    public Prediction createPrediction(@RequestBody Prediction prediction) {
        return predictionService.savePrediction(prediction);
    }
}