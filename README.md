# 爱管理

Spring Boot 3（Java 21）+ Thymeleaf + MySQL 8 的任务管理应用，含登录/角色/成员/任务/看板/统计。

## 启动

前置：JDK 21+、Maven 3.9+、本地 MySQL 8.x（账号密码需与 `src/main/resources/application.yml` 一致，默认 `root / 123456`）。

```bash
# 1. 建库建表（可重复执行；含基线数据，admin / Admin1234）
mysql -u root -p < db/mysql/love_management.sql

# 2.（可选）导入模拟数据：300 成员 / 20 账号 / 300 任务，pm1~pm5、user001~user014 密码 mock123456
mysql -u root -p --default-character-set=utf8mb4 love_management < db/mysql/mock_data.sql

# 3. 启动
mvn spring-boot:run
```

打开 http://localhost:8081 登录（管理员 `admin / Admin1234`）。

测试：`mvn test`（63 个测试全通过）。

## 项目结构

```
db/mysql/            建库脚本（love_management.sql）与模拟数据（mock_data.sql）
tools/               模拟数据生成器
src/main/java/com/lovemanagement/
  config/            安全与 Bean 配置
  domain/            实体（账号/成员/角色/权限/任务/评论/操作日志等）
  repo/              Spring Data JPA 仓库
  service/           业务逻辑（认证/角色/成员/任务/看板/报表/统计）
  web/               控制器
  LoveManagementApplication.java
src/main/resources/
  application.yml    配置（端口 8081）
  static/            CSS
  templates/         Thymeleaf 页面（login/admin/pm/task/stats/fragments）
src/test/java/       测试（web 端到端 + service 策略）
```
