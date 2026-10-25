package com.lovemanagement.web;

import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.MemberStatus;
import com.lovemanagement.domain.ProjectTask;
import com.lovemanagement.domain.TaskComment;
import com.lovemanagement.domain.TaskStatus;
import com.lovemanagement.domain.UserAccount;
import com.lovemanagement.repo.MemberRepository;
import com.lovemanagement.repo.ProjectTaskRepository;
import com.lovemanagement.repo.TaskCommentRepository;
import com.lovemanagement.repo.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin", roles = "ADMIN")
@Transactional
@Sql(scripts = "classpath:db/test-baseline.sql")
class SprintThreeFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectTaskRepository taskRepository;
    @Autowired
    private TaskCommentRepository commentRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Member createMember(String name) {
        Member member = new Member();
        member.setName(name);
        member.setStatus(MemberStatus.ACTIVE);
        return memberRepository.save(member);
    }

    private ProjectTask createTask(String title, UserAccount creator) {
        ProjectTask task = new ProjectTask();
        task.setTitle(title);
        task.setStatus(TaskStatus.TODO);
        task.setPriority("MEDIUM");
        task.setCreator(creator);
        return taskRepository.save(task);
    }

    @Test
    void securityQuestionCanBeSavedAndInconsistentInputIsRejected() throws Exception {
        mockMvc.perform(post("/profile/security")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .param("securityQuestion", "你最喜欢的城市？")
                        .param("securityAnswer", "深圳"))
                .andExpect(redirectedUrl("/profile"));

        mockMvc.perform(get("/profile").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("密保设置")))
                .andExpect(content().string(containsString("你最喜欢的城市？")));

        // 只填问题不填答案会被拒绝
        mockMvc.perform(post("/profile/security")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .param("securityQuestion", "只有问题？"))
                .andExpect(redirectedUrl("/profile"));
    }

    @Test
    void taskRemindTimeCanBeSetAndShownOnDetailAndBoard() throws Exception {
        UserAccount pm = userAccountRepository.findByUsername("pm").orElseThrow();
        ProjectTask task = createTask("带提醒的任务", pm);
        task.setRemindAt(LocalDateTime.parse("2026-10-25T09:30"));
        taskRepository.save(task);

        mockMvc.perform(get("/tasks/" + task.getId()).with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("提醒时间")))
                .andExpect(content().string(containsString("2026-10-25 09:30")));

        mockMvc.perform(get("/pm/dashboard").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("待提醒任务")));
    }

    @Test
    void commentHistoryPageShowsNewestFirst() throws Exception {
        UserAccount pm = userAccountRepository.findByUsername("pm").orElseThrow();
        ProjectTask task = createTask("讨论任务", pm);
        TaskComment older = new TaskComment();
        older.setTask(task);
        older.setAuthor(pm);
        older.setContent("较早的评论");
        commentRepository.save(older);
        older.setCreatedAt(LocalDateTime.now().minusHours(1));
        commentRepository.save(older);
        TaskComment newer = new TaskComment();
        newer.setTask(task);
        newer.setAuthor(pm);
        newer.setContent("较新的评论");
        commentRepository.save(newer);

        String content = mockMvc.perform(get("/tasks/" + task.getId() + "/discussions")
                        .with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("任务讨论记录")))
                .andExpect(content().string(containsString("较早的评论")))
                .andExpect(content().string(containsString("较新的评论")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 较新的评论排在较早的评论之前
        org.assertj.core.api.Assertions.assertThat(content.indexOf("较新的评论"))
                .isLessThan(content.indexOf("较早的评论"));
    }

    @Test
    void trendCanBeViewedWithWeekAndMonthGranularity() throws Exception {
        mockMvc.perform(get("/pm/trends?granularity=week").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("近 8 周")));

        mockMvc.perform(get("/pm/trends?granularity=month").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("近 6 个月")));
    }

    @Test
    void perPersonReportShowsTaskDistribution() throws Exception {
        UserAccount pm = userAccountRepository.findByUsername("pm").orElseThrow();
        Member a = createMember("报表成员甲");
        Member b = createMember("报表成员乙");

        ProjectTask t1 = createTask("报表任务1", pm);
        t1.setAssignee(a);
        taskRepository.save(t1);
        ProjectTask t2 = createTask("报表任务2", pm);
        t2.setAssignee(b);
        t2.setStatus(TaskStatus.DONE);
        taskRepository.save(t2);
        createTask("报表未分配任务", pm);

        mockMvc.perform(get("/pm/reports").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("成员任务分布")))
                .andExpect(content().string(containsString("报表成员甲")))
                .andExpect(content().string(containsString("报表成员乙")))
                .andExpect(content().string(containsString("未分配")));
    }

    @Test
    void perPersonReportIsPaginated() throws Exception {
        for (int i = 1; i <= 12; i++) {
            createMember(String.format("报表分页成员%02d", i));
        }

        // 第 1 页：12 行中前 10 行（按姓名升序），不展示第 11 个
        mockMvc.perform(get("/pm/reports").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("共 12 条 · 第 1 / 2 页")))
                .andExpect(content().string(containsString("报表分页成员01")))
                .andExpect(content().string(containsString("报表分页成员10")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("报表分页成员11"))))
                .andExpect(content().string(containsString("下一页")))
                .andExpect(content().string(containsString("跳至")));

        // 第 2 页：剩余 2 行，可跳回上一页
        mockMvc.perform(get("/pm/reports?page=2").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("第 2 / 2 页")))
                .andExpect(content().string(containsString("报表分页成员11")))
                .andExpect(content().string(containsString("报表分页成员12")))
                .andExpect(content().string(containsString("上一页")));

        // 跳转控件携带 size 参数
        mockMvc.perform(get("/pm/reports?size=5&page=3").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("第 3 / 3 页")))
                .andExpect(content().string(containsString("/pm/reports?size=5")));
    }

    @Test
    void personalStatsPageShowsWorkload() throws Exception {
        UserAccount pm = userAccountRepository.findByUsername("pm").orElseThrow();
        UserAccount memberAccount = userAccountRepository.findByUsername("member").orElseThrow();
        Member member = createMember("统计成员");
        memberAccount.setMember(member);
        userAccountRepository.save(memberAccount);

        ProjectTask task = createTask("个人统计任务", pm);
        task.setAssignee(member);
        task.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.save(task);

        mockMvc.perform(get("/stats/personal").with(user("member").roles("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("我的个人统计")))
                .andExpect(content().string(containsString("个人统计任务")))
                .andExpect(content().string(containsString("我发表的评论")));
    }
}
