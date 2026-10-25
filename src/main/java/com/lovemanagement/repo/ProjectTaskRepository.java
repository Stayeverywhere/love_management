package com.lovemanagement.repo;

import com.lovemanagement.domain.ProjectTask;
import com.lovemanagement.domain.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, Long> {

    List<ProjectTask> findAllByOrderByCreatedAtDesc();

    List<ProjectTask> findByStatusOrderByCreatedAtDesc(TaskStatus status);

    List<ProjectTask> findByAssigneeIsNullOrderByCreatedAtDesc();

    Page<ProjectTask> findAllBy(Pageable pageable);

    Page<ProjectTask> findByStatus(TaskStatus status, Pageable pageable);

    Page<ProjectTask> findByAssigneeIsNull(Pageable pageable);

    List<ProjectTask> findByAssigneeIdOrderByCreatedAtDesc(Long assigneeId);

    List<ProjectTask> findByAssignee_IdOrderByCreatedAtDesc(Long assigneeId);

    List<ProjectTask> findByStatusAndAssigneeIdOrderByCreatedAtDesc(TaskStatus status, Long assigneeId);

    long countByStatus(TaskStatus status);

    long countByAssigneeIsNotNull();

    long countByStatusNotAndRemindAtIsNotNull(TaskStatus status);

    long countByAssigneeIsNull();

    boolean existsByAssignee_Id(Long assigneeId);

    List<ProjectTask> findByCreatorIdOrderByCreatedAtDesc(Long creatorId);

    List<ProjectTask> findByCreatedAtGreaterThanEqual(java.time.LocalDateTime time);

    long countByCreatedAtGreaterThanEqual(java.time.LocalDateTime time);
}
