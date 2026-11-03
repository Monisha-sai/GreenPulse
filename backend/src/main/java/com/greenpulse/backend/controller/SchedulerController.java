package com.greenpulse.backend.controller;

import com.greenpulse.backend.scheduler.SchedulerRequest;
import com.greenpulse.backend.scheduler.SchedulerResult;
import com.greenpulse.backend.scheduler.SchedulerService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scheduler")
public class SchedulerController {

    private final SchedulerService schedulerService;

    public SchedulerController(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @PostMapping("/schedule")
    public ResponseEntity<SchedulerResult> scheduleTask(
            @RequestBody SchedulerRequest request) {

        SchedulerResult result = schedulerService.schedule(request);

        if (result.getSelectedServerId() == null) {
            return ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(result);
        }

        return ResponseEntity.ok(result);
    }
}