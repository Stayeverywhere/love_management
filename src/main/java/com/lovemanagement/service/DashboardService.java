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
