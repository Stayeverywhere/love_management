package com.lovemanagement.service;

import com.lovemanagement.domain.MemberStatus;
import com.lovemanagement.domain.TaskStatus;
import com.lovemanagement.repo.MemberRepository;
import com.lovemanagement.repo.ProjectTaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MM-dd");

    private final MemberRepository memberRepository;
    private final ProjectTaskRepository taskRepository;

    public DashboardService(MemberRepository memberRepository, ProjectTaskRepository taskRepository) {
        this.memberRepository = memberRepository;
        this.taskRepository = taskRepository;
    }

    public DashboardSnapshot snapshot() {
        return new DashboardSnapshot(
                memberRepository.count(),
                memberRepository.countByStatus(MemberStatus.ACTIVE),
                taskRepository.count(),
                taskRepository.countByAssigneeIsNotNull(),
                taskRepository.countByAssigneeIsNull(),
                taskRepository.countByStatus(TaskStatus.TODO),
                taskRepository.countByStatus(TaskStatus.IN_PROGRESS),
                taskRepository.countByStatus(TaskStatus.DONE),
                taskRepository.countByStatusNotAndRemindAtIsNotNull(TaskStatus.DONE)
        );
    }

    public TrendSeries last7Days() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        LocalDateTime from = start.atStartOfDay();
        Map<LocalDate, Long> counts = taskRepository.findByCreatedAtGreaterThanEqual(from).stream()
                .collect(Collectors.groupingBy(task -> task.getCreatedAt().toLocalDate(), Collectors.counting()));
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = start.plusDays(i);
            labels.add(day.format(LABEL));
            values.add(counts.getOrDefault(day, 0L));
        }
        return new TrendSeries(labels, values);
    }

    /** 按日（近7日）/按周（近8周，周一为一周起点）/按月（近6个月）查看趋势。 */
    public TrendSeries trend(String granularity) {
        if ("week".equalsIgnoreCase(granularity)) {
            return last8Weeks();
        }
        if ("month".equalsIgnoreCase(granularity)) {
            return last6Months();
        }
        return last7Days();
    }

    /**
     * 燃尽图（近 8 周，周一为一周起点，含本周）：
     * ideal = 期初应完成基准线（总任务数线性递减至 0），
     * remaining = 各周末实际剩余未完成数。
     * 旧数据无完成时间时，DONE 任务按创建时间计入完成。
     */
    public BurndownSeries burndown() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusWeeks(7).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<com.lovemanagement.domain.ProjectTask> tasks =
                taskRepository.findByCreatedAtGreaterThanEqual(start.atStartOfDay());
        int total = tasks.size();
        List<String> labels = new ArrayList<>();
        List<Long> ideal = new ArrayList<>();
        List<Long> remaining = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            LocalDate weekStart = start.plusWeeks(i);
            LocalDateTime weekEnd = weekStart.plusDays(6).atTime(23, 59);
            long done = tasks.stream().filter(t -> {
                java.time.LocalDateTime doneAt = t.getCompletedAt() != null
                        ? t.getCompletedAt()
                        : t.getCreatedAt();
                return t.getStatus() == TaskStatus.DONE && !doneAt.isAfter(weekEnd);
            }).count();
            labels.add(weekStart.format(LABEL) + "周");
            ideal.add(Math.round((double) total * (7 - i) / 7.0));
            remaining.add((long) (total - done));
        }
        return new BurndownSeries(labels, ideal, remaining);
    }

    public record BurndownSeries(List<String> labels, List<Long> ideal, List<Long> remaining) {
    }

    public TrendSeries last8Weeks() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusWeeks(7).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, Long> byDay = taskRepository.findByCreatedAtGreaterThanEqual(start.atStartOfDay()).stream()
                .collect(Collectors.groupingBy(task -> task.getCreatedAt().toLocalDate(), Collectors.counting()));
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            LocalDate weekStart = start.plusWeeks(i);
            long sum = 0;
            for (int d = 0; d < 7; d++) {
                sum += byDay.getOrDefault(weekStart.plusDays(d), 0L);
            }
            labels.add(weekStart.format(LABEL) + "周");
            values.add(sum);
        }
        return new TrendSeries(labels, values);
    }

    public TrendSeries last6Months() {
        YearMonth start = YearMonth.from(LocalDate.now()).minusMonths(5);
        Map<YearMonth, Long> byMonth = taskRepository.findByCreatedAtGreaterThanEqual(start.atDay(1).atStartOfDay()).stream()
                .collect(Collectors.groupingBy(task -> YearMonth.from(task.getCreatedAt()), Collectors.counting()));
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            YearMonth ym = start.plusMonths(i);
            labels.add(ym.toString());
            values.add(byMonth.getOrDefault(ym, 0L));
        }
        return new TrendSeries(labels, values);
    }

    public record DashboardSnapshot(
            long totalMembers,
            long activeMembers,
            long totalTasks,
            long assignedTasks,
            long unassignedTasks,
            long todoCount,
            long doingCount,
            long doneCount,
            long remindingTasks
    ) {
    }

    public record TrendSeries(List<String> labels, List<Long> values) {
    }
}
