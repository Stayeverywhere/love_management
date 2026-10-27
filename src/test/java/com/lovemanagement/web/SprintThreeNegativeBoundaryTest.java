package com.lovemanagement.web;

import com.lovemanagement.domain.AccountRole;
import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.MemberStatus;
import com.lovemanagement.domain.ProjectTask;
import com.lovemanagement.domain.UserAccount;
import com.lovemanagement.repo.MemberRepository;
import com.lovemanagement.repo.ProjectTaskRepository;
import com.lovemanagement.repo.UserAccountRepository;
import com.lovemanagement.service.DashboardService;
import com.lovemanagement.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 3 用户故事（US-3-01~08）反向与边界测试：
 * - 无评论讨论页空状态、任务不存在的讨论页被拒（US-3-03）
 * - 普通成员越权访问按人分布报表被拒（US-3-05）
 * - 周/月趋势聚合与源数据一致、空周/空月补 0（US-3-04/07）
 * - 成员个人统计只能看到自己的工作量（US-3-06/08）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "classpath:db/test-baseline.sql")
class SprintThreeNegativeBoundaryTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ProjectTaskRepository taskRepository;
    @Autowired
    private TaskService taskService;
    @Autowired
    private DashboardService dashboardService;

    // ---------- US-3-03 反向：空讨论状态 ----------

    @Test
    void discussionsWithoutCommentsShowsEmptyState() throws Exception {
        UserAccount admin = userAccountRepository.findByUsername("admin").orElseThrow();
        ProjectTask task = taskService.create("无评论任务", "d", "MEDIUM",
                LocalDate.now().plusDays(2).toString(), null, admin);
        String body = mockMvc.perform(get("/tasks/" + task.getId() + "/discussions")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("还没有讨论记录");
    }

    // ---------- US-3-03 反向：任务不存在的讨论页被拒 ----------

    @Test
    void discussionsOfMissingTaskIsRejected() throws Exception {
        mockMvc.perform(get("/tasks/999999/discussions").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    // ---------- US-3-05 反向：普通成员越权访问按人分布报表 ----------

    @Test
    void memberCannotAccessPerPersonReport() throws Exception {
        Member m = new Member();
        m.setName("报表越权成员");
        m.setStatus(MemberStatus.ACTIVE);
        m = memberRepository.save(m);
        UserAccount ua = new UserAccount();
        ua.setUsername("reportmember");
        ua.setPasswordHash("{noop}pass1234");
        ua.setAccountRole(AccountRole.MEMBER);
        ua.setDisplayName("报表越权成员");
        ua.setMember(m);
        userAccountRepository.save(ua);

        mockMvc.perform(get("/pm/reports").with(user("reportmember").roles("MEMBER")))
                .andExpect(status().isForbidden());
    }

    // ---------- US-3-04 / US-3-07 边界：趋势聚合与源数据一致、空周/空月补 0 ----------

    @Test
    void weeklyTrendMatchesSourceAndEmptyBucketsAreZero() {
        UserAccount admin = userAccountRepository.findByUsername("admin").orElseThrow();
        taskService.create("周趋势A", "d", "MEDIUM", LocalDate.now().plusDays(2).toString(), null, admin);
        taskService.create("周趋势B", "d", "MEDIUM", LocalDate.now().plusDays(2).toString(), null, admin);
        taskService.create("周趋势C", "d", "MEDIUM", LocalDate.now().plusDays(2).toString(), null, admin);

        DashboardService.TrendSeries week = dashboardService.trend("week");
        assertThat(week.labels()).hasSize(8);
        for (Long v : week.values()) {
            assertThat(v).isGreaterThanOrEqualTo(0);
        }
        long weekSum = week.values().stream().mapToLong(Long::longValue).sum();
        assertThat(weekSum).isGreaterThanOrEqualTo(3);

        DashboardService.TrendSeries month = dashboardService.trend("month");
        assertThat(month.labels()).hasSize(6);
        for (Long v : month.values()) {
            assertThat(v).isGreaterThanOrEqualTo(0);
        }
        // 近 6 个月中，除本月外至少有一个空月补 0
        assertThat(month.values().stream().filter(v -> v == 0).count()).isGreaterThanOrEqualTo(1);
    }

    // ---------- US-3-06 / US-3-08 反向：成员个人统计只看自己的工作量 ----------

    @Test
    void memberOnlySeesOwnWorkload() throws Exception {
        Member mine = new Member();
        mine.setName("工作量成员");
        mine.setStatus(MemberStatus.ACTIVE);
        mine = memberRepository.save(mine);
        Member other = new Member();
        other.setName("他人成员");
        other.setStatus(MemberStatus.ACTIVE);
        other = memberRepository.save(other);
        UserAccount ua = new UserAccount();
        ua.setUsername("wlmember");
        ua.setPasswordHash("{noop}pass1234");
        ua.setAccountRole(AccountRole.MEMBER);
        ua.setDisplayName("工作量成员");
        ua.setMember(mine);
        userAccountRepository.save(ua);

        UserAccount admin = userAccountRepository.findByUsername("admin").orElseThrow();
        ProjectTask mineTask = taskService.create("我的专属任务", "d", "MEDIUM",
                LocalDate.now().plusDays(2).toString(), null, admin);
        ProjectTask otherTask = taskService.create("他人专属任务", "d", "MEDIUM",
                LocalDate.now().plusDays(2).toString(), null, admin);
        taskService.assign(mineTask.getId(), mine.getId());
        taskService.assign(otherTask.getId(), other.getId());

        String body = mockMvc.perform(get("/stats/personal").with(user("wlmember").roles("MEMBER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("我的专属任务");
        assertThat(body).doesNotContain("他人专属任务");
    }
}
