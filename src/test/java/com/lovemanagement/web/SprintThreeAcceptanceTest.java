package com.lovemanagement.web;

import com.lovemanagement.domain.AccountRole;
import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.MemberStatus;
import com.lovemanagement.domain.PolicyException;
import com.lovemanagement.domain.ProjectTask;
import com.lovemanagement.domain.TaskStatus;
import com.lovemanagement.domain.UserAccount;
import com.lovemanagement.repo.MemberRepository;
import com.lovemanagement.repo.ProjectTaskRepository;
import com.lovemanagement.repo.UserAccountRepository;
import com.lovemanagement.service.SecurityPolicy;
import com.lovemanagement.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithUserDetails;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 3 验收标准（US-3-01~08）补齐场景的 E2E 与异常测试：
 * - 密保答案加密落库、profile「已设置」回显（US-3-01）
 * - 提醒时间过去时间被拒绝（US-3-02）
 * - 讨论页越权返回 403（US-3-03）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "classpath:db/test-baseline.sql")
class SprintThreeAcceptanceTest {

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

    // ---------- US-3-01 设置密保 ----------

    @Test
    void securityAnswerIsHashedNotPlaintext() {
        // 加密存储：落库值不等于明文，且为 64 位十六进制摘要
        String stored = SecurityPolicy.normalizeAnswer("我的密保答案123");
        assertThat(stored).isNotEqualTo("我的密保答案123");
        assertThat(stored).hasSize(64);
        // 幂等性不受明文影响：两次相同明文 → 相同摘要
        assertThat(SecurityPolicy.normalizeAnswer("我的密保答案123")).isEqualTo(stored);
    }

    @Test
    void profileShowsSecurityQuestionWhenSet() throws Exception {
        UserAccount admin = userAccountRepository.findByUsername("admin").orElseThrow();
        admin.setSecurityQuestion("你的第一只宠物叫什么？");
        admin.setSecurityAnswer(SecurityPolicy.normalizeAnswer("answer123"));
        userAccountRepository.save(admin);

        MockHttpSession session = login("admin", "Admin1234");
        mockMvc.perform(get("/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("已设置密保问题：你的第一只宠物叫什么？")));
        // 答案不以明文展示（页面不应出现答案明文）
        String body = mockMvc.perform(get("/profile").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("answer123");
    }

    @Test
    void profileDoesNotShowSecurityBannerWhenUnset() throws Exception {
        MockHttpSession session = login("pm", "Pm123456");
        String body = mockMvc.perform(get("/profile").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("已设置密保问题");
    }

    // ---------- US-3-02 设置任务提醒 ----------

    @Test
    void pastRemindTimeIsRejected() {
        UserAccount admin = userAccountRepository.findByUsername("admin").orElseThrow();
        assertThatThrownBy(() ->
                taskService.create("过去提醒任务", "描述", "MEDIUM",
                        LocalDate.now().plusDays(1).toString(),
                        LocalDateTime.now().minusHours(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        admin))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("不能早于当前时间");
    }

    @Test
    void invalidRemindTimeFormatIsRejected() {
        UserAccount admin = userAccountRepository.findByUsername("admin").orElseThrow();
        assertThatThrownBy(() ->
                taskService.create("坏格式提醒", "描述", "MEDIUM",
                        LocalDate.now().plusDays(1).toString(), "2026/10/27 25:99", admin))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("格式不合法");
    }

    @Test
    void futureRemindTimeIsAccepted() {
        UserAccount admin = userAccountRepository.findByUsername("admin").orElseThrow();
        ProjectTask task = taskService.create("未来提醒任务", "描述", "MEDIUM",
                LocalDate.now().plusDays(2).toString(),
                LocalDateTime.now().plusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                admin);
        assertThat(task.getRemindAt()).isNotNull().isAfter(LocalDateTime.now());
    }

    // ---------- US-3-03 查看评论记录 ----------

    @Test
    void memberCannotSeeOtherTaskDiscussions() throws Exception {
        // 创建成员账号并绑定在职成员
        Member m = new Member();
        m.setName("讨论越权成员");
        m.setStatus(MemberStatus.ACTIVE);
        m = memberRepository.save(m);
        UserAccount ua = new UserAccount();
        ua.setUsername("discmember");
        ua.setPasswordHash("{noop}pass1234");
        ua.setAccountRole(AccountRole.MEMBER);
        ua.setDisplayName("讨论越权成员");
        ua.setMember(m);
        userAccountRepository.save(ua);

        // 管理员创建一个未分配的任务（不属于该成员）
        UserAccount admin = userAccountRepository.findByUsername("admin").orElseThrow();
        ProjectTask task = taskService.create("他人任务", "描述", "MEDIUM",
                LocalDate.now().plusDays(3).toString(), null, admin);

        // 该成员访问该任务讨论页 → 403
        mockMvc.perform(get("/tasks/" + task.getId() + "/discussions")
                        .with(user("discmember").roles("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void assigneeCanSeeOwnTaskDiscussions() throws Exception {
        Member m = new Member();
        m.setName("讨论正常成员");
        m.setStatus(MemberStatus.ACTIVE);
        m = memberRepository.save(m);
        UserAccount ua = new UserAccount();
        ua.setUsername("discmember2");
        ua.setPasswordHash("{noop}pass1234");
        ua.setAccountRole(AccountRole.MEMBER);
        ua.setDisplayName("讨论正常成员");
        ua.setMember(m);
        userAccountRepository.save(ua);

        UserAccount admin = userAccountRepository.findByUsername("admin").orElseThrow();
        ProjectTask task = taskService.create("我的任务", "描述", "MEDIUM",
                LocalDate.now().plusDays(3).toString(), null, admin);
        taskService.assign(task.getId(), m.getId());

        mockMvc.perform(get("/tasks/" + task.getId() + "/discussions")
                        .with(user("discmember2").roles("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("讨论")));
    }

    // ---------- US-3-08 查看个人工作量（统计口径说明） ----------

    @Test
    void personalStatsPageShowsCaliberNote() throws Exception {
        MockHttpSession session = login("admin", "Admin1234");
        String body = mockMvc.perform(get("/stats/personal").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 页面须说明统计口径（US-3-08：统计口径有说明）
        assertThat(body).contains("统计口径").contains("分配给我").contains("我创建");
    }

    // ---------- helper ----------

    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login").user(username).password(password))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession();
    }
}
