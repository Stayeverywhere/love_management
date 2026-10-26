package com.lovemanagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "project_task")
public class ProjectTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    /** 任务优先级：LOW / MEDIUM / HIGH，默认 MEDIUM。 */
    @Column(nullable = false, length = 10)
    private String priority;

    /** 截止日期，可空。 */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @ManyToOne
    @JoinColumn(name = "assignee_id")
    private Member assignee;

    @ManyToOne
    @JoinColumn(name = "creator_id")
    private UserAccount creator;

    private LocalDateTime createdAt;

    private LocalDateTime assignedAt;

    /** 完成时间（状态变更为 DONE 时记录），可空。 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 提醒时间，可空。 */
    @Column(name = "remind_at")
    private LocalDateTime remindAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (priority == null) {
            priority = "MEDIUM";
        }
        if (status == null) {
            status = TaskStatus.TODO;
        }
    }

    public String getPriorityLabel() {
        return switch (priority) {
            case "LOW" -> "低";
            case "HIGH" -> "高";
            default -> "中";
        };
    }

    public String getPriorityClass() {
        return switch (priority) {
            case "LOW" -> "low";
            case "HIGH" -> "high";
            default -> "mid";
        };
    }

    public String getDueDateText() {
        return dueDate == null ? "" : dueDate.toString();
    }

    public String getRemindAtText() {
        return remindAt == null ? "" : remindAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public boolean isOverdue() {
        return dueDate != null && status != TaskStatus.DONE && dueDate.isBefore(LocalDate.now());
    }
}
