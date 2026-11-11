-- =============================================================================
-- smtchs_bind_users.sql
-- 用途：把业务员工档案（smt_employee）绑定到若依用户体系（sys_user）
--
-- 背景：
--   smt_employee 只存业务信息（姓名、账号、状态），登录认证走若依的 sys_user。
--   两边通过 smt_employee.user_id = sys_user.user_id 关联。
--   不执行本脚本，员工端登录会失败（提示「账号或密码错误」）。
--
-- 依赖：先执行
--   1. ry_20260417.sql      （若依基础表）
--   2. smtchs_schema.sql    （业务表）
--   3. smtchs_data.sql      （业务数据，含 5 个员工档案）
--   4. smtchs_menu.sql      （业务菜单与权限）
--
-- 幂等性：可重复执行。已存在的 sys_user 只更新密码与状态，不重复插入。
--
-- 默认密码：
--   员工账号（zhangsan / lisi / wangwu / zhaoliu）→ 123456
--   管理员账号（admin）→ 保持若依初始化的 admin123，本脚本不覆盖
--   ⚠ 生产环境请执行后立即修改所有账号密码
-- =============================================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- =============================================================================
-- 1. 为每个员工创建对应的 sys_user
--    密码统一为 123456 的 BCrypt 密文
--    部门默认挂到「研发部门」（dept_id = 103），可按需修改
-- =============================================================================
-- 注意：sys_user.user_name 没有唯一索引，ON DUPLICATE KEY UPDATE 不会生效，
-- 因此用 WHERE NOT EXISTS 保证幂等。
INSERT INTO `sys_user`
  (`dept_id`, `user_name`, `nick_name`, `user_type`, `password`,
   `status`, `del_flag`, `create_by`, `create_time`, `remark`)
SELECT
  103,
  e.account,
  e.name,
  '00',
  '$2a$10$pHEFgatYYKJXfmGDIE5O8OwPtez74EjCtkoon0n9yHgEZoeX2dMXO',
  e.status,
  '0',
  'admin',
  NOW(),
  '业务员工档案自动创建'
FROM `smt_employee` e
WHERE e.del_flag = '0'
  -- admin 由若依初始化脚本创建（密码 admin123），此处不覆盖
  AND e.account <> 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_user` u
    WHERE u.user_name = e.account AND u.del_flag = '0'
  );

-- 已存在的账号：同步昵称、密码、状态（保证重跑后密码一致）
UPDATE `sys_user` u
INNER JOIN `smt_employee` e ON e.account = u.user_name AND e.del_flag = '0'
SET u.nick_name = e.name,
    u.password  = '$2a$10$pHEFgatYYKJXfmGDIE5O8OwPtez74EjCtkoon0n9yHgEZoeX2dMXO',
    u.status    = e.status,
    u.del_flag  = '0'
WHERE u.del_flag = '0'
  AND u.user_name <> 'admin';

-- =============================================================================
-- 2. 回填 smt_employee.user_id
-- =============================================================================
UPDATE `smt_employee` e
INNER JOIN `sys_user` u ON u.user_name = e.account AND u.del_flag = '0'
SET e.user_id = u.user_id
WHERE e.del_flag = '0';

-- =============================================================================
-- 3. 分配角色
--    admin  → 超级管理员（role_id = 1）
--    其他   → 普通角色（role_id = 2）
-- =============================================================================
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT e.user_id, CASE WHEN e.account = 'admin' THEN 1 ELSE 2 END
FROM `smt_employee` e
WHERE e.user_id IS NOT NULL AND e.del_flag = '0'
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- =============================================================================
-- 4. 给普通角色授予业务菜单权限
--    普通角色（员工）只需要：
--      - 工作类型查看（新增记录时选类型）
--      - 记录新增 / 修改 / 删除（自己的记录）
--    管理类权限（审核、报表、配置、员工管理）不授予
-- =============================================================================
DELETE FROM `sys_role_menu`
WHERE `role_id` = 2
  AND `menu_id` IN (
    SELECT `menu_id` FROM (
      SELECT `menu_id` FROM `sys_menu`
      WHERE `perms` LIKE 'smtchs:%' OR `path` = 'smtchs'
    ) AS t
  );

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, `menu_id` FROM `sys_menu`
WHERE `perms` IN (
  'smtchs:worktype:list',
  'smtchs:record:add',
  'smtchs:record:edit',
  'smtchs:record:remove'
)
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- =============================================================================
-- 5. 自检：输出绑定结果
-- =============================================================================
SELECT
  e.account                          AS '登录账号',
  e.name                             AS '姓名',
  e.user_id                          AS 'user_id',
  u.user_name                        AS 'sys_user',
  IF(e.status = '0', '启用', '停用')  AS '员工状态',
  IF(u.status = '0', '启用', '停用')  AS '账号状态',
  (SELECT r.role_name
     FROM `sys_user_role` ur
     INNER JOIN `sys_role` r ON r.role_id = ur.role_id
    WHERE ur.user_id = e.user_id
    LIMIT 1)                         AS '角色'
FROM `smt_employee` e
LEFT JOIN `sys_user` u ON u.user_id = e.user_id
WHERE e.del_flag = '0'
ORDER BY e.id;

COMMIT;

-- =============================================================================
-- 绑定完成
--
-- 验证方式：
--   1. 上面的自检查询应返回 5 行，user_id 与 sys_user 均非空
--   2. 启动后端后测试登录：
--      curl -X POST http://localhost:8080/smtchs/auth/login \
--        -H "Content-Type: application/json" \
--        -d '{"account":"zhangsan","password":"123456"}'
--      应返回 code=200 与 token
--
-- 常见问题：
--   - 登录提示「账号或密码错误」：检查 smt_employee.user_id 是否为空
--   - 登录后接口 403：检查 sys_user_role 与 sys_role_menu 是否已插入
--   - 修改默认密码：UPDATE sys_user SET password='<新BCrypt>' WHERE user_name='xxx'
-- =============================================================================
