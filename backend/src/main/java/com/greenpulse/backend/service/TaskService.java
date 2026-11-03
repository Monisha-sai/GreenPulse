package com.greenpulse.backend.service;

import com.greenpulse.backend.entity.Task;
import com.greenpulse.backend.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }
    public Task saveTask(Task task) {
        return taskRepository.save(task);
    }
}
