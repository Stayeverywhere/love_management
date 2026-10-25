package com.lovemanagement.service;

import com.lovemanagement.domain.ProjectTask;
import com.lovemanagement.domain.TaskStatus;
import com.lovemanagement.domain.UserAccount;
import com.lovemanagement.repo.ProjectTaskRepository;
import com.lovemanagement.repo.TaskCommentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** 个人统计：个人工作量（名下任务 + 创建任务 + 参与评论）。 */
@Service
public class PersonalStatsService {

    private final ProjectTaskRepository taskRepository;
    private final TaskCommentRepository commentRepository;

    public PersonalStatsService(ProjectTaskRepository taskRepository, TaskCommentRepository commentRepository) {
        this.taskRepository = taskRepository;
        this.commentRepository = commentRepository;
    }

    public PersonalStats statsOf(UserAccount account) {
        List<ProjectTask> assigned = account.getMember() == null
                ? List.of()
                : taskRepository.findByAssignee_IdOrderByCreatedAtDesc(account.getMember().getId());
        List<ProjectTask> created = taskRepository.findByCreatorIdOrderByCreatedAtDesc(account.getId());
        long comments = commentRepository.countByAuthorId(account.getId());
        return new PersonalStats(
                assigned.size(),
                count(assigned, TaskStatus.TODO),
                count(assigned, TaskStatus.IN_PROGRESS),
                count(assigned, TaskStatus.DONE),
                created.size(),
                comments,
                assigned.size() > 10 ? assigned.subList(0, 10) : assigned,
                created.size() > 10 ? created.subList(0, 10) : created);
    }

    private long count(List<ProjectTask> tasks, TaskStatus status) {
        return tasks.stream().filter(task -> task.getStatus() == status).count();
    }

    public record PersonalStats(
            long assignedTotal,
            long assignedTodo,
            long assignedInProgress,
            long assignedDone,
            long created,
            long comments,
            List<ProjectTask> assignedTasks,
            List<ProjectTask> createdTasks
    ) {
    }
}
