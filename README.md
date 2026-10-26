# 爱管理 Sprint 1 + Sprint 2 + Sprint 3

Spring Boot 3（Java 21）同时提供接口和页面。数据库使用本地 **MySQL 8.x**。

## 前置条件

- JDK 21+
- Maven 3.9+
- 本地 MySQL 8.x（默认 `root / 123456`，需与本仓库 `application.yml` 一致，或修改 yml 适配本地）
- Git（仅「推送/拉取代码」需要）

## 获取代码（复现第 1 步）

```bash
# 首次克隆
git clone git@github.com:Stayeverywhere/love_management.git
cd love_management

# 已有仓库时更新到最新
git pull origin main
```

## 数据库构建（复现第 2 步）

唯一建库脚本：`db/mysql/love_management.sql`（建库 + 建表 + 基线数据，表结构与 `com.lovemanagement.domain.*` 实体保持一致，`ddl-auto: none`，不使用 Flyway）。

脚本**可重复执行**：会按外键依赖逆序清空并重建 `love_management` 的表，重复执行不会出现 `Duplicate entry` 错误。

```bash
mysql -u root -p < db/mysql/love_management.sql
```

表清单：`account_user`、`team_member`、`app_role`、`function_permission`、`app_role_permission`、`project_task`、`task_comment`、`member_function_permission`（个人权限）、`operation_log`（操作日志）。

> 旧库升级提示：若 `love_management` 库是用旧版脚本建的，直接重跑上面的建库脚本即可整体重建；也可只补列：
> ```sql
> ALTER TABLE project_task ADD COLUMN completed_at DATETIME NULL;
> ```

## 导入模拟数据（可选，复现第 3 步）

`db/mysql/mock_data.sql`（由 `tools/GenerateMockData.java` 生成，随机种子固定，重复生成结果一致）：

| 数据量 | 内容 |
| --- | --- |
| 成员 | 300 名（约 90% 在职 ACTIVE） |
| 登录账号 | 20 个：`admin`、`pm1`~`pm5`、`user001`~`user014`（后 14 个绑定在职成员） |
| 任务 | 300 条，随机状态/优先级/截止日期/提醒时间；**未分配负责人的任务全部为待处理（TODO）** |

脚本自带建表 DDL（与建库脚本结构一致，含 `remind_at`、`completed_at`、密保等最新列），可重复执行，不影响角色、权限等基线表。

```bash
mysql -u root -p --default-character-set=utf8mb4 love_management < db/mysql/mock_data.sql
```

导入后启动应用，用 `pm1 / mock123456` 登录即可看到看板（含燃尽图）与任务数据，用 `user001 / mock123456` 登录可以看到分配给自己的任务。

## 配置数据库连接（复现第 4 步）

`src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/love_management?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
    username: root
    password: 改成你本地 MySQL root 的密码
```

测试配置在 `src/test/resources/application-test.yml`（使用 `love_management_test` 库，Hibernate `create-drop` + `@Sql` 基线 `db/test-baseline.sql`）。

## 登录验证（复现第 6 步）

采用**账号 + 密码**登录（BCrypt 校验）。账号必须存在于 `account_user` 表，密码错误提示「账号或密码不正确」。

| 身份 | 账号 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `Admin1234` |
| 项目经理 | `pm`（模拟数据：`pm1`~`pm5`） | `Pm123456` |
| 团队成员 | `member`（模拟数据：`user001`~`user014`） | `Member123` |

模拟数据中 `pm1`~`pm5` 和 `user001`~`user014` 的密码统一为 `mock123456`。首次登录后可通过「修改密码」更改。

建议验证路径（管理员 `admin` 登录）：
1. 左侧导航「数据看板」→ 查看指标卡与**燃尽图**；
2. 「成员管理」→ 添加/批量导入成员、禁用成员、设置个人权限、查看操作日志；
3. 「角色配置」→ 复制角色、禁用角色（禁用后该角色账号无法登录）；
4. 「操作日志」→ 查看上述全部变更的操作记录。

## 编译并运行（复现第 5 步）

