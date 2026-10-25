package com.lovemanagement.repo;

import com.lovemanagement.domain.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    List<TaskComment> findByTaskIdAndParentIdIsNullOrderByCreatedAtAsc(Long taskId);

    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId);

    List<TaskComment> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    long countByAuthorId(Long authorId);

    void deleteByTaskId(Long taskId);
}
