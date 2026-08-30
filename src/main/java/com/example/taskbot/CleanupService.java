package com.example.taskbot;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class CleanupService {
    private final TaskRepository repository;

    public CleanupService(TaskRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deleteOldTasks() {
        LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
        repository.deleteByStatusAndCompletedAtBefore("COMPLETED", yesterday);
    }
}