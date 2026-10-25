-- ============================================================
-- 爱管理 数据库构建脚本（MySQL 8.x）
-- 一次性建库、建表并写入基线数据；不依赖应用。
--
-- 执行方式（任选其一）：
--   1) mysql -u <用户名> -p < db/mysql/love_management.sql
--   2) 在 MySQL 客户端中打开本文件整体执行
--
-- 生成两个库：
--   - love_management       开发/运行库：完整表结构 + 基线数据
--   - love_management_test  测试库：空库（测试时由 JPA create-drop 自动建表，
--                           避免污染开发数据）
--
-- 后续结构变更：直接在本脚本追加 ALTER 语句，或另建版本脚本。
-- ============================================================

-- ===================== 开发/运行库 =====================
CREATE DATABASE IF NOT EXISTS love_management
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE love_management;

-- 可重复执行：按外键依赖逆序清表
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS task_comment;
DROP TABLE IF EXISTS project_task;
DROP TABLE IF EXISTS app_role_permission;
DROP TABLE IF EXISTS account_user;
DROP TABLE IF EXISTS function_permission;
DROP TABLE IF EXISTS app_role;
DROP TABLE IF EXISTS team_member;
SET FOREIGN_KEY_CHECKS = 1;

-- 团队成员
CREATE TABLE IF NOT EXISTS team_member (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    name      VARCHAR(20)  NOT NULL,
    email     VARCHAR(80)  NULL,
    phone     VARCHAR(20)  NULL,
    position  VARCHAR(20)  NULL,
    status    VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 功能权限
CREATE TABLE IF NOT EXISTS function_permission (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(40)  NOT NULL,
    label       VARCHAR(40)  NOT NULL,
    description VARCHAR(80)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_function_permission_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 登录账号
CREATE TABLE IF NOT EXISTS account_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(32)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(32)  NOT NULL,
    account_role  VARCHAR(32)  NOT NULL,
    member_id     BIGINT       NULL,
    security_question VARCHAR(100) NULL,
    security_answer   VARCHAR(100) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_account_user_username UNIQUE (username),
    CONSTRAINT uk_account_user_member UNIQUE (member_id),
    CONSTRAINT fk_account_user_member FOREIGN KEY (member_id) REFERENCES team_member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 应用角色
CREATE TABLE IF NOT EXISTS app_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(20)  NOT NULL,
    description VARCHAR(100) NULL,
    data_scope  VARCHAR(10)  NOT NULL DEFAULT 'ALL',
    PRIMARY KEY (id),
    CONSTRAINT uk_app_role_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 角色-权限关联（多对多）
CREATE TABLE IF NOT EXISTS app_role_permission (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES app_role (id),
    CONSTRAINT fk_role_permission_perm FOREIGN KEY (permission_id) REFERENCES function_permission (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 项目任务
CREATE TABLE IF NOT EXISTS project_task (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    title       VARCHAR(50)  NOT NULL,
    description VARCHAR(500) NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'TODO',
    priority    VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    due_date    DATE         NULL,
    assignee_id BIGINT       NULL,
    creator_id  BIGINT       NULL,
    created_at  DATETIME     NULL,
    assigned_at DATETIME     NULL,
    remind_at   DATETIME     NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_project_task_assignee FOREIGN KEY (assignee_id) REFERENCES team_member (id),
    CONSTRAINT fk_project_task_creator FOREIGN KEY (creator_id) REFERENCES account_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 任务评论（Sprint 2）
CREATE TABLE IF NOT EXISTS task_comment (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    task_id    BIGINT        NOT NULL,
    author_id  BIGINT        NOT NULL,
    parent_id  BIGINT        NULL,
    content    VARCHAR(500)  NOT NULL,
    created_at DATETIME      NOT NULL,
    PRIMARY KEY (id),
    KEY idx_task_comment_task (task_id),
    CONSTRAINT fk_task_comment_task FOREIGN KEY (task_id) REFERENCES project_task (id),
    CONSTRAINT fk_task_comment_author FOREIGN KEY (author_id) REFERENCES account_user (id),
    CONSTRAINT fk_task_comment_parent FOREIGN KEY (parent_id) REFERENCES task_comment (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ------------------ 基线数据（无演示成员、无演示任务） ------------------

-- 功能权限目录
INSERT INTO function_permission (id, code, label, description) VALUES
    (1, 'ROLE_MANAGE', '角色配置', '填写并保存角色'),
    (2, 'PERMISSION_ASSIGN', '权限配置', '为角色选择功能权限'),
    (3, 'MEMBER_VIEW', '查看成员', '查看成员列表'),
    (4, 'MEMBER_EDIT', '维护成员', '新增、修改、移除成员'),
    (5, 'TASK_CREATE', '创建任务', '填写并保存任务'),
    (6, 'TASK_ASSIGN', '分配任务', '选择负责人并确认分配'),
    (7, 'TASK_VIEW', '查看任务', '查看任务详情'),
    (8, 'DASHBOARD_VIEW', '数据看板', '查看项目数据看板'),
    (9, 'TREND_VIEW', '项目趋势', '查看项目趋势图');

-- 内置角色
INSERT INTO app_role (id, name, description, data_scope) VALUES
    (1, '系统管理员', '维护角色、权限和成员', 'ALL'),
    (2, '项目经理', '创建任务、分配负责人并查看看板', 'TEAM'),
    (3, '团队成员', '查看分配给自己的任务', 'SELF');

-- 角色-权限关联
INSERT INTO app_role_permission (role_id, permission_id) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9),
    (2, 3), (2, 5), (2, 6), (2, 7), (2, 8), (2, 9),
    (3, 7);

-- 初始登录账号（BCrypt 哈希；密码见 README，首次登录后请通过 /password 修改）
INSERT INTO account_user (id, username, password_hash, display_name, account_role, member_id) VALUES
    (1, 'admin', '$2a$10$Ww9hG4drFxcu0RDYMz37keQW95wzb4QOmtfYVBlp3PQ7NZvsJfWoy', '系统管理员', 'ADMIN', NULL),
    (2, 'pm', '$2a$10$DAsSgftxMPOp1nLVHeReAelq/Bg9V/oSm6mzxtian80wSNSI2Av1O', '项目经理', 'PROJECT_MANAGER', NULL),
    (3, 'member', '$2a$10$5LPx.MnDNJWKXclYhjOB3ejyYa/67GrgHajQ9hnsDA.TMMfkUZXai', '团队成员', 'MEMBER', NULL);

-- ===================== 测试库（空库，供 JPA create-drop 使用） =====================
CREATE DATABASE IF NOT EXISTS love_management_test
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
