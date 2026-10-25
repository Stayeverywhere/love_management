import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 生成符合库表约束的模拟数据 SQL 文件：db/mysql/mock_data.sql
 *
 * 用法（项目根目录）：
 *   java tools/GenerateMockData.java
 *
 * 生成内容（全部满足长度/格式/外键约束）：
 *   - team_member          300 名成员（约 90% 在职）
 *   - account_user          20 个登录账号（1 管理员、5 项目经理、14 绑定在职成员的团队成员账号）
 *   - project_task         300 个任务，创建于近 30 天，约 60% 分配给在职成员
 *
 * 说明：
 *   - 随机种子固定（42），重复运行生成相同数据，便于排查与复现。
 *   - 脚本执行前会 DROP 业务表（保留基线表中的权限、角色不动），因此可重复执行；
 *     会清空现有的成员、账号、任务数据。
 *   - 团队成员账号的密码统一使用脚本头注释中给出的 BCrypt 哈希（密码 mock123456）。
 */
public class GenerateMockData {

    private static final int MEMBER_COUNT = 300;
    private static int TASK_COUNT = 300;
    private static final int PM_COUNT = 5;
    private static final int MEMBER_ACCOUNT_COUNT = 14;
    private static final Random R = new Random(42);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 团队成员账号统一密码 mock123456 的 BCrypt 哈希（由 BCryptPasswordEncoder 生成）
    private static final String PM_HASH = "$2a$10$DAsSgftxMPOp1nLVHeReAelq/Bg9V/oSm6mzxtian80wSNSI2Av1O";
    private static final String MEMBER_HASH = "$2a$10$Jgdg07Qn9jE8ghVKv5OWLuQDP2WliqCgeeYXD1zeDBsRnNKoF8HfW";

    private static final String[] SURNAMES = {
            "张", "王", "李", "赵", "刘", "陈", "杨", "黄", "周", "吴",
            "徐", "孙", "马", "朱", "胡", "郭", "何", "林", "罗", "高",
            "郑", "梁", "谢", "宋", "唐", "许", "韩", "冯", "邓", "曹"};
    private static final String[] GIVEN = {
            "伟", "芳", "娜", "敏", "静", "磊", "军", "洋", "勇", "艳",
            "杰", "娟", "涛", "明", "超", "秀英", "霞", "平", "刚", "桂英",
            "婷", "强", "辉", "丽", "鑫", "鹏", "飞", "玲", "宇", "雪",
            "晨", "思远", "子涵", "雨欣", "浩然", "梓萱", "嘉懿", "欣怡", "天佑", "梦琪"};
    private static final String[] POSITIONS = {
            "前端开发", "后端开发", "测试", "产品", "设计", "运维", "项目经理", "数据分析"};
    private static final String[] TASK_A = {
            "完成", "整理", "排查", "优化", "评审", "编写", "核对", "修复", "设计", "部署"};
    private static final String[] TASK_B = {
            "登录模块", "权限校验", "成员列表", "任务看板", "趋势图表", "接口文档",
            "数据备份", "缓存策略", "通知功能", "报表导出", "搜索功能", "日志清理",
            "版本发布", "配置迁移", "缺陷工单", "回归测试", "性能压测", "需求梳理"};
    private static final String[] TASK_DESC = {
            "按迭代计划推进，完成后同步到看板并更新状态。",
            "涉及多端联调，需要测试同学配合回归验证。",
            "优先处理用户反馈集中反映的问题，修复后补充说明。",
            "先输出方案再实施，方案评审通过后进入开发。",
            "关注性能指标，完成后记录优化前后的对比数据。",
            "需要与产品确认边界场景后开始开发。",
            "历史数据需要迁移，执行前先在测试环境演练。",
            "上线前完成灰度验证，确认无误后全量发布。"};

