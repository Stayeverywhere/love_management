package com.lovemanagement.service;

import com.lovemanagement.domain.AccountRole;
import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.MemberStatus;
import com.lovemanagement.domain.PolicyException;
import com.lovemanagement.domain.ProjectTask;
import com.lovemanagement.domain.TaskComment;
import com.lovemanagement.domain.TaskStatus;
import com.lovemanagement.domain.UserAccount;
import com.lovemanagement.repo.MemberRepository;
import com.lovemanagement.repo.ProjectTaskRepository;
import com.lovemanagement.repo.TaskCommentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskService {

    private final ProjectTaskRepository taskRepository;
    private final MemberRepository memberRepository;
    private final TaskCommentRepository commentRepository;

    public TaskService(ProjectTaskRepository taskRepository, MemberRepository memberRepository,
                       TaskCommentRepository commentRepository) {
        this.taskRepository = taskRepository;
        this.memberRepository = memberRepository;
        this.commentRepository = commentRepository;
    }

    public List<ProjectTask> listAll() {
        return taskRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<ProjectTask> listForMember(Long assigneeId) {
        if (assigneeId == null) {
            return List.of();
        }
        return taskRepository.findByAssignee_IdOrderByCreatedAtDesc(assigneeId);
    }

    public List<ProjectTask> listByStatus(TaskStatus status) {
        return taskRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public List<ProjectTask> listUnassigned() {
        return taskRepository.findByAssigneeIsNullOrderByCreatedAtDesc();
    }

    public Page<ProjectTask> pageAll(Pageable pageable) {
        return taskRepository.findAllBy(pageable);
    }

    public Page<ProjectTask> pageByStatus(TaskStatus status, Pageable pageable) {
        return taskRepository.findByStatus(status, pageable);
    }

    public Page<ProjectTask> pageUnassigned(Pageable pageable) {
        return taskRepository.findByAssigneeIsNull(pageable);
    }

    public List<ProjectTask> listByStatusAndMember(TaskStatus status, Long assigneeId) {
        if (assigneeId == null) {
            return List.of();
        }
        return taskRepository.findByStatusAndAssigneeIdOrderByCreatedAtDesc(status, assigneeId);
    }

    public ProjectTask get(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new PolicyException("任务不存在"));
    }

    public ProjectTask getVisible(Long id, UserAccount viewer) {
        ProjectTask task = get(id);
        if (viewer.getAccountRole() == AccountRole.MEMBER) {
            boolean own = task.getAssignee() != null && viewer.getMember() != null
                    && task.getAssignee().getId().equals(viewer.getMember().getId());
            if (!own) {
                throw new PolicyException("只能查看分配给自己的任务");
            }
        }
        return task;
    }

    @Transactional
    public ProjectTask create(String title, String description, UserAccount creator) {
        return create(title, description, null, null, null, creator);
    }

    @Transactional
    public ProjectTask create(String title, String description, String priority, String dueDate, UserAccount creator) {
        return create(title, description, priority, dueDate, null, creator);
    }

    @Transactional
    public ProjectTask create(String title, String description, String priority, String dueDate,
                              String remindAt, UserAccount creator) {
        ProjectTask task = new ProjectTask();
        fill(task, title, description, priority, dueDate, remindAt);
        task.setCreator(creator);
        return taskRepository.save(task);
    }

    @Transactional
    public ProjectTask update(ProjectTask task, String title, String description, String priority, String dueDate) {
        return update(task, title, description, priority, dueDate, null);
    }

    @Transactional
    public ProjectTask update(ProjectTask task, String title, String description, String priority, String dueDate,
                              String remindAt) {
        fill(task, title, description, priority, dueDate, remindAt);
        return taskRepository.save(task);
    }

    private void fill(ProjectTask task, String title, String description, String priority,
                      String dueDate, String remindAt) {
        task.setTitle(TaskTitlePolicy.normalize(title));
        task.setDescription(description == null ? null : description.trim());
        task.setPriority(normalizePriority(priority));
        task.setDueDate(parseDueDate(dueDate));
        task.setRemindAt(parseRemindAt(remindAt));
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "MEDIUM";
        }
        String value = priority.trim().toUpperCase();
        if (!value.equals("LOW") && !value.equals("MEDIUM") && !value.equals("HIGH")) {
            throw new PolicyException("优先级不合法");
        }
        return value;
    }

    private LocalDate parseDueDate(String dueDate) {
        if (dueDate == null || dueDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dueDate.trim());
        } catch (DateTimeParseException ex) {
            throw new PolicyException("截止日期格式不合法");
        }
    }

    private LocalDateTime parseRemindAt(String remindAt) {
        if (remindAt == null || remindAt.isBlank()) {
            return null;
        }
        String value = remindAt.trim().replace('T', ' ');
        LocalDateTime parsed;
        try {
            parsed = LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (DateTimeParseException ex) {
            try {
                parsed = LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
            } catch (DateTimeParseException ex2) {
                throw new PolicyException("提醒时间格式不合法，应为 yyyy-MM-dd HH:mm");
            }
        }
        if (parsed.isBefore(LocalDateTime.now())) {
            throw new PolicyException("提醒时间不能早于当前时间");
        }
        return parsed;
    }

    @Transactional
    public ProjectTask assign(Long taskId, Long memberId) {
        if (memberId == null) {
            throw new PolicyException("请选择任务负责人");
        }
        ProjectTask task = get(taskId);
        Member member = memberRepository.findById(memberId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new PolicyException("只能从在职成员中选择任务负责人"));
        task.setAssignee(member);
        task.setAssignedAt(java.time.LocalDateTime.now());
        return taskRepository.save(task);
    }

    @Transactional
    public ProjectTask changeStatus(ProjectTask task, TaskStatus status) {
        task.setStatus(status);
        task.setCompletedAt(status == TaskStatus.DONE ? LocalDateTime.now() : null);
        return taskRepository.save(task);
    }

    /** 管理员/项目经理可改任意任务；成员只能改自己创建或被分配的任务。 */
    public boolean canManageStatus(ProjectTask task, UserAccount user) {
        if (user.getAccountRole() != AccountRole.MEMBER) {
            return true;
        }
        if (task.getCreator() != null && task.getCreator().getId().equals(user.getId())) {
            return true;
        }
        return task.getAssignee() != null && user.getMember() != null
                && task.getAssignee().getId().equals(user.getMember().getId());
    }

    public void assertCanEdit(ProjectTask task, UserAccount user) {
        if (user.getAccountRole() != AccountRole.MEMBER) {
            return;
        }
        if (task.getCreator() == null || !task.getCreator().getId().equals(user.getId())) {
            throw new PolicyException("只能编辑自己创建的任务");
        }
    }

    public void assertCanDelete(ProjectTask task, UserAccount user) {
        assertCanEdit(task, user);
    }

    @Transactional
    public void delete(Long taskId) {
        commentRepository.deleteByTaskId(taskId);
        ProjectTask task = get(taskId);
        taskRepository.delete(task);
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 任务全部讨论记录，按时间倒序（最新在前）。 */
    public List<CommentView> commentTimeline(Long taskId) {
        List<TaskComment> all = commentRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
        List<CommentView> nodes = new ArrayList<>();
        for (TaskComment c : all) {
            CommentView node = new CommentView();
            node.setComment(c);
            node.setAuthorName(c.getAuthor() == null ? "" : c.getAuthor().getDisplayName());
            node.setTime(c.getCreatedAt() == null ? "" : TIME_FORMAT.format(c.getCreatedAt()));
            nodes.add(node);
        }
        return nodes;
    }

    public List<CommentView> commentTree(Long taskId) {
        List<TaskComment> all = commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
        List<CommentView> nodes = new ArrayList<>();
        Map<Long, CommentView> byId = new HashMap<>();
        for (TaskComment c : all) {
            CommentView node = new CommentView();
            node.setComment(c);
            node.setAuthorName(c.getAuthor() == null ? "" : c.getAuthor().getDisplayName());
            node.setTime(c.getCreatedAt() == null ? "" : TIME_FORMAT.format(c.getCreatedAt()));
            nodes.add(node);
            byId.put(c.getId(), node);
        }
        List<CommentView> tops = new ArrayList<>();
        for (CommentView node : nodes) {
            TaskComment c = node.getComment();
            if (c.getParent() == null) {
                tops.add(node);
            } else {
                CommentView parent = byId.get(c.getParent().getId());
                if (parent != null) {
                    parent.getReplies().add(node);
                } else {
                    tops.add(node);
                }
            }
        }
        return tops;
    }

    @Transactional
    public TaskComment addComment(ProjectTask task, UserAccount author, String content, Long parentId) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new PolicyException("评论内容不能为空");
        }
        if (text.length() > 500) {
            throw new PolicyException("评论内容不能超过500字");
        }
        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setAuthor(author);
        if (parentId != null) {
            TaskComment parent = commentRepository.findById(parentId)
                    .filter(p -> p.getTask() != null && p.getTask().getId().equals(task.getId()))
                    .orElseThrow(() -> new PolicyException("回复的评论不存在"));
            comment.setParent(parent);
        }
        comment.setContent(text);
        return commentRepository.save(comment);
    }
}
