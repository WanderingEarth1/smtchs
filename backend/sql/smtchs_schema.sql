-- =============================================================================
-- smtchs_schema.sql
-- 用途：SM TCHS 上门服务工时计费系统 —— MySQL 8 建表脚本
-- 内容：7 张业务表的 CREATE TABLE 语句（员工档案、工作类型、单价矩阵、
--       服务记录、记录照片、修正痕迹、计费配置）
-- 说明：
--   1. 全部使用 ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
--   2. 所有表与字段均带中文 COMMENT
--   3. 逻辑关联通过索引 + 应用层约束实现，不建物理外键
--   4. 通用审计字段约定：
--        create_by / create_time / update_by / update_time / del_flag
--        del_flag：'0' 存在，'2' 删除（若依逻辑删除约定）
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 表1: 员工档案（登录、状态、锁定与审计信息）
CREATE TABLE `smt_employee` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`         BIGINT       DEFAULT NULL COMMENT '关联 sys_user.user_id',
  `name`            VARCHAR(20)  NOT NULL COMMENT '姓名',
  `account`         VARCHAR(20)  NOT NULL COMMENT '登录账号',
  `status`          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0启用 1停用）',
  `failed_attempts` INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
  `locked_until`    DATETIME     DEFAULT NULL COMMENT '锁定截止时间',
  `create_by`       VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  `create_time`     DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_by`       VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  `update_time`     DATETIME     DEFAULT NULL COMMENT '更新时间',
  `del_flag`        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account` (`account`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工档案';

-- 表2: 工作类型（按次 / 按小时计费）
CREATE TABLE `smt_work_type` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`         VARCHAR(20) NOT NULL COMMENT '类型名称',
  `billing_mode` VARCHAR(16) NOT NULL COMMENT '计费模式（BY_COUNT按次 BY_HOUR按小时）',
  `enabled`      TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否启用（1启用 0停用）',
  `sort`         INT         NOT NULL DEFAULT 0 COMMENT '排序',
  `create_by`    VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time`  DATETIME    DEFAULT NULL COMMENT '创建时间',
  `update_by`    VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time`  DATETIME    DEFAULT NULL COMMENT '更新时间',
  `del_flag`     CHAR(1)     NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作类型';

-- 表3: 单价矩阵（员工 × 工作类型 的单价与生效时间）
CREATE TABLE `smt_price_rule` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id`  BIGINT        NOT NULL COMMENT '员工ID',
  `work_type_id` BIGINT        NOT NULL COMMENT '工作类型ID',
  `price`        DECIMAL(10,2) NOT NULL COMMENT '单价（元/次 或 元/小时）',
  `effective_at` DATETIME      NOT NULL COMMENT '生效时间',
  `create_by`    VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  `create_time`  DATETIME      DEFAULT NULL COMMENT '创建时间',
  `update_by`    VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  `update_time`  DATETIME      DEFAULT NULL COMMENT '更新时间',
  `del_flag`     CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_type` (`employee_id`, `work_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单价矩阵';

-- 表4: 服务记录（核心业务表，含计费参数快照）
CREATE TABLE `smt_work_record` (
  `id`                       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `serial_no`                VARCHAR(32)   NOT NULL COMMENT '流水号',
  `employee_id`              BIGINT        NOT NULL COMMENT '员工ID',
  `work_type_id`             BIGINT        NOT NULL COMMENT '工作类型ID',
  `service_date`             DATE          NOT NULL COMMENT '服务日期',
  `start_time`               VARCHAR(5)    NOT NULL COMMENT '开始时间 HH:mm',
  `end_time`                 VARCHAR(5)    DEFAULT NULL COMMENT '结束时间 HH:mm，按次为空',
  `location`                 VARCHAR(60)   NOT NULL COMMENT '服务地点',
  `latitude`                 DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
  `longitude`                DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
  `geo_captured`             TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否获取定位',
  `content`                  VARCHAR(200)  NOT NULL COMMENT '工作内容',
  `remark`                   VARCHAR(200)  DEFAULT '' COMMENT '备注',
  `status`                   VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING待确认 APPROVED已确认 REJECTED已驳回）',
  `is_backfill`              TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否补录',
  `reject_reason`            VARCHAR(200)  DEFAULT NULL COMMENT '驳回原因',
  `submitted_at`             DATETIME      NOT NULL COMMENT '提交时间',
  `confirmed_at`             DATETIME      DEFAULT NULL COMMENT '确认时间',
  `rejected_at`              DATETIME      DEFAULT NULL COMMENT '驳回时间',
  `snap_billing_mode`        VARCHAR(16)   NOT NULL COMMENT '计费模式快照',
  `snap_unit_price`          DECIMAL(10,2) NOT NULL COMMENT '单价快照',
  `snap_overtime_start`      VARCHAR(5)    NOT NULL COMMENT '加班起算时间快照 HH:mm',
  `snap_overtime_multiplier` DECIMAL(4,2)  NOT NULL COMMENT '加班倍率快照',
  `snap_rounding_rule`       VARCHAR(16)   NOT NULL COMMENT '取整规则快照（EXACT/HALF_HOUR_UP/HOUR_UP）',
  `create_by`                VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  `create_time`              DATETIME      DEFAULT NULL COMMENT '创建时间',
  `update_by`                VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  `update_time`              DATETIME      DEFAULT NULL COMMENT '更新时间',
  `del_flag`                 CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_serial_no` (`serial_no`),
  KEY `idx_emp_date` (`employee_id`, `service_date`),
  KEY `idx_status` (`status`),
  KEY `idx_date` (`service_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务记录';

-- 表5: 记录照片（一条服务记录可有多张照片）
CREATE TABLE `smt_record_photo` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `record_id`   BIGINT       NOT NULL COMMENT '记录ID',
  `url`         VARCHAR(500) NOT NULL COMMENT '照片URL',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `size`        INT          NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_record_id` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记录照片';

-- 表6: 修正痕迹（服务记录被修改时的前后快照与说明）
CREATE TABLE `smt_record_revision` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `record_id`     BIGINT       NOT NULL COMMENT '记录ID',
  `operator_name` VARCHAR(20)  NOT NULL COMMENT '修正人姓名',
  `revised_at`    DATETIME     NOT NULL COMMENT '修正时间',
  `before_json`   TEXT         COMMENT '修正前快照JSON',
  `after_json`    TEXT         COMMENT '修正后快照JSON',
  `note`          VARCHAR(200) NOT NULL COMMENT '修正说明',
  PRIMARY KEY (`id`),
  KEY `idx_record_id` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='修正痕迹';

-- 表7: 计费配置（全局唯一一行，加班/取整参数）
CREATE TABLE `smt_sys_config` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `overtime_start`      VARCHAR(5)   NOT NULL DEFAULT '18:00' COMMENT '加班起算时间 HH:mm',
  `overtime_multiplier` DECIMAL(4,2) NOT NULL DEFAULT 2.00 COMMENT '加班倍率',
  `rounding_rule`       VARCHAR(16)  NOT NULL DEFAULT 'EXACT' COMMENT '取整规则',
  `update_by`           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  `update_time`         DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计费配置';

SET FOREIGN_KEY_CHECKS = 1;