    public static void main(String[] args) throws IOException {
        List<String> members = new ArrayList<>();   // 成员 SQL 行
        List<String> accounts = new ArrayList<>();  // 账号 SQL 行
        List<String> tasks = new ArrayList<>();     // 任务 SQL 行
        LocalDateTime now = LocalDateTime.now();

        // ---------- 成员 ----------
        List<Integer> activeIds = new ArrayList<>();
        for (int i = 1; i <= MEMBER_COUNT; i++) {
            String name = randomName();
            MEMBER_NAMES.add(name);
            String status = R.nextDouble() < 0.9 ? "ACTIVE" : "INACTIVE";
            if ("ACTIVE".equals(status)) {
                activeIds.add(i);
            }
            members.add("    (" + i + ", '" + name + "', 'member" + i
                    + "@example.com', '" + randomPhone() + "', '" + pick(POSITIONS) + "', '" + status + "')");
        }

        // ---------- 账号 ----------
        accounts.add("    (101, 'admin', '$2a$10$Ww9hG4drFxcu0RDYMz37keQW95wzb4QOmtfYVBlp3PQ7NZvsJfWoy', '系统管理员', 'ADMIN', NULL)");
        for (int i = 1; i <= PM_COUNT; i++) {
            accounts.add("    (" + (101 + i) + ", 'pm" + i + "', '" + PM_HASH + "', '项目经理" + i + "', 'PROJECT_MANAGER', NULL)");
        }
        List<Integer> accountIds = new ArrayList<>();
        for (int i = 1; i <= MEMBER_ACCOUNT_COUNT; i++) {
            int memberId = activeIds.get(R.nextInt(activeIds.size()));
            accountIds.add(101 + PM_COUNT + i);
            accounts.add("    (" + (101 + PM_COUNT + i) + ", 'user" + pad(i) + "', '" + MEMBER_HASH
                    + "', '" + nameOf(memberId) + "', 'MEMBER', " + memberId + ")");
        }

        // ---------- 任务 ----------
        List<Integer> creators = new ArrayList<>();
        for (int i = 101; i <= 101 + PM_COUNT; i++) {
            creators.add(i);
        }
        for (int i = 1; i <= TASK_COUNT; i++) {
            int creator = pick(creators);
            LocalDateTime createdAt = now.minusDays(R.nextInt(30)).minusHours(R.nextInt(24))
                    .minusMinutes(R.nextInt(60));
            String title = pick(TASK_A) + pick(TASK_B) + "第" + (R.nextInt(50) + 1) + "期";
            String desc = R.nextDouble() < 0.2 ? null : pick(TASK_DESC);
            Long assignee = null;
            LocalDateTime assignedAt = null;
            String status;
            if (R.nextDouble() < 0.4) {
                // 未分配负责人的任务保持待处理状态
                status = "TODO";
            } else {
                assignee = (long) activeIds.get(R.nextInt(activeIds.size()));
                assignedAt = createdAt.plusHours(1).plusMinutes(R.nextInt(58));
                status = R.nextDouble() < 0.5 ? "IN_PROGRESS" : "DONE";
            }
            String assigneeSql = (assignee == null) ? "NULL" : String.valueOf(assignee);
            String assignedAtSql = (assignedAt == null) ? "NULL" : "'" + FMT.format(assignedAt) + "'";
            String priority = pick(new String[]{"LOW", "MEDIUM", "MEDIUM", "MEDIUM", "HIGH"});
            LocalDate due = R.nextDouble() < 0.5 ? createdAt.toLocalDate().plusDays(R.nextInt(41) - 10) : null;
            String dueSql = (due == null) ? "NULL" : "'" + due + "'";
            tasks.add("    (" + i + ", '" + title + "', " + sqlStr(desc) + ", '" + status + "', '"
                    + priority + "', " + dueSql + ", "
                    + assigneeSql + ", " + creator + ", '" + FMT.format(createdAt) + "', "
                    + assignedAtSql + ")");
        }

        // ---------- 输出 SQL ----------
        Path out = Path.of("db/mysql/mock_data.sql");
        Files.createDirectories(out.getParent());
        try (PrintStream ps = new PrintStream(Files.newOutputStream(out), true, StandardCharsets.UTF_8)) {
            ps.println("-- ============================================================");
            ps.println("-- 爱管理 模拟数据导入脚本（MySQL 8.x）");
            ps.println("-- 由 tools/GenerateMockData.java 生成（随机种子固定，可重复生成同一份数据）");
            ps.println("--");
            ps.println("-- 前置条件：已执行 db/mysql/love_management.sql 建好 love_management 库");
            ps.println("-- 使用：mysql -u root -p --default-character-set=utf8mb4 love_management < db/mysql/mock_data.sql");
            ps.println("--");
            ps.println("-- 注意：");
            ps.println("--   1) 会 DROP 并重刷 team_member / account_user / project_task 三张表，");
            ps.println("--      权限、角色基线表不受影响；现有成员、账号、任务数据会被清空。");
            ps.println("--   2) 登录账号：admin(密码 Admin1234)、pm1~pm5(密码 Pm123456)、");
            ps.println("--      user001~user014(密码 mock123456，已绑定在职成员)。");
            ps.println("--   3) 统计：成员 " + MEMBER_COUNT + "、账号 " + (1 + PM_COUNT + MEMBER_ACCOUNT_COUNT)
                    + "、任务 " + TASK_COUNT + "（近 30 天）。");
            ps.println("-- ============================================================");
            ps.println();
            ps.println("USE love_management;");
            ps.println("SET NAMES utf8mb4;");
            ps.println("SET autocommit = 0;");
            ps.println("SET FOREIGN_KEY_CHECKS = 0;");
            ps.println();
            ps.println("DROP TABLE IF EXISTS task_comment;");
            ps.println("DROP TABLE IF EXISTS project_task;");
            ps.println("DROP TABLE IF EXISTS account_user;");
            ps.println("DROP TABLE IF EXISTS team_member;");
            ps.println();
            ps.println("CREATE TABLE team_member (");
            ps.println("    id        BIGINT       NOT NULL AUTO_INCREMENT,");
            ps.println("    name      VARCHAR(20)  NOT NULL,");
            ps.println("    email     VARCHAR(80)  NULL,");
            ps.println("    phone     VARCHAR(20)  NULL,");
            ps.println("    position  VARCHAR(20)  NULL,");
            ps.println("    status    VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',");
            ps.println("    PRIMARY KEY (id)");
            ps.println(") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;");
            ps.println();
            ps.println("CREATE TABLE account_user (");
            ps.println("    id            BIGINT       NOT NULL AUTO_INCREMENT,");
            ps.println("    username      VARCHAR(32)  NOT NULL,");
            ps.println("    password_hash VARCHAR(255) NOT NULL,");
            ps.println("    security_question VARCHAR(100) NULL,");
            ps.println("    security_answer   VARCHAR(100) NULL,");
            ps.println("    display_name  VARCHAR(32)  NOT NULL,");
            ps.println("    account_role  VARCHAR(32)  NOT NULL,");
            ps.println("    member_id     BIGINT       NULL,");
            ps.println("    PRIMARY KEY (id),");
            ps.println("    CONSTRAINT uk_account_user_username UNIQUE (username),");
            ps.println("    CONSTRAINT uk_account_user_member UNIQUE (member_id),");
            ps.println("    CONSTRAINT fk_account_user_member FOREIGN KEY (member_id) REFERENCES team_member (id)");
            ps.println(") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;");
            ps.println();
            ps.println("CREATE TABLE project_task (");
            ps.println("    id          BIGINT       NOT NULL AUTO_INCREMENT,");
            ps.println("    title       VARCHAR(50)  NOT NULL,");
            ps.println("    description VARCHAR(500) NULL,");
            ps.println("    status      VARCHAR(20)  NOT NULL DEFAULT 'TODO',");
            ps.println("    priority    VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',");
            ps.println("    due_date    DATE         NULL,");
            ps.println("    assignee_id BIGINT       NULL,");
            ps.println("    creator_id  BIGINT       NULL,");
            ps.println("    created_at  DATETIME     NULL,");
            ps.println("    assigned_at DATETIME     NULL,");
            ps.println("    remind_at   DATETIME     NULL,");
            ps.println("    PRIMARY KEY (id),");
            ps.println("    CONSTRAINT fk_project_task_assignee FOREIGN KEY (assignee_id) REFERENCES team_member (id),");
            ps.println("    CONSTRAINT fk_project_task_creator FOREIGN KEY (creator_id) REFERENCES account_user (id)");
            ps.println(") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;");
            ps.println();
            ps.println("-- ---------- 成员（" + MEMBER_COUNT + "） ----------");
            ps.println("INSERT INTO team_member (id, name, email, phone, position, status) VALUES");
            ps.println(String.join(",\n", members) + ";");
            ps.println();
            ps.println("-- ---------- 登录账号（" + (1 + PM_COUNT + MEMBER_ACCOUNT_COUNT) + "） ----------");
            ps.println("INSERT INTO account_user (id, username, password_hash, display_name, account_role, member_id) VALUES");
            ps.println(String.join(",\n", accounts) + ";");
            ps.println();
            ps.println("-- ---------- 项目任务（" + TASK_COUNT + "） ----------");
            ps.println("INSERT INTO project_task (id, title, description, status, priority, due_date, assignee_id, creator_id, created_at, assigned_at) VALUES");
            ps.println(String.join(",\n", tasks) + ";");
            ps.println();
            ps.println("COMMIT;");
            ps.println("CREATE TABLE IF NOT EXISTS task_comment (");
            ps.println("    id         BIGINT       NOT NULL AUTO_INCREMENT,");
            ps.println("    task_id    BIGINT       NOT NULL,");
            ps.println("    author_id  BIGINT       NOT NULL,");
            ps.println("    parent_id  BIGINT       NULL,");
            ps.println("    content    VARCHAR(500) NOT NULL,");
            ps.println("    created_at DATETIME     NOT NULL,");
            ps.println("    PRIMARY KEY (id),");
            ps.println("    KEY idx_task_comment_task (task_id, created_at),");
            ps.println("    KEY idx_task_comment_author (author_id),");
            ps.println("    CONSTRAINT fk_task_comment_task FOREIGN KEY (task_id) REFERENCES project_task (id) ON DELETE CASCADE,");
            ps.println("    CONSTRAINT fk_task_comment_author FOREIGN KEY (author_id) REFERENCES account_user (id)");
            ps.println(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;");
            ps.println();
            ps.println("SET FOREIGN_KEY_CHECKS = 1;");
            ps.println("SET autocommit = 1;");
        }
        System.out.println("已生成 " + out + "：成员 " + MEMBER_COUNT + "、账号 "
                + (1 + PM_COUNT + MEMBER_ACCOUNT_COUNT) + "、任务 " + TASK_COUNT);
    }

    private static String randomName() {
        return pick(SURNAMES) + pick(GIVEN);
    }

    // 成员姓名需要与账号 display_name 对应：按 id 用固定序列取，避免额外存储
    private static final List<String> MEMBER_NAMES = new ArrayList<>();
    private static String nameOf(int memberId) {
        return MEMBER_NAMES.get(memberId - 1);
    }

    private static String randomPhone() {
        String head = pick(new String[]{"13", "15", "18", "19"});
        StringBuilder sb = new StringBuilder(head);
        for (int i = 0; i < 9; i++) {
            sb.append(R.nextInt(10));
        }
        return sb.toString();
    }

    private static String pick(String[] arr) {
        return arr[R.nextInt(arr.length)];
    }

    private static Integer pick(List<Integer> arr) {
        return arr.get(R.nextInt(arr.size()));
    }

    private static String pad(int i) {
        return String.format("%03d", i);
    }

    private static String sqlStr(String s) {
        return s == null ? "NULL" : "'" + s + "'";
    }
}
