package com.greenpulse.backend.repository;

import com.greenpulse.backend.entity.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    Optional<Prediction> findByTaskId(Long taskId);
}