```bash
# 编译（可选，直接 run 也会编译）
mvn compile

# 启动
mvn spring-boot:run
```

浏览器打开 http://localhost:8081 。

## Sprint 1 功能

- **登录/退出登录**：账号 + 密码登录 `POST /login`；退出 `/logout`。
- **修改密码**：`/password`（BCrypt 加密存储）。
- **创建角色**：`/admin/roles/new` 填写角色名称（4~20 字符，不可重复）。
- **配置权限**：`/admin/roles/{id}/permissions` 勾选功能权限并保存。
- **管理成员**：`/admin/members` 添加/移除/修改成员信息，支持**姓名搜索 + 分页**。
- **创建任务**：`/pm/tasks/new` 填写任务标题（2~50 字符），可选择负责人。
- **任务分配**：`/pm/tasks/{id}/assign` 选择任务负责人并确认分配（只能选在职成员）。
- **数据看板**：`/pm/dashboard` 成员总数、任务总数、未分配、各状态数量、待提醒任务，以及**燃尽图**（近 8 周，周一为一周起点：灰色虚线为应完成基准线，实线为实际剩余未完成任务数；任务置为已完成时记录完成时间）。
- **项目趋势图**：`/pm/trends` 展示新建任务趋势（Chart.js）。

## Sprint 2 功能

- **填写角色描述 / 选择数据权限**：角色表单支持描述与数据范围（仅自己/本团队/全部数据）。
- **查看权限配置结果**：权限页展示功能权限勾选结果与数据权限。
- **填写任务描述 / 设置任务优先级 / 设置截止日期**：创建/编辑表单支持描述、低/中/高优先级（彩色徽章）、截止日期（过期未完成红色高亮）。
- **编辑任务**：PM/ADMIN 可编辑任意任务，成员仅可编辑自己创建的任务（`/pm/tasks/{id}/edit`）。
- **状态流转（选择新状态/保存状态）**：详情页下拉选择 待处理/进行中/已完成 并保存，非法值被拒绝。
- **查看任务状态 / 按状态筛选任务**：列表与详情页展示状态徽章；`/tasks`（成员）与 `/pm/tasks`（PM）顶部筛选栏。
- **筛选未分配任务**：`/pm/tasks?assignee=none` 只看尚未指派负责人的任务。
- **删除任务**：PM/ADMIN 可删任意任务，创建人可删自己创建的；评论级联删除。
- **评论与回复**：任务详情页讨论区，可发表评论、对任意评论回复（一层嵌套）。
- **修改个人资料**：`/profile` 修改登录账号与展示名（改账号后本次会话立即生效）。

## Sprint 3 功能

- **设置密保**：`/profile` 下方密保设置表单，保存密保问题与答案（各 2-100 字，须同时填写或同时清空）。
- **设置任务提醒**：创建/编辑任务可设置提醒时间（`remind_at`，`yyyy-MM-dd HH:mm`，可只填日期）；详情页展示；看板「待提醒任务」指标（未完成且已设提醒的数量）。
- **查看评论记录**：`/tasks/{id}/discussions` 按时间倒序展示该任务全部讨论记录（详情页「查看讨论记录」入口）。
- **按周查看趋势 / 按月查看趋势**：`/pm/trends` 顶部粒度切换：按日（近 7 日，默认）/ 按周（近 8 周，周一为一周起点）/ 按月（近 6 个月）。
- **按人查看任务分布**：`/pm/reports`（导航「成员任务分布」，页面标题「成员任务分布」）统计每位在职成员名下任务的待处理/进行中/已完成数量与占比，未分配任务单独列出；成员多时按 10 条/页分页。
- **进入个人统计页面 / 查看个人工作量**：`/stats/personal`（导航「我的统计」，所有登录用户可见）展示名下任务总数及状态分布、我创建的任务、我发表的评论数。

## 功能增强

