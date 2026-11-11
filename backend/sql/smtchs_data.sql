-- =============================================================================
-- smtchs_data.sql
-- 用途：SM TCHS 上门服务工时计费系统 —— MySQL 8 初始化数据脚本
-- 依赖：先执行 smtchs_schema.sql 建表
-- 说明：
--   1. 本脚本只做 INSERT，不建表、不改表结构
--   2. 关联 ID 一律用子查询按业务键取值，不硬编码自增主键
--      （子查询均指向其它表，MySQL 允许在 INSERT ... VALUES 中使用）
--   3. 服务记录中的 snap_* 五个快照字段与「员工 × 工作类型」单价矩阵保持一致
--   4. 建议在空库上执行；重复执行会因唯一键（account / name / serial_no 等）冲突
-- =============================================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- =============================================================================
-- 1. 员工档案 smt_employee（5 条）
--    张三 / 李四 / 王五 / 管理员 为启用状态（status='0'），赵六 为停用状态（status='1'）
--    user_id 统一留空，若依 sys_user 关联由后续步骤处理
-- =============================================================================
INSERT INTO `smt_employee`
  (`user_id`, `name`, `account`, `status`, `failed_attempts`, `locked_until`,
   `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
  (NULL, '张三', 'zhangsan', '0', 0, NULL, 'admin', NOW(), '', NULL, '0'),
  (NULL, '李四', 'lisi',     '0', 0, NULL, 'admin', NOW(), '', NULL, '0'),
  (NULL, '王五', 'wangwu',   '0', 0, NULL, 'admin', NOW(), '', NULL, '0'),
  (NULL, '赵六', 'zhaoliu',  '1', 0, NULL, 'admin', NOW(), '', NULL, '0'),
  -- 管理员账号：管理员也可能上门服务，需有员工档案才能创建工单
  (NULL, '管理员', 'admin',  '0', 0, NULL, 'admin', NOW(), '', NULL, '0');

-- =============================================================================
-- 2. 工作类型 smt_work_type（5 条）
--    送货上门 / 巡检保养 为按次计费（BY_COUNT），其余为按小时计费（BY_HOUR）
--    客户培训 为停用状态（enabled=0），用于验证停用类型不可再被选择
-- =============================================================================
INSERT INTO `smt_work_type`
  (`name`, `billing_mode`, `enabled`, `sort`,
   `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
  ('送货上门', 'BY_COUNT', 1, 1, 'admin', NOW(), '', NULL, '0'),
  ('安装调试', 'BY_HOUR',  1, 2, 'admin', NOW(), '', NULL, '0'),
  ('维修维护', 'BY_HOUR',  1, 3, 'admin', NOW(), '', NULL, '0'),
  ('巡检保养', 'BY_COUNT', 1, 4, 'admin', NOW(), '', NULL, '0'),
  ('客户培训', 'BY_HOUR',  0, 5, 'admin', NOW(), '', NULL, '0');

-- =============================================================================
-- 3. 单价矩阵 smt_price_rule（25 条 = 5 员工 × 5 工作类型）
--    含管理员账号：管理员也可能上门服务，需配单价才能创建工单
--    单价单位：按次为 元/次，按小时为 元/小时
--    生效时间统一为 2026-10-01 00:00:00
-- =============================================================================
INSERT INTO `smt_price_rule`
  (`employee_id`, `work_type_id`, `price`, `effective_at`,
   `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
  -- 张三：送货上门 50 / 安装调试 90 / 维修维护 80 / 巡检保养 40 / 客户培训 100
  ((SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '送货上门'), 50.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '安装调试'), 90.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '维修维护'), 80.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '巡检保养'), 40.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '客户培训'), 100.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),

  -- 李四：送货上门 50 / 安装调试 85 / 维修维护 75 / 巡检保养 40 / 客户培训 95
  ((SELECT id FROM `smt_employee` WHERE account = 'lisi'),
   (SELECT id FROM `smt_work_type` WHERE name = '送货上门'), 50.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'lisi'),
   (SELECT id FROM `smt_work_type` WHERE name = '安装调试'), 85.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'lisi'),
   (SELECT id FROM `smt_work_type` WHERE name = '维修维护'), 75.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'lisi'),
   (SELECT id FROM `smt_work_type` WHERE name = '巡检保养'), 40.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'lisi'),
   (SELECT id FROM `smt_work_type` WHERE name = '客户培训'), 95.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),

  -- 王五：送货上门 45 / 安装调试 80 / 维修维护 70 / 巡检保养 35 / 客户培训 90
  ((SELECT id FROM `smt_employee` WHERE account = 'wangwu'),
   (SELECT id FROM `smt_work_type` WHERE name = '送货上门'), 45.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'wangwu'),
   (SELECT id FROM `smt_work_type` WHERE name = '安装调试'), 80.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'wangwu'),
   (SELECT id FROM `smt_work_type` WHERE name = '维修维护'), 70.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'wangwu'),
   (SELECT id FROM `smt_work_type` WHERE name = '巡检保养'), 35.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'wangwu'),
   (SELECT id FROM `smt_work_type` WHERE name = '客户培训'), 90.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),

  -- 赵六（已停用，历史单价保留）：送货上门 45 / 安装调试 80 / 维修维护 70 / 巡检保养 35 / 客户培训 90
  ((SELECT id FROM `smt_employee` WHERE account = 'zhaoliu'),
   (SELECT id FROM `smt_work_type` WHERE name = '送货上门'), 45.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'zhaoliu'),
   (SELECT id FROM `smt_work_type` WHERE name = '安装调试'), 80.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'zhaoliu'),
   (SELECT id FROM `smt_work_type` WHERE name = '维修维护'), 70.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'zhaoliu'),
   (SELECT id FROM `smt_work_type` WHERE name = '巡检保养'), 35.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'zhaoliu'),
   (SELECT id FROM `smt_work_type` WHERE name = '客户培训'), 90.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),

  -- 管理员：与张三同价（管理员也可能上门服务）
  ((SELECT id FROM `smt_employee` WHERE account = 'admin'),
   (SELECT id FROM `smt_work_type` WHERE name = '送货上门'), 50.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'admin'),
   (SELECT id FROM `smt_work_type` WHERE name = '安装调试'), 90.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'admin'),
   (SELECT id FROM `smt_work_type` WHERE name = '维修维护'), 80.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'admin'),
   (SELECT id FROM `smt_work_type` WHERE name = '巡检保养'), 40.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0'),
  ((SELECT id FROM `smt_employee` WHERE account = 'admin'),
   (SELECT id FROM `smt_work_type` WHERE name = '客户培训'), 100.00, '2026-10-01 00:00:00', 'admin', NOW(), '', NULL, '0');

-- =============================================================================
-- 4. 计费配置 smt_sys_config（1 条，全局唯一）
--    加班 18:00 起算，倍率 2.00，取整规则 EXACT（不做取整）
-- =============================================================================
INSERT INTO `smt_sys_config`
  (`overtime_start`, `overtime_multiplier`, `rounding_rule`, `update_by`, `update_time`)
VALUES
  ('18:00', 2.00, 'EXACT', 'admin', NOW());

-- =============================================================================
-- 5. 服务记录 smt_work_record（18 条，2026-11 月）
--    场景覆盖：
--      · 状态：APPROVED 10 条 / PENDING 7 条 / REJECTED 1 条
--      · 补录 is_backfill=1：SR2026110008、SR2026110014
--      · 跨 0 点：SR2026110005（22:00 ~ 次日 01:00）
--      · 未获取定位 geo_captured=0：SR2026110010（经纬度为 NULL）
--      · 按次（end_time 为 NULL）9 条 / 按小时（end_time 有值）9 条
--    快照字段取值来源：smt_price_rule 单价 + smt_sys_config 计费参数
-- =============================================================================
INSERT INTO `smt_work_record`
  (`serial_no`, `employee_id`, `work_type_id`, `service_date`, `start_time`, `end_time`,
   `location`, `latitude`, `longitude`, `geo_captured`, `content`, `remark`,
   `status`, `is_backfill`, `reject_reason`,
   `submitted_at`, `confirmed_at`, `rejected_at`,
   `snap_billing_mode`, `snap_unit_price`, `snap_overtime_start`,
   `snap_overtime_multiplier`, `snap_rounding_rule`,
   `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
  -- 1. 张三 / 送货上门 / 11-03 09:00 / 按次 / 已确认
  ('SR2026110001',
   (SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '送货上门'),
   '2026-11-03', '09:00', NULL,
   '滨江花园', 30.208500, 120.211300, 1,
   '配送冰箱、洗衣机各一台至客户家中，协助摆放到位并完成签收', '',
   'APPROVED', 0, NULL,
   '2026-11-03 09:20:00', '2026-11-03 18:30:00', NULL,
   'BY_COUNT', 50.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 2. 张三 / 送货上门 / 11-03 20:00 / 按次 / 已确认（夜间配送）
  ('SR2026110002',
   (SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '送货上门'),
   '2026-11-03', '20:00', NULL,
   '锦绣江南', 30.221400, 120.185600, 1,
   '夜间加急配送餐桌椅一套，客户验收无误', '客户要求夜间配送',
   'APPROVED', 0, NULL,
   '2026-11-03 20:25:00', '2026-11-04 09:10:00', NULL,
   'BY_COUNT', 50.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 3. 张三 / 维修维护 / 11-05 17:30~19:30 / 按小时 / 已确认（跨加班时段）
  ('SR2026110003',
   (SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '维修维护'),
   '2026-11-05', '17:30', '19:30',
   '阳光里小区', 30.245800, 120.162400, 1,
   '处理空调外机异响，更换减震垫并补充冷媒', '',
   'APPROVED', 0, NULL,
   '2026-11-05 19:45:00', '2026-11-06 10:20:00', NULL,
   'BY_HOUR', 80.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 4. 张三 / 安装调试 / 11-06 09:00~12:00 / 按小时 / 已确认
  ('SR2026110004',
   (SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '安装调试'),
   '2026-11-06', '09:00', '12:00',
   '万科城市之光', 30.268900, 120.193700, 1,
   '安装壁挂式新风主机并完成风量调试与滤网检查', '',
   'APPROVED', 0, NULL,
   '2026-11-06 12:15:00', '2026-11-06 19:00:00', NULL,
   'BY_HOUR', 90.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 5. 张三 / 维修维护 / 11-07 22:00~次日01:00 / 按小时 / 已确认（跨 0 点）
  ('SR2026110005',
   (SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '维修维护'),
   '2026-11-07', '22:00', '01:00',
   '翠苑一区', 30.281200, 120.135800, 1,
   '夜间抢修厨房水管爆裂，更换角阀与软管后恢复供水', '跨零点作业，已提前报备',
   'APPROVED', 0, NULL,
   '2026-11-08 01:15:00', '2026-11-08 10:40:00', NULL,
   'BY_HOUR', 80.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 6. 张三 / 巡检保养 / 11-08 10:00 / 按次 / 待确认
  ('SR2026110006',
   (SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '巡检保养'),
   '2026-11-08', '10:00', NULL,
   '和平小区', 30.254700, 120.174200, 1,
   '按季度巡检中央空调系统，清洗滤网并记录运行参数', '',
   'PENDING', 0, NULL,
   '2026-11-08 10:30:00', NULL, NULL,
   'BY_COUNT', 40.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 7. 张三 / 维修维护 / 11-09 17:00~18:40 / 按小时 / 待确认（跨加班起算点）
  ('SR2026110007',
   (SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '维修维护'),
   '2026-11-09', '17:00', '18:40',
   '古荡新村', 30.272300, 120.128600, 1,
   '检修热水器点火故障，更换点火针并测试水温', '',
   'PENDING', 0, NULL,
   '2026-11-09 18:55:00', NULL, NULL,
   'BY_HOUR', 80.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 8. 张三 / 送货上门 / 11-01 14:00 / 按次 / 待确认 / 补录
  ('SR2026110008',
   (SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '送货上门'),
   '2026-11-01', '14:00', NULL,
   '文三路', 30.276500, 120.142300, 1,
   '补录：配送微波炉一台，客户已签收', '补录原因：当日系统离线未及时提交',
   'PENDING', 1, NULL,
   '2026-11-02 09:30:00', NULL, NULL,
   'BY_COUNT', 50.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 9. 张三 / 安装调试 / 11-04 13:00~15:00 / 按小时 / 已驳回
  ('SR2026110009',
   (SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '安装调试'),
   '2026-11-04', '13:00', '15:00',
   '香积寺路', 30.293400, 120.158700, 1,
   '安装智能门锁并录入指纹，现场照片拍摄不清', '',
   'REJECTED', 0, '照片不清晰，无法确认安装完成情况，请重新拍摄后提交',
   '2026-11-04 15:20:00', NULL, '2026-11-05 09:30:00',
   'BY_HOUR', 90.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 10. 张三 / 送货上门 / 11-10 11:00 / 按次 / 待确认 / 未获取定位
  ('SR2026110010',
   (SELECT id FROM `smt_employee` WHERE account = 'zhangsan'),
   (SELECT id FROM `smt_work_type` WHERE name = '送货上门'),
   '2026-11-10', '11:00', NULL,
   '未来科技城', NULL, NULL, 0,
   '配送净水器一台，现场未开启定位权限', '客户拒绝授权定位',
   'PENDING', 0, NULL,
   '2026-11-10 11:25:00', NULL, NULL,
   'BY_COUNT', 50.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 11. 李四 / 送货上门 / 11-02 09:30 / 按次 / 已确认
  ('SR2026110011',
   (SELECT id FROM `smt_employee` WHERE account = 'lisi'),
   (SELECT id FROM `smt_work_type` WHERE name = '送货上门'),
   '2026-11-02', '09:30', NULL,
   '滨江花园', 30.209100, 120.212800, 1,
   '配送沙发组合一套，搬运至客厅并拆包装', '',
   'APPROVED', 0, NULL,
   '2026-11-02 09:50:00', '2026-11-02 17:20:00', NULL,
   'BY_COUNT', 50.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 12. 李四 / 维修维护 / 11-05 14:00~17:30 / 按小时 / 已确认
  ('SR2026110012',
   (SELECT id FROM `smt_employee` WHERE account = 'lisi'),
   (SELECT id FROM `smt_work_type` WHERE name = '维修维护'),
   '2026-11-05', '14:00', '17:30',
   '锦绣江南', 30.222600, 120.186900, 1,
   '维修洗衣机排水异常，清理排水泵异物', '',
   'APPROVED', 0, NULL,
   '2026-11-05 17:45:00', '2026-11-06 09:40:00', NULL,
   'BY_HOUR', 75.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 13. 李四 / 安装调试 / 11-06 17:00~19:40 / 按小时 / 待确认（跨加班起算点）
  ('SR2026110013',
   (SELECT id FROM `smt_employee` WHERE account = 'lisi'),
   (SELECT id FROM `smt_work_type` WHERE name = '安装调试'),
   '2026-11-06', '17:00', '19:40',
   '阳光里小区', 30.246300, 120.163100, 1,
   '安装智能晾衣架并调试升降与照明功能', '',
   'PENDING', 0, NULL,
   '2026-11-06 19:55:00', NULL, NULL,
   'BY_HOUR', 85.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 14. 李四 / 送货上门 / 11-07 15:00 / 按次 / 待确认 / 补录
  ('SR2026110014',
   (SELECT id FROM `smt_employee` WHERE account = 'lisi'),
   (SELECT id FROM `smt_work_type` WHERE name = '送货上门'),
   '2026-11-07', '15:00', NULL,
   '万科城市之光', 30.269400, 120.194500, 1,
   '补录：配送电饭煲一台，客户当场验收', '补录原因：纸质单据后补录入',
   'PENDING', 1, NULL,
   '2026-11-08 10:05:00', NULL, NULL,
   'BY_COUNT', 50.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 15. 王五 / 维修维护 / 11-04 10:00~12:30 / 按小时 / 已确认
  ('SR2026110015',
   (SELECT id FROM `smt_employee` WHERE account = 'wangwu'),
   (SELECT id FROM `smt_work_type` WHERE name = '维修维护'),
   '2026-11-04', '10:00', '12:30',
   '翠苑一区', 30.281800, 120.136400, 1,
   '维修油烟机吸力下降，拆洗风轮与油网', '',
   'APPROVED', 0, NULL,
   '2026-11-04 12:45:00', '2026-11-04 20:10:00', NULL,
   'BY_HOUR', 70.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 16. 王五 / 巡检保养 / 11-08 09:00 / 按次 / 待确认
  ('SR2026110016',
   (SELECT id FROM `smt_employee` WHERE account = 'wangwu'),
   (SELECT id FROM `smt_work_type` WHERE name = '巡检保养'),
   '2026-11-08', '09:00', NULL,
   '和平小区', 30.255200, 120.175100, 1,
   '巡检全屋净水设备，更换前置滤芯', '',
   'PENDING', 0, NULL,
   '2026-11-08 09:25:00', NULL, NULL,
   'BY_COUNT', 35.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 17. 赵六 / 送货上门 / 11-02 10:00 / 按次 / 已确认（停用员工历史记录保留）
  ('SR2026110017',
   (SELECT id FROM `smt_employee` WHERE account = 'zhaoliu'),
   (SELECT id FROM `smt_work_type` WHERE name = '送货上门'),
   '2026-11-02', '10:00', NULL,
   '古荡新村', 30.272900, 120.129300, 1,
   '配送电视机一台并协助挂墙安装', '',
   'APPROVED', 0, NULL,
   '2026-11-02 10:20:00', '2026-11-03 09:15:00', NULL,
   'BY_COUNT', 45.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0'),

  -- 18. 赵六 / 维修维护 / 11-03 14:00~16:00 / 按小时 / 已确认（停用员工历史记录保留）
  ('SR2026110018',
   (SELECT id FROM `smt_employee` WHERE account = 'zhaoliu'),
   (SELECT id FROM `smt_work_type` WHERE name = '维修维护'),
   '2026-11-03', '14:00', '16:00',
   '文三路', 30.277100, 120.143000, 1,
   '维修燃气灶点火异常，更换电池盒与点火器', '',
   'APPROVED', 0, NULL,
   '2026-11-03 16:15:00', '2026-11-04 10:30:00', NULL,
   'BY_HOUR', 70.00, '18:00', 2.00, 'EXACT',
   'admin', NOW(), '', NULL, '0');

COMMIT;
