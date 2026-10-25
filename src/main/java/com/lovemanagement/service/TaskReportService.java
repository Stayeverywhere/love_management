package com.lovemanagement.service;

import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.MemberStatus;
import com.lovemanagement.domain.ProjectTask;
import com.lovemanagement.domain.TaskStatus;
import com.lovemanagement.repo.MemberRepository;
import com.lovemanagement.repo.ProjectTaskRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 按人查看任务分布（分析报表）。 */
@Service
public class TaskReportService {

    private final MemberRepository memberRepository;
    private final ProjectTaskRepository taskRepository;

    public TaskReportService(MemberRepository memberRepository, ProjectTaskRepository taskRepository) {
        this.memberRepository = memberRepository;
        this.taskRepository = taskRepository;
    }

    /** 每位在职成员的任务分布 + 未分配行。percent 为占总任务数的百分比（如 "12.5%"，总数为 0 时为空串）。 */
    public ReportResult distribution() {
        Map<Long, List<ProjectTask>> byAssignee = taskRepository.findAll().stream()
                .filter(task -> task.getAssignee() != null)
                .collect(Collectors.groupingBy(task -> task.getAssignee().getId()));
        long unassigned = taskRepository.countByAssigneeIsNull();
        long totalAll = byAssignee.values().stream().mapToLong(List::size).sum() + unassigned;
        List<ReportRow> rows = new ArrayList<>();
        for (Member member : memberRepository.findByStatusOrderByNameAsc(MemberStatus.ACTIVE)) {
            List<ProjectTask> tasks = byAssignee.getOrDefault(member.getId(), List.of());
            rows.add(new ReportRow(
                    member.getName(),
                    tasks.size(),
                    count(tasks, TaskStatus.TODO),
                    count(tasks, TaskStatus.IN_PROGRESS),
                    count(tasks, TaskStatus.DONE),
                    percent(tasks.size(), totalAll)));
        }
        return new ReportResult(rows, unassigned, totalAll, percent(unassigned, totalAll));
    }

    private String percent(long value, long total) {
        if (total <= 0 || value <= 0) {
            return "";
        }
        return String.format("%.1f%%", value * 100.0 / total);
    }

    private long count(List<ProjectTask> tasks, TaskStatus status) {
        return tasks.stream().filter(task -> task.getStatus() == status).count();
    }

    public record ReportRow(String name, long total, long todo, long inProgress, long done, String percent) {
    }

    public record ReportResult(List<ReportRow> rows, long unassigned, long totalAll, String unassignedPercent) {
    }
}
