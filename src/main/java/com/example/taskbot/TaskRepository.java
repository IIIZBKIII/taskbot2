package com.example.taskbot;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    // Выдает активные задачи, отсортированные по дедлайну (сначала ближайшие)
    List<Task> findByChatIdAndStatusOrderByDeadlineAsc(Long chatId, String status);

    // Для утреннего дайджеста (берет все активные задачи у всех пользователей)
    List<Task> findByStatus(String status);

    void deleteByStatusAndCompletedAtBefore(String status, LocalDateTime time);
}