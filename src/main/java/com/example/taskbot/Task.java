package com.example.taskbot;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private String text;
    private String status; // "ACTIVE" или "COMPLETED"
    private String priority; // "🔴 ВЫСОКИЙ", "🟡 СРЕДНИЙ", "🟢 НИЗКИЙ"
    private LocalDateTime deadline;
    private LocalDateTime completedAt;

    public Task() {}

    public Task(Long chatId, String text, String priority, LocalDateTime deadline, String status) {
        this.chatId = chatId;
        this.text = text;
        this.priority = priority;
        this.deadline = deadline;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}