-- =============================================================================
-- smtchs_rollback.sql
-- 用途：SM TCHS 上门服务工时计费系统 —— 回滚脚本
-- 说明：删除全部业务表与相关菜单权限，用于开发环境重置或卸载
-- 警告：执行后数据不可恢复，生产环境请先备份
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 按依赖倒序删除（先删子表，再删主表）
DROP TABLE IF EXISTS `smt_record_revision`;
DROP TABLE IF EXISTS `smt_record_photo`;
DROP TABLE IF EXISTS `smt_work_record`;
DROP TABLE IF EXISTS `smt_price_rule`;
DROP TABLE IF EXISTS `smt_work_type`;
DROP TABLE IF EXISTS `smt_employee`;
DROP TABLE IF EXISTS `smt_sys_config`;

-- 清理若依菜单与按钮权限（若已导入）
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (
  SELECT `menu_id` FROM `sys_menu` WHERE `perms` LIKE 'smtchs:%' OR `path` = 'smtchs'
);
DELETE FROM `sys_menu` WHERE `perms` LIKE 'smtchs:%' OR `path` = 'smtchs';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 回滚完成
-- =============================================================================
