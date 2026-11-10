-- =============================================================================
-- smtchs_menu.sql
-- 用途：SM TCHS 上门服务工时计费系统 —— 若依（v3.8.7）菜单与按钮权限
-- 内容：1 个一级菜单 + 7 个二级菜单 + 17 个按钮权限（合计 25 条 sys_menu 记录）
-- 依赖：需先执行 smtchs_schema.sql 建表；本脚本只操作 sys_menu，不涉及业务表
--
-- ⚠️ 仅执行一次！
--    sys_menu 的菜单记录重复插入会产生重复菜单（前端侧边栏出现两份），
--    且按钮权限重复会导致角色授权列表冗余。若需重跑，请先执行：
--      DELETE FROM sys_role_menu WHERE menu_id IN (
--        SELECT menu_id FROM sys_menu WHERE perms LIKE 'smtchs:%' OR path = 'smtchs');
--      DELETE FROM sys_menu WHERE perms LIKE 'smtchs:%' OR path = 'smtchs';
--    （等价于 smtchs_rollback.sql 中的菜单清理段）
--
-- 说明：
--   1. parent_id 通过子查询按「菜单名 + 父ID」定位，不硬编码 menu_id，
--      避免与已有菜单的自增 ID 冲突
--   2. menu_type：M 目录 / C 菜单 / F 按钮
--   3. is_frame：1 否（非外链）；is_cache：0 缓存；visible/status：'0' 正常
--   4. 按钮权限标识与后端 @PreAuthorize 注解逐一对应
--   5. 未显式写入 route_name 列，沿用若依默认值（空串），
--      前端按 path 生成路由名，无需额外配置
--   6. 若 @parentId 取值为 NULL（一级菜单插入失败），后续子菜单会以
--      parent_id=NULL 插入而不会报错，执行后请务必查看文件末尾的自检 SELECT
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 一级菜单：服务记录管理
-- -----------------------------------------------------------------------------
INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('服务记录管理', 0, 5, 'smtchs', NULL, '', 1, 0,
 'M', '0', '0', NULL, 'documentation', 'admin', NOW(),
 '', NULL, 'SM TCHS 上门服务工时计费系统');

-- 取一级菜单 ID（按名称 + parent_id=0 定位，避免依赖 LAST_INSERT_ID）
SET @parentId = (SELECT `menu_id` FROM `sys_menu`
                 WHERE `menu_name` = '服务记录管理' AND `parent_id` = 0 LIMIT 1);

-- -----------------------------------------------------------------------------
-- 2. 二级菜单：员工管理
-- -----------------------------------------------------------------------------
INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('员工管理', @parentId, 1, 'employee', 'smtchs/employee/index', '', 1, 0,
 'C', '0', '0', 'smtchs:employee:list', 'user', 'admin', NOW(),
 '', NULL, '员工档案管理菜单');

SET @employeeMenuId = (SELECT `menu_id` FROM `sys_menu`
                       WHERE `path` = 'employee' AND `parent_id` = @parentId LIMIT 1);

INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('员工查询', @employeeMenuId, 1, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:employee:query',    '#', 'admin', NOW(), '', NULL, ''),
('员工新增', @employeeMenuId, 2, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:employee:add',      '#', 'admin', NOW(), '', NULL, ''),
('员工修改', @employeeMenuId, 3, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:employee:edit',     '#', 'admin', NOW(), '', NULL, ''),
('员工删除', @employeeMenuId, 4, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:employee:remove',   '#', 'admin', NOW(), '', NULL, ''),
('重置密码', @employeeMenuId, 5, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:employee:resetPwd', '#', 'admin', NOW(), '', NULL, '');

-- -----------------------------------------------------------------------------
-- 3. 二级菜单：工作类型
-- -----------------------------------------------------------------------------
INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('工作类型', @parentId, 2, 'worktype', 'smtchs/worktype/index', '', 1, 0,
 'C', '0', '0', 'smtchs:worktype:list', 'tree', 'admin', NOW(),
 '', NULL, '工作类型管理菜单');

SET @worktypeMenuId = (SELECT `menu_id` FROM `sys_menu`
                       WHERE `path` = 'worktype' AND `parent_id` = @parentId LIMIT 1);

INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('类型新增', @worktypeMenuId, 1, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:worktype:add',    '#', 'admin', NOW(), '', NULL, ''),
('类型修改', @worktypeMenuId, 2, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:worktype:edit',   '#', 'admin', NOW(), '', NULL, ''),
('类型删除', @worktypeMenuId, 3, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:worktype:remove', '#', 'admin', NOW(), '', NULL, '');

-- -----------------------------------------------------------------------------
-- 4. 二级菜单：单价矩阵
-- -----------------------------------------------------------------------------
INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('单价矩阵', @parentId, 3, 'price', 'smtchs/price/index', '', 1, 0,
 'C', '0', '0', 'smtchs:price:list', 'money', 'admin', NOW(),
 '', NULL, '员工 × 工作类型单价矩阵菜单');

SET @priceMenuId = (SELECT `menu_id` FROM `sys_menu`
                    WHERE `path` = 'price' AND `parent_id` = @parentId LIMIT 1);

INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('单价编辑', @priceMenuId, 1, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:price:edit', '#', 'admin', NOW(), '', NULL, '');

-- -----------------------------------------------------------------------------
-- 5. 二级菜单：计费规则
-- -----------------------------------------------------------------------------
INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('计费规则', @parentId, 4, 'config', 'smtchs/config/index', '', 1, 0,
 'C', '0', '0', 'smtchs:config:list', 'edit', 'admin', NOW(),
 '', NULL, '加班起算时间 / 倍率 / 取整规则配置菜单');

SET @configMenuId = (SELECT `menu_id` FROM `sys_menu`
                     WHERE `path` = 'config' AND `parent_id` = @parentId LIMIT 1);

INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('规则修改', @configMenuId, 1, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:config:edit', '#', 'admin', NOW(), '', NULL, '');

-- -----------------------------------------------------------------------------
-- 6. 二级菜单：记录查询
-- -----------------------------------------------------------------------------
INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('记录查询', @parentId, 5, 'record', 'smtchs/record/index', '', 1, 0,
 'C', '0', '0', 'smtchs:record:list', 'list', 'admin', NOW(),
 '', NULL, '服务记录查询菜单');

SET @recordMenuId = (SELECT `menu_id` FROM `sys_menu`
                     WHERE `path` = 'record' AND `parent_id` = @parentId LIMIT 1);

INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('记录新增', @recordMenuId, 1, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:record:add',    '#', 'admin', NOW(), '', NULL, ''),
('记录修改', @recordMenuId, 2, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:record:edit',   '#', 'admin', NOW(), '', NULL, ''),
('记录删除', @recordMenuId, 3, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:record:remove', '#', 'admin', NOW(), '', NULL, '');

-- -----------------------------------------------------------------------------
-- 7. 二级菜单：记录审核
-- -----------------------------------------------------------------------------
INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('记录审核', @parentId, 6, 'review', 'smtchs/review/index', '', 1, 0,
 'C', '0', '0', 'smtchs:review:list', 'validCode', 'admin', NOW(),
 '', NULL, '服务记录审核菜单');

SET @reviewMenuId = (SELECT `menu_id` FROM `sys_menu`
                     WHERE `path` = 'review' AND `parent_id` = @parentId LIMIT 1);

INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('审核通过', @reviewMenuId, 1, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:review:approve', '#', 'admin', NOW(), '', NULL, ''),
('审核驳回', @reviewMenuId, 2, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:review:reject',  '#', 'admin', NOW(), '', NULL, ''),
('记录修正', @reviewMenuId, 3, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:review:revise',  '#', 'admin', NOW(), '', NULL, '');

-- -----------------------------------------------------------------------------
-- 8. 二级菜单：月度报表
-- -----------------------------------------------------------------------------
INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('月度报表', @parentId, 7, 'report', 'smtchs/report/index', '', 1, 0,
 'C', '0', '0', 'smtchs:report:list', 'chart', 'admin', NOW(),
 '', NULL, '月度报表与 Excel 双 Sheet 导出菜单');

SET @reportMenuId = (SELECT `menu_id` FROM `sys_menu`
                     WHERE `path` = 'report' AND `parent_id` = @parentId LIMIT 1);

INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`)
VALUES
('报表导出', @reportMenuId, 1, '', NULL, '', 1, 0, 'F', '0', '0', 'smtchs:report:export', '#', 'admin', NOW(), '', NULL, '');

-- =============================================================================
-- 执行结果自检（应返回 25 行：1 目录 + 7 菜单 + 17 按钮）
-- =============================================================================
SELECT `menu_id`, `menu_name`, `parent_id`, `menu_type`, `path`, `component`, `perms`
FROM `sys_menu`
WHERE `perms` LIKE 'smtchs:%' OR `path` = 'smtchs'
ORDER BY `parent_id`, `order_num`;

-- =============================================================================
-- 授权提示（可选）：把新菜单授予角色
--   超级管理员（role_id=1）默认拥有全部权限，无需额外授权。
--   其他角色请到「系统管理 → 角色管理 → 修改 → 菜单权限」勾选，
--   或按需执行：
--     INSERT INTO sys_role_menu (role_id, menu_id)
--     SELECT 2, menu_id FROM sys_menu
--     WHERE perms LIKE 'smtchs:%' OR path = 'smtchs';
-- =============================================================================
