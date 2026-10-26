-- 测试基线数据（供 SprintOneFlowTest 通过 @Sql 在每个测试事务内导入）
-- 内容与 db/mysql/love_management.sql 的基线数据部分保持一致
-- 不包含演示成员与演示任务；测试中需要的成员由测试自行创建

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

INSERT INTO app_role (id, name, description, data_scope, code, enabled) VALUES
    (1, '系统管理员', '维护角色、权限和成员', 'ALL', 'ADMIN', 1),
    (2, '项目经理', '创建任务、分配负责人并查看看板', 'TEAM', 'PROJECT_MANAGER', 1),
    (3, '团队成员', '查看分配给自己的任务', 'SELF', 'MEMBER', 1);

INSERT INTO app_role_permission (role_id, permission_id) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9),
    (2, 3), (2, 5), (2, 6), (2, 7), (2, 8), (2, 9),
    (3, 7);

INSERT INTO account_user (id, username, password_hash, display_name, account_role, member_id) VALUES
    (1, 'admin', '$2a$10$Ww9hG4drFxcu0RDYMz37keQW95wzb4QOmtfYVBlp3PQ7NZvsJfWoy', '系统管理员', 'ADMIN', NULL),
    (2, 'pm', '$2a$10$DAsSgftxMPOp1nLVHeReAelq/Bg9V/oSm6mzxtian80wSNSI2Av1O', '项目经理', 'PROJECT_MANAGER', NULL),
    (3, 'member', '$2a$10$5LPx.MnDNJWKXclYhjOB3ejyYa/67GrgHajQ9hnsDA.TMMfkUZXai', '团队成员', 'MEMBER', NULL);
