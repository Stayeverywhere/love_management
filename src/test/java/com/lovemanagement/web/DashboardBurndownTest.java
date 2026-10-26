package com.lovemanagement.web;

import com.lovemanagement.domain.ProjectTask;
import com.lovemanagement.domain.TaskStatus;
import com.lovemanagement.repo.ProjectTaskRepository;
import com.lovemanagement.repo.UserAccountRepository;
import com.lovemanagement.service.DashboardService;
import com.lovemanagement.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "classpath:db/test-baseline.sql")
class DashboardBurndownTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectTaskRepository taskRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private TaskService taskService;

    private ProjectTask newTask(TaskStatus status, LocalDate createdAt) {
        ProjectTask task = new ProjectTask();
        task.setTitle("燃尽任务-" + taskRepository.count() + status);
        task.setStatus(status);
        task.setPriority("MEDIUM");
        task.setCreator(userAccountRepository.findByUsername("admin").orElseThrow());
        task.setCreatedAt(createdAt.atStartOfDay().plusHours(9));
        return taskRepository.save(task);
    }

    @Test
    void changeStatusRecordsCompletedAt() {
        ProjectTask task = newTask(TaskStatus.TODO, LocalDate.now());
        taskService.changeStatus(task, TaskStatus.DONE);
        ProjectTask done = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(done.getCompletedAt()).isNotNull();

        taskService.changeStatus(done, TaskStatus.IN_PROGRESS);
        ProjectTask back = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(back.getCompletedAt()).isNull();
    }

    @Test
    void burndownSeriesHasExpectedShape() {
        // 4 个任务：2 个已完成（本周完成），2 个未完成
        newTask(TaskStatus.TODO, LocalDate.now().minusDays(10));
        newTask(TaskStatus.TODO, LocalDate.now().minusDays(8));
        ProjectTask done1 = newTask(TaskStatus.TODO, LocalDate.now().minusDays(9));
        ProjectTask done2 = newTask(TaskStatus.TODO, LocalDate.now().minusDays(6));
        taskService.changeStatus(done1, TaskStatus.DONE);
        taskService.changeStatus(done2, TaskStatus.DONE);

        DashboardService.BurndownSeries series = dashboardService.burndown();
        assertThat(series.labels()).hasSize(8);
        // 基准线：从总数线性递减到 0
        assertThat(series.ideal().get(0)).isEqualTo(4);
        assertThat(series.ideal().get(7)).isZero();
        // 实际剩余：总任务 - 已完成 = 2（完成时间在本周，计入最后一格）
        assertThat(series.remaining().get(7)).isEqualTo(2);
        // 剩余数单调不增（任务越做越少）
        for (int i = 1; i < series.remaining().size(); i++) {
            assertThat(series.remaining().get(i)).isLessThanOrEqualTo(series.remaining().get(i - 1));
        }
    }

    @Test
    void dashboardPageRendersBurndownChart() throws Exception {
        newTask(TaskStatus.TODO, LocalDate.now().minusDays(3));
        MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/login").user("admin").password("Admin1234"))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession();
        MvcResult result = mockMvc.perform(get("/pm/dashboard").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("burndownChart").contains("燃尽图");
    }

    @Test
    void homePageHighlightsDashboardNavForAdmin() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/login").user("admin").password("Admin1234"))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession();
        // /home 对管理员渲染数据看板视图
        MvcResult result = mockMvc.perform(get("/home").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("数据看板").contains("burndownChart");
        // 导航中数据看板按钮处于高亮态
        assertThat(body).contains("nav-item active").contains("/pm/dashboard");
    }
}