- **角色复制**：角色卡片「复制」按钮，生成「名称(副本)」并复制全部权限。
- **角色启用/禁用**：禁用后该角色账号无法登录；系统管理员（ADMIN）角色不可禁用。
- **成员启用/禁用**：成员列表「禁用/启用」按钮；移除成员有确认弹窗。
- **成员批量导入**：`/admin/members/import` 按行填写姓名（邮箱可选、按顺序对应），重名自动跳过。
- **个人权限**：`/admin/members/{id}/permissions` 为成员单独设置功能权限；已配置则仅用个人集，未配置则跟随角色权限。
- **操作日志**：导航「操作日志」→ `/admin/logs` 全局记录；成员「日志」按钮 → 该成员专属记录。覆盖角色复制/启用禁用/权限变更、成员增删改/禁用/批量导入/个人权限设置。
- **导航高亮**：登录后 `/home`（管理员/项目经理直接渲染数据看板）与 `/pm/dashboard` 均高亮「数据看板」按钮。

- **分页 + 跳转页码**：`/pm/tasks`、`/admin/members` 与 `/pm/reports` 默认每页 10 条（上限 50），支持「跳至第 N 页」输入框；翻页/跳转保留当前筛选条件（状态、未分配、姓名关键词）。
- **成员姓名搜索**：`/admin/members?name=关键词` 模糊匹配。

## 测试

```bash
mvn test
```

共 49 个测试，全部通过：

| 测试类 | 数量 | 说明 |
| --- | --- | --- |
| `PolicyTest` | 3 | 策略单测（密码/命名/标题） |
| `SprintOneFlowTest` | 8 | 登录、角色、成员、任务分配流程 |
| `SprintOneIncrementTest` | 8 | 角色复制/禁用、成员禁用/批量导入/个人权限/操作日志 |
| `SprintTwoFlowTest` | 17 | 任务编辑/状态/删除/评论/筛选/分页/搜索 |
| `SprintThreeFlowTest` | 7 | 密保/提醒/讨论记录/周月趋势/报表/个人统计 |
| `AllPagesSmokeTest` | 2 | 真实登录（非 mock）逐页巡检，守住 500 回归 |
| `DashboardBurndownTest` | 4 | 完成时间记录、燃尽序列形状、看板渲染、导航高亮 |

## 规则

- 登录：`POST /login`，字段 `username`、`password`（BCrypt 校验）。失败提示「账号或密码不正确」。
- 任务标题：必填，2 到 50 个字符。
- 任务负责人：只能选择在职成员。
- 数据权限：管理员角色可见全部数据；PM 可见项目数据；成员可见自己相关任务。

## 推送到 GitHub

远程仓库已配置为 `origin`（`git@github.com:Stayeverywhere/love_management.git`，SSH）。

```bash
# 1. 查看改动
git status

# 2. 暂存全部改动（含新增文件）
git add -A

# 3. 提交（中文描述本次变更）
git commit -m "feat: 燃尽图 + 操作日志 + 个人权限 + 角色复制/禁用 + 成员批量导入"

# 4. 推送（首次推送该分支用 -u 建立跟踪关系）
git push origin main
```

推送前建议先 `mvn test` 确认全绿。

## 故障排查

- **`No plugin found for prefix 'xxx'`**：命令拼写错误，例如 `sprng-boot`，正确为 `mvn spring-boot:run`。
- **Maven 下载时 `Checksum validation failed`（WARNING）**：阿里云镜像校验和缺失的警告，可忽略，不影响构建。
- **500/Whitelabel + `ClassFormatError` / `NoClassDefFoundError`**：`target/classes` 里 class 文件损坏（Windows 增量编译残留）。执行 `mvn clean` 后重新 `mvn spring-boot:run`。
- **500 + `Table doesn't exist` / `Unknown column`**：真实库结构与建库脚本不一致（如导入过旧版本脚本）。重新执行 `mysql -u root -p < db/mysql/love_management.sql`，再按需重新导入模拟数据。
- **建库脚本重跑报 `Duplicate entry`**：说明在用旧版脚本；当前版本支持重复执行（先清空重建）。
- **`Port 8081 already in use`**：`netstat -ano | grep LISTEN | grep 8081` 找到 PID 后 `taskkill /PID <PID> /F`。
- **页面看不到新功能**：确认运行的是最新代码——旧进程不会热加载，需先结束旧进程再启动。
